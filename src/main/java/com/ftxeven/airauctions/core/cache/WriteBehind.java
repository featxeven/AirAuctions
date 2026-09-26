package com.ftxeven.airauctions.core.cache;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

public final class WriteBehind implements AutoCloseable {

    @FunctionalInterface
    public interface Write {
        void execute() throws Exception;
    }

    public record Field(Object owner, String name) {}

    private static final long FLUSH_INTERVAL_MILLIS = 200L;
    private static final long CLOSE_TIMEOUT_SECONDS = 30L;
    private static final long LOG_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int MAX_ATTEMPTS = 3;

    private static final class Entry {
        private final @Nullable Object key;
        private volatile Write write;
        private volatile int attempts;

        Entry(@Nullable Object key, Write write, int attempts) {
            this.key = key;
            this.write = write;
            this.attempts = attempts;
        }
    }

    private final Logger logger;
    private final ScheduledThreadPoolExecutor io;
    private final Queue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Object, Entry> pending = new ConcurrentHashMap<>();

    private volatile boolean closed;
    private long lastFailureLogNanos = Long.MIN_VALUE; // io thread only

    public WriteBehind(String threadName, Logger logger) {
        this.logger = logger;
        this.io = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        io.setRemoveOnCancelPolicy(true);
        io.scheduleWithFixedDelay(this::drainQuietly, FLUSH_INTERVAL_MILLIS, FLUSH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void submit(Object key, Write write) {
        if (closed) {
            executeNow(write);
            return;
        }
        pending.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.write = write;
                existing.attempts = 0;
                return existing;
            }
            Entry fresh = new Entry(k, write, 0);
            queue.add(fresh);
            return fresh;
        });
    }

    public void append(Write write) {
        if (closed) {
            executeNow(write);
            return;
        }
        queue.add(new Entry(null, write, 0));
    }

    /** blocks until everything currently queued has been attempted. */
    public void flush() {
        try {
            io.submit(this::drain).get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not flush pending writes", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        flush();
        io.shutdownNow();
        drain(); // anything submitted during shutdown, inline
    }

    // IO thread

    private void drainQuietly() {
        try {
            drain();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Write-behind drain crashed", t);
        }
    }

    private void drain() {
        Entry entry;
        while ((entry = queue.poll()) != null) {
            Write write = claim(entry);
            if (write == null) {
                continue;
            }
            try {
                write.execute();
            } catch (Exception e) {
                retryOrDrop(entry, write, e);
            }
        }
    }

    /** takes the latest write for this entry and releases its coalescing slot atomically */
    private @Nullable Write claim(Entry entry) {
        if (entry.key == null) {
            return entry.write;
        }
        Write[] holder = new Write[1];
        pending.computeIfPresent(entry.key, (key, current) -> {
            if (current != entry) {
                return current; // superseded by a freshly queued entry, leave it alone
            }
            holder[0] = current.write;
            return null;
        });
        return holder[0];
    }

    private void retryOrDrop(Entry entry, Write write, Exception failure) {
        int attempts = entry.attempts + 1;
        if (attempts >= MAX_ATTEMPTS || closed) {
            logFailure(failure, true);
            return;
        }
        logFailure(failure, false);
        // re-submitting through the public path means a newer write for the same key wins,
        // instead of this stale one clobbering it
        if (entry.key != null) {
            pending.compute(entry.key, (key, existing) -> {
                if (existing != null) {
                    return existing; // newer state already queued, discard the failed one
                }
                Entry retry = new Entry(key, write, attempts);
                queue.add(retry);
                return retry;
            });
        } else {
            queue.add(new Entry(null, write, attempts));
        }
    }

    private void executeNow(Write write) {
        try {
            write.execute();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not persist a write submitted after shutdown", e);
        }
    }

    private void logFailure(Exception failure, boolean dropped) {
        long now = System.nanoTime();
        if (!dropped && lastFailureLogNanos != Long.MIN_VALUE && now - lastFailureLogNanos < LOG_THROTTLE_NANOS) {
            return;
        }
        lastFailureLogNanos = now;
        logger.log(Level.SEVERE, dropped
                ? "Giving up on a pending write after " + MAX_ATTEMPTS + " attempts, the change is lost"
                : "A pending write failed, retrying on the next flush", failure);
    }
}