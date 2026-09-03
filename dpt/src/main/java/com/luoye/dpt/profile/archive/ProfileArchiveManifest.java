package com.luoye.dpt.profile.archive;

/** Immutable metadata describing one .dptprofile file. */
public final class ProfileArchiveManifest {
    public static final int CURRENT_FORMAT_VERSION = 1;

    private int formatVersion = CURRENT_FORMAT_VERSION;
    private ProfileArchiveStatus status;
    private String applicationId;
    private String profileId;
    private String revisionId;
    private String abi;
    private int protocolVersion;
    private String generatorVersion;
    private String coreVersion;
    private String toolchainVersion;
    private String artifactSha256;
    private String reportsSha256;
    private long createdAtEpochMillis;

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public ProfileArchiveStatus getStatus() {
        return status;
    }

    public void setStatus(ProfileArchiveStatus status) {
        this.status = status;
    }

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

    public String getAbi() {
        return abi;
    }

    public void setAbi(String abi) {
        this.abi = abi;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
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

    public String getArtifactSha256() {
        return artifactSha256;
    }

    public void setArtifactSha256(String artifactSha256) {
        this.artifactSha256 = artifactSha256;
    }

    public String getReportsSha256() {
        return reportsSha256;
    }

    public void setReportsSha256(String reportsSha256) {
        this.reportsSha256 = reportsSha256;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public void setCreatedAtEpochMillis(long createdAtEpochMillis) {
        this.createdAtEpochMillis = createdAtEpochMillis;
    }
}
