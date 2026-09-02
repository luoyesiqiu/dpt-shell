package com.luoye.dpt.profile.gate.performance;

public final class ProfileArm64BuildBenchmarkRunner {
    private ProfileArm64BuildBenchmarkRunner() {
    }

    public static long measureOnce(Operation operation, NanoClock clock) throws Exception {
        if (operation == null || clock == null) {
            throw new IllegalArgumentException("arm64 构建操作和时钟不能为空");
        }
        long start = clock.nanoTime();
        operation.run();
        long end = clock.nanoTime();
        if (end <= start) {
            throw new IllegalStateException("arm64 构建计时时钟未前进");
        }
        long millis = (end - start) / 1_000_000L;
        if (millis <= 0L) {
            throw new IllegalStateException("arm64 构建耗时低于 1 ms");
        }
        return millis;
    }

    @FunctionalInterface
    public interface Operation {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }
}
