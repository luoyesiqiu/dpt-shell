package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.gate.scan.ProfileScanGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanGateResult;
import com.luoye.dpt.profile.model.ProfileGateReport;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileUnifiedGateTest {
    private static final String PROFILE_ID = "pf_0123456789abcdef";
    private static final String REVISION_ID = "r000001";

    @Test
    public void requiresEveryHardGateAndRejectsPendingEvidence() {
        ProfileGateReport report = gateReport();
        report.getResults().remove("scan");

        ProfileUnifiedGateResult result = ProfileUnifiedGate.evaluate(report);

        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("scan"));
    }

    @Test
    public void acceptsAllHardGatesOnlyWithComparableManualScanEvidence() {
        ProfileGateReport report = gateReport();
        ProfileScanReport baseline = scan("a".repeat(64));
        ProfileScanReport candidate = scan("b".repeat(64));
        ProfileScanGateResult scanResult = ProfileScanGate.evaluate(
                candidate.getApkSha256(), baseline, candidate);

        ProfileUnifiedGateResult result = ProfileUnifiedGate.evaluate(report, scanResult);

        Assert.assertTrue(result.getFailureReasons().toString(), result.isPassed());
    }

    @Test
    public void failsClosedWhenScanEvidenceIsNotComparable() {
        ProfileGateReport report = gateReport();
        ProfileScanReport baseline = scan("a".repeat(64));
        ProfileScanReport candidate = scan("b".repeat(64));
        candidate.setWindowId("different-window");
        ProfileScanGateResult scanResult = ProfileScanGate.evaluate(baseline, candidate);

        ProfileUnifiedGateResult result = ProfileUnifiedGate.evaluate(report, scanResult);

        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.getFailureReasons().toString().contains("scan"));
    }

    private static ProfileGateReport gateReport() {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(PROFILE_ID);
        report.setRevisionId(REVISION_ID);
        report.setCandidateApkSha256("b".repeat(64));
        report.setOverallStatus(ProfileGateReport.Status.PASSED);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        Map<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String gate : Arrays.asList("contract", "arm64", "static-identifiers",
                "structural-difference", "size", "permissions-components", "performance",
                "behavior", "scan")) {
            ProfileGateReport.Result value = new ProfileGateReport.Result();
            value.setStatus(ProfileGateReport.Status.PASSED);
            results.put(gate, value);
        }
        report.setResults(results);
        return report;
    }

    private static ProfileScanReport scan(String hash) {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId("com.example.app");
        report.setApkSha256(hash);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        report.setWindowId("window-1");
        report.setWindowStartEpochMillis(1_700_000_000_000L);
        report.setWindowEndEpochMillis(1_700_000_360_000L);
        Map<String, String> scanners = new LinkedHashMap<>();
        scanners.put("engine", "1");
        report.setScannerSet(scanners);
        return report;
    }
}
