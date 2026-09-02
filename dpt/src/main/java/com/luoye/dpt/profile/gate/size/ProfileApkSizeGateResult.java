package com.luoye.dpt.profile.gate.size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileApkSizeGateResult {
    private final String inputApkSha256;
    private final String legacyProtectedApkSha256;
    private final String candidateApkSha256;
    private final long inputBytes;
    private final long legacyProtectedBytes;
    private final long candidateBytes;
    private final long absoluteLimitBytes;
    private final long relativeIncreaseLimitBytes;
    private final boolean absoluteLimitPassed;
    private final boolean relativeIncreaseLimitPassed;
    private final List<String> failureReasons;

    ProfileApkSizeGateResult(String inputApkSha256, String legacyProtectedApkSha256,
                             String candidateApkSha256, long inputBytes,
                             long legacyProtectedBytes, long candidateBytes,
                             long absoluteLimitBytes, long relativeIncreaseLimitBytes,
                             boolean absoluteLimitPassed,
                             boolean relativeIncreaseLimitPassed,
                             List<String> failureReasons) {
        this.inputApkSha256 = inputApkSha256;
        this.legacyProtectedApkSha256 = legacyProtectedApkSha256;
        this.candidateApkSha256 = candidateApkSha256;
        this.inputBytes = inputBytes;
        this.legacyProtectedBytes = legacyProtectedBytes;
        this.candidateBytes = candidateBytes;
        this.absoluteLimitBytes = absoluteLimitBytes;
        this.relativeIncreaseLimitBytes = relativeIncreaseLimitBytes;
        this.absoluteLimitPassed = absoluteLimitPassed;
        this.relativeIncreaseLimitPassed = relativeIncreaseLimitPassed;
        this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
    }

    public boolean isPassed() {
        return absoluteLimitPassed && relativeIncreaseLimitPassed;
    }

    public String getInputApkSha256() {
        return inputApkSha256;
    }

    public String getLegacyProtectedApkSha256() {
        return legacyProtectedApkSha256;
    }

    public String getCandidateApkSha256() {
        return candidateApkSha256;
    }

    public long getInputBytes() {
        return inputBytes;
    }

    public long getLegacyProtectedBytes() {
        return legacyProtectedBytes;
    }

    public long getCandidateBytes() {
        return candidateBytes;
    }

    public long getAbsoluteLimitBytes() {
        return absoluteLimitBytes;
    }

    public long getRelativeIncreaseLimitBytes() {
        return relativeIncreaseLimitBytes;
    }

    public boolean isAbsoluteLimitPassed() {
        return absoluteLimitPassed;
    }

    public boolean isRelativeIncreaseLimitPassed() {
        return relativeIncreaseLimitPassed;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }
}
