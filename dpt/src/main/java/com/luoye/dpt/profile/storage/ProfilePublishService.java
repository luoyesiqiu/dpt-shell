package com.luoye.dpt.profile.storage;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.gate.ProfileUnifiedGate;
import com.luoye.dpt.profile.gate.ProfileUnifiedGateResult;
import com.luoye.dpt.profile.gate.scan.ProfileScanGate;
import com.luoye.dpt.profile.gate.scan.ProfileScanGateResult;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class ProfilePublishService {
    private static final String GATE_REPORT_FILE_NAME = "gate-report.json";
    private static final String SCAN_BASELINE_FILE_NAME = "scan-baseline.json";
    private static final String SCAN_CANDIDATE_FILE_NAME = "scan-candidate.json";

    private final ProfileRegistry registry;
    private final ProfileRevisionStore revisionStore;
    private final ProfileActiveRevisionStore activeRevisionStore;
    private final ProfilePublishedBundleStore publishedBundleStore;
    private final ProfileBundleValidator bundleValidator;
    private final Path publishLocksRoot;

    public ProfilePublishService(Path profilesRoot, Path activeProfilesRoot) {
        if (profilesRoot == null || activeProfilesRoot == null) {
            throw new IllegalArgumentException("profile publish roots must not be null");
        }
        this.registry = new ProfileRegistry(profilesRoot);
        this.revisionStore = new ProfileRevisionStore(profilesRoot);
        this.activeRevisionStore = new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot,
                revisionStore);
        this.publishedBundleStore = new ProfilePublishedBundleStore(profilesRoot,
                activeProfilesRoot);
        this.bundleValidator = new ProfileBundleValidator(profilesRoot);
        this.publishLocksRoot = activeProfilesRoot.resolve(".publish-state");
    }

    public ActiveRevisionPointer publish(String applicationId, String profileId, String revisionId,
                                         Path reportDir) throws IOException {
        return publishLockedEntry(applicationId, profileId, revisionId, reportDir,
                null, null, false);
    }

    /** Publishes only when the report directory contains a complete manual scan pair. */
    public ActiveRevisionPointer publishStrict(String applicationId, String profileId,
                                               String revisionId, Path reportDir)
            throws IOException {
        return publishLockedEntry(applicationId, profileId, revisionId, reportDir,
                null, null, true);
    }

    /**
     * Publishes a candidate with explicitly supplied manual scan reports.  Both scan paths must
     * be provided together; the reports are read locally and are never uploaded by this service.
     */
    public ActiveRevisionPointer publish(String applicationId, String profileId, String revisionId,
                                         Path reportDir, Path scanBaselinePath,
                                         Path scanCandidatePath) throws IOException {
        return publishLockedEntry(applicationId, profileId, revisionId, reportDir,
                scanBaselinePath, scanCandidatePath, true);
    }

    private ActiveRevisionPointer publishLockedEntry(String applicationId, String profileId,
                                                     String revisionId, Path reportDir,
                                                     Path scanBaselinePath,
                                                     Path scanCandidatePath,
                                                     boolean requireScanEvidence)
            throws IOException {
        Binding binding = resolveBinding(applicationId, profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        try (ProfileFileLock ignored = ProfileFileLock.acquire(publishLocksRoot,
                binding.profileId)) {
            return publishLocked(binding, revisionId, reportDir, scanBaselinePath,
                    scanCandidatePath, requireScanEvidence);
        }
    }

    private ActiveRevisionPointer publishLocked(Binding binding, String revisionId,
                                                Path reportDir, Path explicitScanBaselinePath,
                                                Path explicitScanCandidatePath,
                                                boolean requireScanEvidence) throws IOException {
        ProfileRevisionManifest candidate;
        try {
            candidate = revisionStore.load(binding.profileId, revisionId);
        } catch (ProfileRevisionException e) {
            throw invalidCandidate("候选 Revision 无效：revisionId=" + revisionId, e);
        }
        requireTarget(candidate, binding, ProfileErrorCode.INVALID_CANDIDATE,
                "候选 Revision 无效");
        if (candidate.getStatus() != RevisionStatus.CANDIDATE) {
            if (candidate.getStatus() == RevisionStatus.ACTIVE
                    || candidate.getStatus() == RevisionStatus.VERIFIED) {
                throw new ProfileCommandException(ProfileErrorCode.ALREADY_PUBLISHED,
                        "Revision 已经发布：revisionId=" + revisionId
                                + "，status=" + candidate.getStatus());
            }
            throw invalidCandidate("候选 Revision 无效：revisionId=" + revisionId
                    + "，status=" + candidate.getStatus(), null);
        }

        try {
            bundleValidator.validateCandidate(binding.applicationId, binding.profileId,
                    revisionId);
        } catch (IOException | RuntimeException exception) {
            throw invalidCandidate("候选 Revision Bundle 无效：" + exception.getMessage(),
                    exception);
        }

        LoadedGateReport loadedReport;
        try {
            loadedReport = loadGateReport(reportDir, binding.profileId, revisionId);
        } catch (ProfileCommandException e) {
            throw e;
        } catch (IOException e) {
            throw invalidCandidate("候选 Revision 无效：" + e.getMessage(), e);
        }
        // If the report directory contains manual scanner exports, require both files and
        // compare them before publishing.  Keeping the optional discovery here preserves the
        // source-compatible aggregate-report API; production callers use the strict overload.
        ProfileScanGateResult scanResult;
        try {
            scanResult = loadScanResult(reportDir, loadedReport.report,
                    binding.applicationId, explicitScanBaselinePath, explicitScanCandidatePath,
                    requireScanEvidence);
        } catch (IOException | RuntimeException exception) {
            rejectCandidateAfterGateFailure(binding.profileId, revisionId, loadedReport,
                    exception);
            if (exception instanceof ProfileCommandException) {
                throw (ProfileCommandException) exception;
            }
            throw invalidCandidate("候选 Revision 无效：手工扫描报告" + exception.getMessage(),
                    exception);
        }
        ProfileUnifiedGateResult unified;
        if (scanResult != null || requireScanEvidence) {
            unified = ProfileUnifiedGate.evaluate(loadedReport.report, scanResult);
        } else {
            unified = ProfileUnifiedGate.evaluate(loadedReport.report);
        }
        if (!unified.isPassed()) {
            persistGateReport(binding.profileId, revisionId, loadedReport.content);
            revisionStore.markRejected(binding.profileId, revisionId, loadedReport.sha256);
            throw invalidCandidate("候选 Revision 无效：统一发布门禁失败 "
                    + String.join(";", unified.getFailureReasons()), null);
        }

        persistGateReport(binding.profileId, revisionId, loadedReport.content);
        boolean bundlePublished = false;
        try {
            publishedBundleStore.publishCandidate(binding.applicationId, binding.profileId,
                    revisionId);
            bundlePublished = true;
            revisionStore.markVerified(binding.profileId, revisionId, loadedReport.sha256);
        } catch (IOException | RuntimeException exception) {
            if (bundlePublished) {
                try {
                    publishedBundleStore.deletePublishedCandidate(binding.profileId, revisionId);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw invalidCandidate("候选 Revision Bundle 发布失败：" + exception.getMessage(),
                    exception);
        }
        return activeRevisionStore.activate(binding.profileId, revisionId);
    }

    public ActiveRevisionPointer rollback(String applicationId, String profileId, String revisionId)
            throws IOException {
        Binding binding = resolveBinding(applicationId, profileId);
        ProfileModelValidator.validateRevisionId(revisionId);
        Path activePointerPath = activeRevisionStore.getActivePointerPath(binding.profileId);
        if (!Files.isRegularFile(activePointerPath)) {
            throw new ProfileCommandException(ProfileErrorCode.ACTIVE_REVISION_MISSING,
                    "Profile 没有活动 Revision：profileId=" + binding.profileId);
        }
        try {
            activeRevisionStore.loadActive(binding.profileId);
        } catch (ProfileActiveRevisionException e) {
            throw new ProfileCommandException(ProfileErrorCode.ACTIVE_REVISION_MISSING,
                    "Profile 没有可用的活动 Revision：profileId=" + binding.profileId, e);
        }
        ProfileRevisionManifest revision;
        try {
            revision = revisionStore.load(binding.profileId, revisionId);
        } catch (ProfileRevisionException e) {
            throw new ProfileCommandException(ProfileErrorCode.ILLEGAL_ROLLBACK,
                    "目标 Revision 不能回滚：revisionId=" + revisionId, e);
        }
        requireTarget(revision, binding, ProfileErrorCode.ILLEGAL_ROLLBACK,
                "目标 Revision 不能回滚");
        try {
            return activeRevisionStore.rollback(binding.profileId, revisionId);
        } catch (ProfileActiveRevisionException e) {
            throw new ProfileCommandException(ProfileErrorCode.ILLEGAL_ROLLBACK,
                    "目标 Revision 不能回滚：revisionId=" + revisionId
                            + "，status=" + revision.getStatus(), e);
        }
    }

    private Binding resolveBinding(String applicationId, String profileId) throws IOException {
        String normalizedApplicationId = blankToNull(applicationId);
        String normalizedProfileId = blankToNull(profileId);
        if (normalizedApplicationId == null && normalizedProfileId == null) {
            throw new ProfileCommandException(ProfileErrorCode.MISSING_PARAMETER,
                    "缺少必填参数：applicationId 或 profileId");
        }
        if (normalizedApplicationId != null) {
            ProfileModelValidator.validateApplicationId(normalizedApplicationId);
            Optional<String> registeredProfileId = registry.findProfileId(normalizedApplicationId);
            if (!registeredProfileId.isPresent()) {
                throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                        "Profile 不存在：applicationId=" + normalizedApplicationId);
            }
            if (normalizedProfileId != null && !normalizedProfileId.equals(registeredProfileId.get())) {
                throw new IOException("profile binding conflict for applicationId: "
                        + normalizedApplicationId);
            }
            return new Binding(normalizedApplicationId, registeredProfileId.get());
        }

        ProfileModelValidator.validateProfileId(normalizedProfileId);
        Optional<String> registeredApplicationId = registry.findApplicationId(normalizedProfileId);
        if (!registeredApplicationId.isPresent()) {
            throw new ProfileCommandException(ProfileErrorCode.PROFILE_NOT_FOUND,
                    "Profile 不存在：profileId=" + normalizedProfileId);
        }
        return new Binding(registeredApplicationId.get(), normalizedProfileId);
    }

    private static LoadedGateReport loadGateReport(Path reportDir, String profileId,
                                                   String revisionId) throws IOException {
        if (reportDir == null) {
            throw invalidCandidate("候选 Revision 无效：缺少门禁报告目录", null);
        }
        Path reportPath = reportDir.resolve(GATE_REPORT_FILE_NAME);
        if (!Files.isRegularFile(reportPath)) {
            throw new IOException("gate report is missing: " + reportPath);
        }
        byte[] content = Files.readAllBytes(reportPath);
        try {
            ProfileGateReport report = JSON.parseObject(content, ProfileGateReport.class);
            ProfileModelValidator.validate(report);
            if (!profileId.equals(report.getProfileId()) || !revisionId.equals(report.getRevisionId())) {
                throw new IOException("gate report target mismatch: " + reportPath);
            }
            return new LoadedGateReport(report, content, MessageDigestUtils.sha256(content));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("invalid gate report: " + reportPath, e);
        }
    }

    private static ProfileScanGateResult loadScanResult(Path reportDir,
                                                        ProfileGateReport gateReport,
                                                        String expectedApplicationId,
                                                        Path explicitBaselinePath,
                                                        Path explicitCandidatePath,
                                                        boolean requireScanEvidence)
            throws IOException {
        if ((explicitBaselinePath == null) != (explicitCandidatePath == null)) {
            throw invalidCandidate("候选 Revision 无效：手工扫描报告路径必须同时提供基线和候选", null);
        }
        Path baselinePath = explicitBaselinePath;
        Path candidatePath = explicitCandidatePath;
        if (baselinePath != null) {
            ProfileScanReport baseline = ProfileScanReportCodec.read(baselinePath);
            ProfileScanReport candidate = ProfileScanReportCodec.read(candidatePath);
            return ProfileScanGate.evaluate(expectedApplicationId,
                    gateReport.getCandidateApkSha256(), baseline, candidate);
        }
        if (reportDir == null) {
            return null;
        }
        baselinePath = reportDir.resolve(SCAN_BASELINE_FILE_NAME);
        candidatePath = reportDir.resolve(SCAN_CANDIDATE_FILE_NAME);
        boolean baselineExists = Files.exists(baselinePath);
        boolean candidateExists = Files.exists(candidatePath);
        if (!baselineExists && !candidateExists) {
            if (requireScanEvidence) {
                throw invalidCandidate("候选 Revision 无效：缺少手工扫描报告（"
                        + SCAN_BASELINE_FILE_NAME + " 和 " + SCAN_CANDIDATE_FILE_NAME + "）", null);
            }
            return null;
        }
        if (!baselineExists || !candidateExists) {
            throw invalidCandidate("候选 Revision 无效：手工扫描报告必须同时提供 "
                    + SCAN_BASELINE_FILE_NAME + " 和 " + SCAN_CANDIDATE_FILE_NAME, null);
        }
        ProfileScanReport baseline = ProfileScanReportCodec.read(baselinePath);
        ProfileScanReport candidate = ProfileScanReportCodec.read(candidatePath);
        return ProfileScanGate.evaluate(expectedApplicationId, gateReport.getCandidateApkSha256(),
                baseline, candidate);
    }

    private void rejectCandidateAfterGateFailure(String profileId, String revisionId,
                                                  LoadedGateReport loadedReport,
                                                  Exception failure) {
        try {
            persistGateReport(profileId, revisionId, loadedReport.content);
        } catch (IOException persistFailure) {
            failure.addSuppressed(persistFailure);
        }
        try {
            revisionStore.markRejected(profileId, revisionId, loadedReport.sha256);
        } catch (IOException rejectFailure) {
            failure.addSuppressed(rejectFailure);
        }
    }

    private void persistGateReport(String profileId, String revisionId, byte[] content)
            throws IOException {
        Path revisionDirectory = revisionStore.getRevisionManifestPath(profileId, revisionId).getParent();
        Path targetPath = revisionDirectory.resolve("reports").resolve(GATE_REPORT_FILE_NAME);
        Files.createDirectories(targetPath.getParent());
        Path temporaryPath = Files.createTempFile(targetPath.getParent(), ".gate-report.", ".tmp");
        try {
            Files.write(temporaryPath, content, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporaryPath, targetPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic gate report replacement is not supported: " + targetPath, e);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void requireTarget(ProfileRevisionManifest revision, Binding binding,
                                      ProfileErrorCode errorCode, String messagePrefix)
            throws ProfileCommandException {
        if (!binding.profileId.equals(revision.getProfileId())
                || !binding.applicationId.equals(revision.getApplicationId())) {
            throw new ProfileCommandException(errorCode, messagePrefix
                    + "：目标应用不匹配，revisionId=" + revision.getRevisionId());
        }
    }

    private static ProfileCommandException invalidCandidate(String message, Throwable cause) {
        return new ProfileCommandException(ProfileErrorCode.INVALID_CANDIDATE, message, cause);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static final class Binding {
        private final String applicationId;
        private final String profileId;

        private Binding(String applicationId, String profileId) {
            this.applicationId = applicationId;
            this.profileId = profileId;
        }
    }

    private static final class LoadedGateReport {
        private final ProfileGateReport report;
        private final byte[] content;
        private final String sha256;

        private LoadedGateReport(ProfileGateReport report, byte[] content, String sha256) {
            this.report = report;
            this.content = content;
            this.sha256 = sha256;
        }
    }

}
