package com.luoye.dpt.profile.error;

public enum ProfileErrorCode {
    MISSING_PARAMETER("DPT-PROFILE-1001"),
    PROFILE_NOT_FOUND("DPT-PROFILE-1002"),
    ACTIVE_REVISION_MISSING("DPT-PROFILE-1003"),
    INVALID_CANDIDATE("DPT-PROFILE-1004"),
    ALREADY_PUBLISHED("DPT-PROFILE-1005"),
    ILLEGAL_ROLLBACK("DPT-PROFILE-1006"),
    APK_PROFILE_TARGET_MISMATCH("DPT-PROFILE-2002"),
    APK_PROFILE_PROTOCOL_INCOMPATIBLE("DPT-PROFILE-2003"),
    APK_BUNDLE_INTEGRITY_FAILED("DPT-PROFILE-2004"),
    APK_ABI_INCOMPATIBLE("DPT-PROFILE-2005");

    private final String code;

    ProfileErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
