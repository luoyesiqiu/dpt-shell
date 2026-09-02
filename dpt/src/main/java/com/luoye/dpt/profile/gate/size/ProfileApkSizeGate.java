package com.luoye.dpt.profile.gate.size;

import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReport;
import com.luoye.dpt.profile.gate.baseline.ProfileBaselineReportCodec;

import java.util.ArrayList;
import java.util.List;

public final class ProfileApkSizeGate {
    public static final long ABSOLUTE_HEADROOM_BYTES = 2L * 1024L * 1024L;

    private ProfileApkSizeGate() {
    }

    public static ProfileApkSizeGateResult evaluate(ProfileBaselineReport baseline,
                                                    String candidateApkSha256,
                                                    long candidateBytes) {
        ProfileBaselineReportCodec.validate(baseline);
        requireSha256(candidateApkSha256);
        if (candidateBytes <= 0L) {
            throw new IllegalArgumentException("候选 APK 大小必须大于 0");
        }

        long inputBytes = baseline.getInputApk().getBytes();
        long legacyBytes = baseline.getLegacyProtectedApk().getBytes();
        long absoluteLimit = safeAdd(legacyBytes, ABSOLUTE_HEADROOM_BYTES);
        long relativeLimit = safeAdd(legacyBytes, inputBytes / 100L);
        boolean absolutePassed = candidateBytes <= absoluteLimit;
        boolean relativePassed = candidateBytes <= relativeLimit;
        List<String> failureReasons = new ArrayList<>();
        if (!absolutePassed) {
            failureReasons.add("候选 APK 超过旧加固产物 +2 MiB：limit="
                    + absoluteLimit + "，actual=" + candidateBytes);
        }
        if (!relativePassed) {
            failureReasons.add("候选 APK 相对原 APK 的增幅超过旧基线 +1 个百分点：limit="
                    + relativeLimit + "，actual=" + candidateBytes);
        }
        return new ProfileApkSizeGateResult(
                baseline.getInputApk().getSha256(),
                baseline.getLegacyProtectedApk().getSha256(),
                candidateApkSha256,
                inputBytes,
                legacyBytes,
                candidateBytes,
                absoluteLimit,
                relativeLimit,
                absolutePassed,
                relativePassed,
                failureReasons);
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("APK 包体阈值溢出", exception);
        }
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("候选 APK SHA-256 格式无效");
        }
    }
}
