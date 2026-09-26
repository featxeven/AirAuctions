package com.ftxeven.airauctions.core.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class BoundedCache<K, V> {

    private final Map<K, V> entries;

    public BoundedCache(int maxSize) {
        this.entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    public V get(K key, Function<K, V> loader) {
        V cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        V value = loader.apply(key); // computed outside the lock on purpose
        entries.put(key, value);
        return value;
    }

    public void clear() {
        entries.clear();
    }
}