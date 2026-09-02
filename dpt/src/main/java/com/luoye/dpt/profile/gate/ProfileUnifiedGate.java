package com.luoye.dpt.profile.gate;

import com.luoye.dpt.profile.gate.scan.ProfileScanGateResult;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates every hard gate required before a Revision can become active.
 *
 * <p>The overload accepting a scan result is the strict path used when manual scan evidence is
 * available.  It deliberately fails closed for missing or incomparable scan evidence.</p>
 */
public final class ProfileUnifiedGate {
    public static final List<String> REQUIRED_GATE_IDS = Collections.unmodifiableList(Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan"));

    private ProfileUnifiedGate() {
    }

    /** Evaluates the persisted aggregate report without changing any Revision state. */
    public static ProfileUnifiedGateResult evaluate(ProfileGateReport report) {
        return evaluateInternal(report, null, false);
    }

    /** Strictly evaluates the aggregate report and the manually compared scan evidence. */
    public static ProfileUnifiedGateResult evaluate(ProfileGateReport report,
                                                    ProfileScanGateResult scanResult) {
        return evaluateInternal(report, scanResult, true);
    }

    private static ProfileUnifiedGateResult evaluateInternal(ProfileGateReport report,
                                                              ProfileScanGateResult scanResult,
                                                              boolean requireScanEvidence) {
        ProfileModelValidator.validate(report);
        Map<String, String> statuses = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        Map<String, ProfileGateReport.Result> results = report.getResults();
        for (String gateId : REQUIRED_GATE_IDS) {
            ProfileGateReport.Result result = results.get(gateId);
            if (result == null) {
                statuses.put(gateId, "MISSING");
                failures.add("硬门槛报告缺少 " + gateId);
                continue;
            }
            ProfileGateReport.Status status = result.getStatus();
            String statusName = status == null ? "MISSING" : status.name();
            statuses.put(gateId, statusName);
            if (status == null || status == ProfileGateReport.Status.FAILED) {
                failures.add("硬门槛失败：" + gateId);
            } else if (status != ProfileGateReport.Status.PASSED) {
                failures.add("硬门槛待完成：" + gateId);
            }
        }
        if (report.getOverallStatus() != ProfileGateReport.Status.PASSED) {
            failures.add("硬门槛汇总状态不是 PASSED：" + report.getOverallStatus());
        }
        if (requireScanEvidence) {
            if (scanResult == null) {
                failures.add("scan 缺少手工多引擎扫描证据");
            } else if (!scanResult.isPassed()) {
                failures.add("scan 手工报告不可比或未通过："
                        + String.join(";", scanResult.getFailureReasons()));
            }
        }
        return new ProfileUnifiedGateResult(statuses, failures);
    }
}
