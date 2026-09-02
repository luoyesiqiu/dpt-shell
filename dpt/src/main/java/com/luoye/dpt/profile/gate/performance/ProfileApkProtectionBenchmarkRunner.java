package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileApkProtectionBenchmarkRunner {
    public static final int RUN_COUNT = 10;

    private ProfileApkProtectionBenchmarkRunner() {
    }

    public static List<Long> measureTen(Iteration operation, NanoClock clock) throws Exception {
        if (operation == null || clock == null) {
            throw new IllegalArgumentException("APK 加固计时操作和时钟不能为空");
        }
        List<Long> samples = new ArrayList<>(RUN_COUNT);
        for (int index = 0; index < RUN_COUNT; index++) {
            long start = clock.nanoTime();
            operation.run(index);
            long end = clock.nanoTime();
            if (end <= start) {
                throw new IllegalStateException("APK 加固计时时钟未前进：iteration=" + index);
            }
            long millis = (end - start) / 1_000_000L;
            if (millis <= 0L) {
                throw new IllegalStateException("APK 加固耗时低于 1 ms：iteration=" + index);
            }
            samples.add(millis);
        }
        return Collections.unmodifiableList(samples);
    }

    @FunctionalInterface
    public interface Iteration {
        void run(int index) throws Exception;
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }
}
