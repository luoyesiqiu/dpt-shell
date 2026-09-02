package com.luoye.dpt.profile.model;

public class ProfileRevisionManifest {
    private int schemaVersion = ProfileSchemaVersions.REVISION_MANIFEST;
    private String profileId;
    private String applicationId;
    private String revisionId;
    private RevisionStatus status;
    private String recipeId;
    private String overlaySha256;
    private String generatorVersion;
    private String coreVersion;
    private String toolchainVersion;
    private String cacheKey;
    private String generatedSourceSha256;
    private String structuralDifferenceReportSha256;
    private String gateReportSha256;
    private long createdAtEpochMillis;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
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

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getOverlaySha256() {
        return overlaySha256;
    }

    public void setOverlaySha256(String overlaySha256) {
        this.overlaySha256 = overlaySha256;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public void setGeneratorVersion(String generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    public String getCoreVersion() {
        return coreVersion;
    }

    public void setCoreVersion(String coreVersion) {
        this.coreVersion = coreVersion;
    }

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    public void setToolchainVersion(String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getGeneratedSourceSha256() {
        return generatedSourceSha256;
    }

    public void setGeneratedSourceSha256(String generatedSourceSha256) {
        this.generatedSourceSha256 = generatedSourceSha256;
    }

    public String getGateReportSha256() {
        return gateReportSha256;
    }

    public String getStructuralDifferenceReportSha256() {
        return structuralDifferenceReportSha256;
    }

    public void setStructuralDifferenceReportSha256(String structuralDifferenceReportSha256) {
        this.structuralDifferenceReportSha256 = structuralDifferenceReportSha256;
    }

    public void setGateReportSha256(String gateReportSha256) {
        this.gateReportSha256 = gateReportSha256;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public void setCreatedAtEpochMillis(long createdAtEpochMillis) {
        this.createdAtEpochMillis = createdAtEpochMillis;
    }
}
