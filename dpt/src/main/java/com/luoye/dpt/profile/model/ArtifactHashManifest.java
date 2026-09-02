package com.luoye.dpt.profile.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArtifactHashManifest {
    private int schemaVersion = ProfileSchemaVersions.ARTIFACT_HASHES;
    private String profileId;
    private String revisionId;
    private Map<String, String> sha256 = new LinkedHashMap<>();

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

    public Map<String, String> getSha256() {
        return sha256;
    }

    public void setSha256(Map<String, String> sha256) {
        this.sha256 = sha256;
    }
}
