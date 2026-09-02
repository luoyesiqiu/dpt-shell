package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ProfileDurationStatistics {
    private ProfileDurationStatistics() {
    }

    static long percentile(List<Long> samples, int percentile) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("性能样本不能为空");
        }
        if (percentile <= 0 || percentile > 100) {
            throw new IllegalArgumentException("百分位必须在 1 到 100 之间");
        }
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int rank = (sorted.size() * percentile + 99) / 100;
        return sorted.get(rank - 1);
    }

    static long increasedLimit(long baseline, int percent) {
        long whole = Math.multiplyExact(baseline / 100L, percent);
        long remainder = Math.multiplyExact(baseline % 100L, percent) / 100L;
        return Math.addExact(baseline, Math.addExact(whole, remainder));
    }
}
