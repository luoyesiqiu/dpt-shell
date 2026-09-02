package com.luoye.dpt.profile.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileArtifactUniquenessReport {
    private final ProfileReference left;
    private final ProfileReference right;
    private final List<IdentifierConflict> identifierConflicts;
    private final Map<String, ArtifactHashComparison> artifactHashes;

    ProfileArtifactUniquenessReport(ProfileReference left, ProfileReference right,
                                    List<IdentifierConflict> identifierConflicts,
                                    Map<String, ArtifactHashComparison> artifactHashes) {
        this.left = left;
        this.right = right;
        this.identifierConflicts = Collections.unmodifiableList(
                new ArrayList<>(identifierConflicts));
        this.artifactHashes = Collections.unmodifiableMap(new LinkedHashMap<>(artifactHashes));
    }

    public ProfileReference getLeft() {
        return left;
    }

    public ProfileReference getRight() {
        return right;
    }

    public List<IdentifierConflict> getIdentifierConflicts() {
        return identifierConflicts;
    }

    public Map<String, ArtifactHashComparison> getArtifactHashes() {
        return artifactHashes;
    }

    public boolean isPassed() {
        if (!identifierConflicts.isEmpty()) {
            return false;
        }
        for (ArtifactHashComparison comparison : artifactHashes.values()) {
            if (comparison.isIdentical()) {
                return false;
            }
        }
        return true;
    }

    public static final class ProfileReference {
        private final String applicationId;
        private final String profileId;
        private final String revisionId;

        ProfileReference(String applicationId, String profileId, String revisionId) {
            this.applicationId = applicationId;
            this.profileId = profileId;
            this.revisionId = revisionId;
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
    }

    public static final class IdentifierConflict {
        private final String leftField;
        private final String rightField;
        private final String value;

        IdentifierConflict(String leftField, String rightField, String value) {
            this.leftField = leftField;
            this.rightField = rightField;
            this.value = value;
        }

        public String getLeftField() {
            return leftField;
        }

        public String getRightField() {
            return rightField;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return leftField + "=" + rightField + "=" + value;
        }
    }

    public static final class ArtifactHashComparison {
        private final String leftSha256;
        private final String rightSha256;

        ArtifactHashComparison(String leftSha256, String rightSha256) {
            this.leftSha256 = leftSha256;
            this.rightSha256 = rightSha256;
        }

        public String getLeftSha256() {
            return leftSha256;
        }

        public String getRightSha256() {
            return rightSha256;
        }

        public boolean isIdentical() {
            return leftSha256.equals(rightSha256);
        }
    }
}
