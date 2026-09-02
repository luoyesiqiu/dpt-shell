package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.model.RevisionStatus;

public final class ProfileStructuralRevision {
    private final String applicationId;
    private final String profileId;
    private final String revisionId;
    private final RevisionStatus status;
    private final StructuralFeatureSet javaFeatures;
    private final StructuralFeatureSet nativeFeatures;

    public ProfileStructuralRevision(String applicationId, String profileId, String revisionId,
                                     RevisionStatus status,
                                     StructuralFeatureSet javaFeatures,
                                     StructuralFeatureSet nativeFeatures) {
        this.applicationId = requireText("applicationId", applicationId);
        this.profileId = requireText("profileId", profileId);
        this.revisionId = requireText("revisionId", revisionId);
        if (status == null || javaFeatures == null || nativeFeatures == null) {
            throw new IllegalArgumentException("structural revision fields must not be null");
        }
        if (!"java".equals(javaFeatures.getLanguage())
                || !"native".equals(nativeFeatures.getLanguage())) {
            throw new IllegalArgumentException("structural revision languages must be java/native");
        }
        this.status = status;
        this.javaFeatures = javaFeatures;
        this.nativeFeatures = nativeFeatures;
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

    public RevisionStatus getStatus() {
        return status;
    }

    public StructuralFeatureSet getJavaFeatures() {
        return javaFeatures;
    }

    public StructuralFeatureSet getNativeFeatures() {
        return nativeFeatures;
    }

    private static String requireText(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
