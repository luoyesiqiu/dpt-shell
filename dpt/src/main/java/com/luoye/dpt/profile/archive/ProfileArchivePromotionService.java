package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.gate.ProfileUnifiedGate;
import com.luoye.dpt.profile.gate.ProfileUnifiedGateResult;
import com.luoye.dpt.profile.gate.scan.ProfileScanGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanGateResult;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Promotes a validated candidate archive to an immutable verified archive. */
public final class ProfileArchivePromotionService {
    private static final String GATE_REPORT_FILE_NAME = "gate-report.json";
    private static final String SCAN_BASELINE_FILE_NAME = "scan-baseline.json";
    private static final String SCAN_CANDIDATE_FILE_NAME = "scan-candidate.json";

    public Path promote(Path candidateArchive, Path reportsRoot, Path output) throws IOException {
        Path candidate = requireRegularFile(candidateArchive, "candidate Profile");
        Path reports = requireDirectory(reportsRoot, "报告目录");
        Path target = requireOutput(output);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Profile 正式归档输出已存在，拒绝覆盖：" + target);
        }

        try (ProfileArchiveBundle bundle = new ProfileArchiveReader().read(candidate)) {
            if (bundle.getStatus() != ProfileArchiveStatus.CANDIDATE) {
                throw new IOException("promote 只接受 candidate Profile：" + candidate);
            }
            ProfileArchiveManifest manifest = bundle.getManifest();
            ProfileGateReport gateReport = readGateReport(reports);
            if (!manifest.getProfileId().equals(gateReport.getProfileId())
                    || !manifest.getRevisionId().equals(gateReport.getRevisionId())) {
                throw new IOException("门禁报告与 candidate Profile 目标不匹配");
            }
            ProfileScanReport baseline = ProfileScanReportCodec.read(
                    reports.resolve(SCAN_BASELINE_FILE_NAME));
            ProfileScanReport scanCandidate = ProfileScanReportCodec.read(
                    reports.resolve(SCAN_CANDIDATE_FILE_NAME));
            ProfileScanGateResult scanResult = ProfileScanGate.evaluate(
                    manifest.getApplicationId(), gateReport.getCandidateApkSha256(), baseline,
                    scanCandidate);
            ProfileUnifiedGateResult unified = ProfileUnifiedGate.evaluate(gateReport, scanResult);
            if (!unified.isPassed()) {
                throw new IOException("Profile 晋级门禁失败："
                        + String.join(";", unified.getFailureReasons()));
            }
            return new ProfileArchiveWriter().packVerifiedAfterPromotion(bundle.getRoot(), target,
                    reports);
        }
    }

    private static ProfileGateReport readGateReport(Path reportsRoot) throws IOException {
        Path path = reportsRoot.resolve(GATE_REPORT_FILE_NAME);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("门禁报告不存在或不是普通文件：" + path);
        }
        try {
            ProfileGateReport report = JSON.parseObject(
                    Files.readAllBytes(path), ProfileGateReport.class);
            ProfileModelValidator.validate(report);
            return report;
        } catch (RuntimeException exception) {
            throw new IOException("门禁报告无效：" + path, exception);
        }
    }

    private static Path requireRegularFile(Path path, String label) throws IOException {
        if (path == null) {
            throw new IOException(label + "路径不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "不存在或不是普通文件：" + normalized);
        }
        String name = normalized.getFileName().toString();
        if (!name.endsWith(ProfileArchiveWriter.CANDIDATE_SUFFIX)) {
            throw new IOException("candidate Profile 文件名必须以 "
                    + ProfileArchiveWriter.CANDIDATE_SUFFIX + " 结尾：" + normalized);
        }
        return normalized;
    }

    private static Path requireDirectory(Path path, String label) throws IOException {
        if (path == null) {
            throw new IOException(label + "路径不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + "不存在或不是普通目录：" + normalized);
        }
        return normalized;
    }

    private static Path requireOutput(Path path) throws IOException {
        if (path == null) {
            throw new IOException("正式 Profile 输出路径不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getFileName() == null
                || !normalized.getFileName().toString().endsWith(ProfileArchiveWriter.VERIFIED_SUFFIX)
                || normalized.getFileName().toString().endsWith(ProfileArchiveWriter.CANDIDATE_SUFFIX)) {
            throw new IOException("正式 Profile 文件名必须以 "
                    + ProfileArchiveWriter.VERIFIED_SUFFIX + " 结尾且不能是 candidate：" + normalized);
        }
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("正式 Profile 输出路径不得为符号链接：" + normalized);
        }
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new IOException("正式 Profile 输出路径没有父目录：" + normalized);
        }
        Files.createDirectories(parent);
        return normalized;
    }
}
