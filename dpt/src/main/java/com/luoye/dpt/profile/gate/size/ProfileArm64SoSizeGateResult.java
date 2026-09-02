package com.luoye.dpt.profile.gate.size;

public final class ProfileArm64SoSizeGateResult {
    private final String legacyArm64SoSha256;
    private final String candidateArm64SoSha256;
    private final long legacyBytes;
    private final long limitBytes;
    private final long candidateBytes;
    private final boolean passed;
    private final String message;

    ProfileArm64SoSizeGateResult(String legacyArm64SoSha256,
                                 String candidateArm64SoSha256,
                                 long legacyBytes, long limitBytes,
                                 long candidateBytes, boolean passed,
                                 String message) {
        this.legacyArm64SoSha256 = legacyArm64SoSha256;
        this.candidateArm64SoSha256 = candidateArm64SoSha256;
        this.legacyBytes = legacyBytes;
        this.limitBytes = limitBytes;
        this.candidateBytes = candidateBytes;
        this.passed = passed;
        this.message = message;
    }

    public String getLegacyArm64SoSha256() {
        return legacyArm64SoSha256;
    }

    public String getCandidateArm64SoSha256() {
        return candidateArm64SoSha256;
    }

    public long getLegacyBytes() {
        return legacyBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public long getCandidateBytes() {
        return candidateBytes;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
