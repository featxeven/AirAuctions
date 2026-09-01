package com.ftxeven.airauctions.core.animation;

import java.util.List;
import java.util.SplittableRandom;

public record Animation(List<String> frames, int interval, Mode mode) {

    public Animation {
        frames = List.copyOf(frames);
    }

    public enum Mode {
        LOOP {
            @Override
            long cycleLength(int frameCount) {
                return frameCount;
            }

            @Override
            int frameIndex(String key, long step, int frameCount) {
                return (int) (step % frameCount);
            }

            @Override
            int settledIndex(int frameCount) {
                return frameCount - 1;
            }
        },
        PING_PONG {
            @Override
            long cycleLength(int frameCount) {
                return 2L * (frameCount - 1);
            }

            @Override
            int frameIndex(String key, long step, int frameCount) {
                int period = (int) cycleLength(frameCount);
                int pos = (int) (step % period);
                return pos < frameCount ? pos : period - pos;
            }

            @Override
            int settledIndex(int frameCount) {
                return 0;
            }
        },
        RANDOM {
            @Override
            long cycleLength(int frameCount) {
                return frameCount;
            }

            @Override
            int frameIndex(String key, long step, int frameCount) {
                long seed = key.hashCode() * 31L + step;
                return new SplittableRandom(seed).nextInt(frameCount);
            }

            @Override
            int settledIndex(int frameCount) {
                return frameCount - 1;
            }
        };

        abstract long cycleLength(int frameCount);

        abstract int frameIndex(String key, long step, int frameCount);

        abstract int settledIndex(int frameCount);
    }
}