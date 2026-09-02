package com.luoye.dpt.profile.gate.behavior;

import java.util.Objects;

public final class ProfileBehaviorCase {
    private final String applicationId;
    private final String profileId;
    private final String revisionId;
    private final String recipeId;
    private final String overlaySha256;
    private final ProfileBehaviorVariant variant;
    private final String candidateApkSha256;

    public ProfileBehaviorCase(String applicationId, String profileId, String revisionId,
                               String recipeId, String overlaySha256,
                               ProfileBehaviorVariant variant,
                               String candidateApkSha256) {
        requireText("applicationId", applicationId);
        requireText("Profile ID", profileId);
        requireText("Revision ID", revisionId);
        requireText("Recipe ID", recipeId);
        requireSha256("Overlay", overlaySha256);
        requireSha256("候选 APK", candidateApkSha256);
        if (variant == null) {
            throw new IllegalArgumentException("行为矩阵 APK 类型不能为空");
        }
        this.applicationId = applicationId;
        this.profileId = profileId;
        this.revisionId = revisionId;
        this.recipeId = recipeId;
        this.overlaySha256 = overlaySha256;
        this.variant = variant;
        this.candidateApkSha256 = candidateApkSha256;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public String getOverlaySha256() {
        return overlaySha256;
    }

    public ProfileBehaviorVariant getVariant() {
        return variant;
    }

    public String getCandidateApkSha256() {
        return candidateApkSha256;
    }

    String key() {
        return applicationId + "|" + profileId + "|" + revisionId + "|" + variant.name();
    }

    String groupKey() {
        return recipeId + "|" + overlaySha256;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProfileBehaviorCase)) {
            return false;
        }
        ProfileBehaviorCase that = (ProfileBehaviorCase) other;
        return applicationId.equals(that.applicationId)
                && profileId.equals(that.profileId)
                && revisionId.equals(that.revisionId)
                && recipeId.equals(that.recipeId)
                && overlaySha256.equals(that.overlaySha256)
                && variant == that.variant
                && candidateApkSha256.equals(that.candidateApkSha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, profileId, revisionId, recipeId, overlaySha256,
                variant, candidateApkSha256);
    }

    private static void requireText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static void requireSha256(String label, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " SHA-256 格式无效");
        }
    }
}
