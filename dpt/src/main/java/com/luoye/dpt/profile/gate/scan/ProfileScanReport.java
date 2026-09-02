package com.luoye.dpt.profile.gate.scan;

import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Locally supplied evidence from one manual multi-engine scan.
 *
 * <p>This model intentionally contains no upload endpoint or APK content.  The caller copies the
 * report exported by a scanner into the report directory and the publish gate only reads it.</p>
 */
public class ProfileScanReport {
    private int schemaVersion = ProfileSchemaVersions.SCAN_REPORT;
    private String applicationId;
    private String apkSha256;
    private long generatedAtEpochMillis;
    private String windowId;
    private long windowStartEpochMillis;
    private long windowEndEpochMillis;
    private Map<String, String> scannerSet = new LinkedHashMap<>();
    private List<Detection> detections = new ArrayList<>();

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

    public String getApkSha256() {
        return apkSha256;
    }

    public void setApkSha256(String apkSha256) {
        this.apkSha256 = apkSha256;
    }

    /** Alias used by callers that distinguish a candidate report from a baseline report. */
    public String getCandidateApkSha256() {
        return apkSha256;
    }

    public void setCandidateApkSha256(String candidateApkSha256) {
        this.apkSha256 = candidateApkSha256;
    }

    public long getGeneratedAtEpochMillis() {
        return generatedAtEpochMillis;
    }

    public void setGeneratedAtEpochMillis(long generatedAtEpochMillis) {
        this.generatedAtEpochMillis = generatedAtEpochMillis;
    }

    public String getWindowId() {
        return windowId;
    }

    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }

    public String getScanWindowId() {
        return windowId;
    }

    public void setScanWindowId(String scanWindowId) {
        this.windowId = scanWindowId;
    }

    public long getWindowStartEpochMillis() {
        return windowStartEpochMillis;
    }

    public void setWindowStartEpochMillis(long windowStartEpochMillis) {
        this.windowStartEpochMillis = windowStartEpochMillis;
    }

    public long getWindowEndEpochMillis() {
        return windowEndEpochMillis;
    }

    public void setWindowEndEpochMillis(long windowEndEpochMillis) {
        this.windowEndEpochMillis = windowEndEpochMillis;
    }

    public Map<String, String> getScannerSet() {
        return scannerSet;
    }

    public void setScannerSet(Map<String, String> scannerSet) {
        this.scannerSet = scannerSet;
    }

    /** Convenience view for reports that only record scanner IDs. */
    public List<String> getScannerIds() {
        return new ArrayList<>(scannerSet == null
                ? java.util.Collections.emptySet() : scannerSet.keySet());
    }

    public void setScannerIds(List<String> scannerIds) {
        Map<String, String> values = new LinkedHashMap<>();
        if (scannerIds != null) {
            for (String scannerId : scannerIds) {
                values.put(scannerId, "unknown");
            }
        }
        this.scannerSet = values;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public void setDetections(List<Detection> detections) {
        this.detections = detections;
    }

    public static class Detection {
        private String scannerId;
        private String scannerVersion;
        private String signature;
        private String family;
        private String category;
        private String severity;

        public String getScannerId() {
            return scannerId;
        }

        public void setScannerId(String scannerId) {
            this.scannerId = scannerId;
        }

        public String getScannerVersion() {
            return scannerVersion;
        }

        public void setScannerVersion(String scannerVersion) {
            this.scannerVersion = scannerVersion;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getFamily() {
            return family;
        }

        public void setFamily(String family) {
            this.family = family;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        // Compatibility aliases for common scanner export field names.
        public String getEngine() {
            return scannerId;
        }

        public void setEngine(String engine) {
            this.scannerId = engine;
        }

        public String getLabel() {
            return signature;
        }

        public void setLabel(String label) {
            this.signature = label;
        }
    }
}
