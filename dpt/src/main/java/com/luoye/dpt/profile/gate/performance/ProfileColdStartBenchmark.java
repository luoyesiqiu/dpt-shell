package com.luoye.dpt.profile.gate.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileColdStartBenchmark {
    private final String apkSha256;
    private final String deviceEnvironmentId;
    private final List<Long> samplesMillis;
    private final int crashCount;
    private final int anrCount;

    public ProfileColdStartBenchmark(String apkSha256, String deviceEnvironmentId,
                                     List<Long> samplesMillis, int crashCount,
                                     int anrCount) {
        requireSha256(apkSha256);
        if (deviceEnvironmentId == null || deviceEnvironmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("冷启动设备环境 ID 不能为空");
        }
        if (samplesMillis == null) {
            throw new IllegalArgumentException("冷启动样本不能为空");
        }
        if (crashCount < 0 || anrCount < 0) {
            throw new IllegalArgumentException("冷启动崩溃和 ANR 次数不能为负数");
        }
        List<Long> copy = new ArrayList<>(samplesMillis.size());
        for (Long sample : samplesMillis) {
            if (sample == null || sample <= 0L) {
                throw new IllegalArgumentException("冷启动耗时样本必须大于 0");
            }
            copy.add(sample);
        }
        this.apkSha256 = apkSha256;
        this.deviceEnvironmentId = deviceEnvironmentId.trim();
        this.samplesMillis = Collections.unmodifiableList(copy);
        this.crashCount = crashCount;
        this.anrCount = anrCount;
    }

    public String getApkSha256() {
        return apkSha256;
    }

    public String getDeviceEnvironmentId() {
        return deviceEnvironmentId;
    }

    public List<Long> getSamplesMillis() {
        return samplesMillis;
    }

    public int getCrashCount() {
        return crashCount;
    }

    public int getAnrCount() {
        return anrCount;
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("冷启动 APK SHA-256 格式无效");
        }
    }
}
