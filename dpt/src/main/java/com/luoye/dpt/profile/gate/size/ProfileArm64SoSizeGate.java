package com.luoye.dpt.profile.gate.size;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReportCodec;

public final class ProfileArm64SoSizeGate {
    private ProfileArm64SoSizeGate() {
    }

    public static ProfileArm64SoSizeGateResult evaluate(ProfileBaselineReport baseline,
                                                        String candidateArm64SoSha256,
                                                        long candidateBytes) {
        ProfileBaselineReportCodec.validate(baseline);
        requireSha256(candidateArm64SoSha256);
        if (candidateBytes <= 0L) {
            throw new IllegalArgumentException("候选 arm64 壳 SO 大小必须大于 0");
        }

        ProfileBaselineReport.Artifact legacy = baseline.getLegacyArm64So();
        long limitBytes = safeAdd(legacy.getBytes(), legacy.getBytes() / 10L);
        boolean passed = candidateBytes <= limitBytes;
        String prefix = passed ? "arm64 壳 SO 包体门槛通过" : "arm64 壳 SO 超过旧壳 SO 的 110%";
        String message = prefix + "：baseline=" + legacy.getBytes()
                + "，limit=" + limitBytes + "，actual=" + candidateBytes;
        return new ProfileArm64SoSizeGateResult(
                legacy.getSha256(), candidateArm64SoSha256, legacy.getBytes(), limitBytes,
                candidateBytes, passed, message);
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("arm64 壳 SO 包体阈值溢出", exception);
        }
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("候选 arm64 壳 SO SHA-256 格式无效");
        }
    }
}
