package com.luoye.dpt.profile.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileGateReport {
    public enum Status {
        PENDING,
        PASSED,
        FAILED
    }

    public static class Result {
        private Status status = Status.PENDING;
        private String message;
        private Map<String, String> metrics = new LinkedHashMap<>();

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, String> getMetrics() {
            return metrics;
        }

        public void setMetrics(Map<String, String> metrics) {
            this.metrics = metrics;
        }
    }

    private int schemaVersion = ProfileSchemaVersions.GATE_REPORT;
    private String profileId;
    private String revisionId;
    private String candidateApkSha256;
    private Status overallStatus = Status.PENDING;
    private long generatedAtEpochMillis;
    private Map<String, Result> results = new LinkedHashMap<>();

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

    public String getCandidateApkSha256() {
        return candidateApkSha256;
    }

    public void setCandidateApkSha256(String candidateApkSha256) {
        this.candidateApkSha256 = candidateApkSha256;
    }

    public Status getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(Status overallStatus) {
        this.overallStatus = overallStatus;
    }

    public long getGeneratedAtEpochMillis() {
        return generatedAtEpochMillis;
    }

    public void setGeneratedAtEpochMillis(long generatedAtEpochMillis) {
        this.generatedAtEpochMillis = generatedAtEpochMillis;
    }

    public Map<String, Result> getResults() {
        return results;
    }

    public void setResults(Map<String, Result> results) {
        this.results = results;
    }
}
