package com.luoye.dpt.profile.gate.structure;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.RevisionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileStructuralDifferenceReport {
    private int schemaVersion = ProfileSchemaVersions.STRUCTURAL_DIFFERENCE_REPORT;
    private long generatedAtEpochMillis;
    private boolean passed;
    private RevisionEvidence candidate;
    private List<Comparison> comparisons = new ArrayList<>();
    private List<String> failureReasons = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public long getGeneratedAtEpochMillis() {
        return generatedAtEpochMillis;
    }

    public void setGeneratedAtEpochMillis(long generatedAtEpochMillis) {
        this.generatedAtEpochMillis = generatedAtEpochMillis;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public RevisionEvidence getCandidate() {
        return candidate;
    }

    public void setCandidate(RevisionEvidence candidate) {
        this.candidate = candidate;
    }

    public List<Comparison> getComparisons() {
        return comparisons;
    }

    public void setComparisons(List<Comparison> comparisons) {
        this.comparisons = comparisons;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    public void setFailureReasons(List<String> failureReasons) {
        this.failureReasons = failureReasons;
    }

    public static class RevisionEvidence {
        private String applicationId;
        private String profileId;
        private String revisionId;
        private RevisionStatus status;
        private FeatureEvidence javaFeatures;
        private FeatureEvidence nativeFeatures;

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getProfileId() {
            return profileId;
        }

        public void setProfileId(String profileId) {
            this.profileId = profileId;
        }

        public String getRevisionId() {
            return revisionId;
        }

        public void setRevisionId(String revisionId) {
            this.revisionId = revisionId;
        }

        public RevisionStatus getStatus() {
            return status;
        }

        public void setStatus(RevisionStatus status) {
            this.status = status;
        }

        public FeatureEvidence getJavaFeatures() {
            return javaFeatures;
        }

        public void setJavaFeatures(FeatureEvidence javaFeatures) {
            this.javaFeatures = javaFeatures;
        }

        public FeatureEvidence getNativeFeatures() {
            return nativeFeatures;
        }

        public void setNativeFeatures(FeatureEvidence nativeFeatures) {
            this.nativeFeatures = nativeFeatures;
        }
    }

    public static class FeatureEvidence {
        private String language;
        private String scope;
        private List<String> features = new ArrayList<>();

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public List<String> getFeatures() {
            return features;
        }

        public void setFeatures(List<String> features) {
            this.features = features;
        }
    }

    public static class Comparison {
        private RevisionEvidence target;
        private boolean blocking;
        private boolean passed;
        private double javaSimilarity;
        private double javaLimit;
        private double nativeSimilarity;
        private double nativeLimit;
        private ArtifactUniqueness artifactUniqueness;
        private List<String> failureReasons = new ArrayList<>();

        public RevisionEvidence getTarget() {
            return target;
        }

        public void setTarget(RevisionEvidence target) {
            this.target = target;
        }

        public boolean isBlocking() {
            return blocking;
        }

        public void setBlocking(boolean blocking) {
            this.blocking = blocking;
        }

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public double getJavaSimilarity() {
            return javaSimilarity;
        }

        public void setJavaSimilarity(double javaSimilarity) {
            this.javaSimilarity = javaSimilarity;
        }

        public double getJavaLimit() {
            return javaLimit;
        }

        public void setJavaLimit(double javaLimit) {
            this.javaLimit = javaLimit;
        }

        public double getNativeSimilarity() {
            return nativeSimilarity;
        }

        public void setNativeSimilarity(double nativeSimilarity) {
            this.nativeSimilarity = nativeSimilarity;
        }

        public double getNativeLimit() {
            return nativeLimit;
        }

        public void setNativeLimit(double nativeLimit) {
            this.nativeLimit = nativeLimit;
        }

        public ArtifactUniqueness getArtifactUniqueness() {
            return artifactUniqueness;
        }

        public void setArtifactUniqueness(ArtifactUniqueness artifactUniqueness) {
            this.artifactUniqueness = artifactUniqueness;
        }

        public List<String> getFailureReasons() {
            return failureReasons;
        }

        public void setFailureReasons(List<String> failureReasons) {
            this.failureReasons = failureReasons;
        }
    }

    public static class ArtifactUniqueness {
        private boolean passed;
        private List<IdentifierConflict> identifierConflicts = new ArrayList<>();
        private Map<String, ArtifactHashComparison> artifactHashes = new LinkedHashMap<>();

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public List<IdentifierConflict> getIdentifierConflicts() {
            return identifierConflicts;
        }

        public void setIdentifierConflicts(List<IdentifierConflict> identifierConflicts) {
            this.identifierConflicts = identifierConflicts;
        }

        public Map<String, ArtifactHashComparison> getArtifactHashes() {
            return artifactHashes;
        }

        public void setArtifactHashes(Map<String, ArtifactHashComparison> artifactHashes) {
            this.artifactHashes = artifactHashes;
        }
    }

    public static class IdentifierConflict {
        private String leftField;
        private String rightField;
        private String value;

        public String getLeftField() {
            return leftField;
        }

        public void setLeftField(String leftField) {
            this.leftField = leftField;
        }

        public String getRightField() {
            return rightField;
        }

        public void setRightField(String rightField) {
            this.rightField = rightField;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class ArtifactHashComparison {
        private String candidateSha256;
        private String targetSha256;
        private boolean identical;

        public String getCandidateSha256() {
            return candidateSha256;
        }

        public void setCandidateSha256(String candidateSha256) {
            this.candidateSha256 = candidateSha256;
        }

        public String getTargetSha256() {
            return targetSha256;
        }

        public void setTargetSha256(String targetSha256) {
            this.targetSha256 = targetSha256;
        }

        public boolean isIdentical() {
            return identical;
        }

        public void setIdentical(boolean identical) {
            this.identical = identical;
        }
    }
}
