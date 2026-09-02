package com.luoye.dpt.profile.model;

public class ActiveRevisionPointer {
    private int schemaVersion = ProfileSchemaVersions.ACTIVE_POINTER;
    private String profileId;
    private String revisionId;
    private String profileManifestSha256;

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

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getProfileManifestSha256() {
        return profileManifestSha256;
    }

    public void setProfileManifestSha256(String profileManifestSha256) {
        this.profileManifestSha256 = profileManifestSha256;
    }
}
