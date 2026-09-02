package com.luoye.dpt.profile.model;

import java.util.ArrayList;
import java.util.List;

public class ProfileSourceConfig {
    private int schemaVersion = ProfileSchemaVersions.SOURCE_CONFIG;
    private String applicationId;
    private String recipeId;
    private String overlayDir;
    private List<String> abis = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(String recipeId) {
        this.recipeId = recipeId;
    }

    public String getOverlayDir() {
        return overlayDir;
    }

    public void setOverlayDir(String overlayDir) {
        this.overlayDir = overlayDir;
    }

    public List<String> getAbis() {
        return abis;
    }

    public void setAbis(List<String> abis) {
        this.abis = abis;
    }
}
