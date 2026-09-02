package com.luoye.dpt.profile.gate.baseline;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileBaselineReport {
    private int schemaVersion = ProfileSchemaVersions.BASELINE_REPORT;
    private long generatedAtEpochMillis;
    private String applicationId;
    private String abi;
    private String signingMethod;
    private boolean skipSigning;
    private Map<String, String> toolchain = new LinkedHashMap<>();
    private Environment deviceEnvironment;
    private Environment ciEnvironment;
    private Artifact inputApk;
    private Artifact legacyProtectedApk;
    private Artifact legacyArm64So;

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

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getAbi() {
        return abi;
    }

    public void setAbi(String abi) {
        this.abi = abi;
    }

    public String getSigningMethod() {
        return signingMethod;
    }

    public void setSigningMethod(String signingMethod) {
        this.signingMethod = signingMethod;
    }

    public boolean isSkipSigning() {
        return skipSigning;
    }

    public void setSkipSigning(boolean skipSigning) {
        this.skipSigning = skipSigning;
    }

    public Map<String, String> getToolchain() {
        return toolchain;
    }

    public void setToolchain(Map<String, String> toolchain) {
        this.toolchain = toolchain;
    }

    public Environment getDeviceEnvironment() {
        return deviceEnvironment;
    }

    public void setDeviceEnvironment(Environment deviceEnvironment) {
        this.deviceEnvironment = deviceEnvironment;
    }

    public Environment getCiEnvironment() {
        return ciEnvironment;
    }

    public void setCiEnvironment(Environment ciEnvironment) {
        this.ciEnvironment = ciEnvironment;
    }

    public Artifact getInputApk() {
        return inputApk;
    }

    public void setInputApk(Artifact inputApk) {
        this.inputApk = inputApk;
    }

    public Artifact getLegacyProtectedApk() {
        return legacyProtectedApk;
    }

    public void setLegacyProtectedApk(Artifact legacyProtectedApk) {
        this.legacyProtectedApk = legacyProtectedApk;
    }

    public Artifact getLegacyArm64So() {
        return legacyArm64So;
    }

    public void setLegacyArm64So(Artifact legacyArm64So) {
        this.legacyArm64So = legacyArm64So;
    }

    public static class Artifact {
        private String logicalName;
        private String fileName;
        private String sha256;
        private long bytes;

        public String getLogicalName() {
            return logicalName;
        }

        public void setLogicalName(String logicalName) {
            this.logicalName = logicalName;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }

        public long getBytes() {
            return bytes;
        }

        public void setBytes(long bytes) {
            this.bytes = bytes;
        }
    }

    public static class Environment {
        private String id;
        private Map<String, String> attributes = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }
}
