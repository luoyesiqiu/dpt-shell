package com.luoye.dpt.profile.gate.structure;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.gate.ProfileArtifactUniquenessReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;
import com.luoye.dpt.util.MessageDigestUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileStructuralDifferenceReportStore {
    public static final String REPORT_FILE_NAME = "structural-difference.json";

    private final ProfileRegistry registry;
    private final ProfileRevisionStore revisions;

    public ProfileStructuralDifferenceReportStore(Path profilesRoot) {
        if (profilesRoot == null) {
            throw new IllegalArgumentException("profiles root must not be null");
        }
        this.registry = new ProfileRegistry(profilesRoot);
        this.revisions = new ProfileRevisionStore(profilesRoot);
    }

    public StoredReport write(ProfileStructuralComparisonResult structuralResult,
                              List<ProfileArtifactUniquenessReport> artifactReports)
            throws IOException {
        if (structuralResult == null || artifactReports == null) {
            throw new IllegalArgumentException("difference report inputs must not be null");
        }
        ProfileStructuralRevision candidate = structuralResult.getCandidate();
        ProfileRevisionManifest persisted = revisions.load(candidate.getProfileId(),
                candidate.getRevisionId());
        requireCandidateMatches(candidate, persisted);
        if (persisted.getStructuralDifferenceReportSha256() != null) {
            throw new IOException("差异化报告已经绑定：profileId=" + candidate.getProfileId()
                    + "，revisionId=" + candidate.getRevisionId());
        }

        Map<String, ProfileArtifactUniquenessReport> artifactsByTarget = indexArtifactReports(
                candidate, artifactReports);
        ProfileStructuralDifferenceReport report = createReport(structuralResult,
                artifactsByTarget);
        byte[] content = JSON.toJSONBytes(report);
        String sha256 = MessageDigestUtils.sha256(content);
        Path path = reportPath(candidate.getProfileId(), candidate.getRevisionId());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("差异化报告已存在，拒绝覆盖：" + path);
        }
        writeAtomically(path, content);
        try {
            revisions.bindStructuralDifferenceReport(candidate.getProfileId(),
                    candidate.getRevisionId(), sha256);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(path);
            throw failure;
        }
        return new StoredReport(path, sha256, report);
    }

    public ProfileStructuralDifferenceReport load(String profileId, String revisionId)
            throws IOException {
        ProfileRevisionManifest revision = revisions.load(profileId, revisionId);
        String expectedSha256 = revision.getStructuralDifferenceReportSha256();
        if (expectedSha256 == null) {
            throw new IOException("Revision 缺少差异化报告摘要：profileId=" + profileId
                    + "，revisionId=" + revisionId);
        }
        Path path = reportPath(profileId, revisionId);
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("差异化报告不存在或不是普通文件：" + path);
        }
        byte[] content = Files.readAllBytes(path);
        String actualSha256 = MessageDigestUtils.sha256(content);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IOException("差异化报告 SHA-256 不匹配：" + path);
        }
        ProfileStructuralDifferenceReport report;
        try {
            report = JSON.parseObject(content, ProfileStructuralDifferenceReport.class);
        } catch (RuntimeException exception) {
            throw new IOException("差异化报告无法解析：" + path, exception);
        }
        validateReportTarget(report, revision);
        return report;
    }

    public List<ProfileStructuralRevision> loadActiveHistory() throws IOException {
        List<ProfileStructuralRevision> result = new ArrayList<>();
        for (Map.Entry<String, String> binding : registry.listBindings().entrySet()) {
            for (ProfileRevisionManifest revision : revisions.list(binding.getValue())) {
                if (revision.getStatus() != RevisionStatus.ACTIVE) {
                    continue;
                }
                ProfileStructuralDifferenceReport report = load(revision.getProfileId(),
                        revision.getRevisionId());
                result.add(toStructuralRevision(report.getCandidate(), RevisionStatus.ACTIVE));
            }
        }
        result.sort(Comparator.comparing(ProfileStructuralRevision::getApplicationId)
                .thenComparing(ProfileStructuralRevision::getProfileId)
                .thenComparing(ProfileStructuralRevision::getRevisionId));
        return Collections.unmodifiableList(result);
    }

    private ProfileStructuralDifferenceReport createReport(
            ProfileStructuralComparisonResult structuralResult,
            Map<String, ProfileArtifactUniquenessReport> artifactsByTarget)
            throws IOException {
        ProfileStructuralDifferenceReport report = new ProfileStructuralDifferenceReport();
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        report.setCandidate(toEvidence(structuralResult.getCandidate()));
        List<ProfileStructuralDifferenceReport.Comparison> comparisons = new ArrayList<>();
        List<String> allFailures = new ArrayList<>();
        for (ProfileStructuralComparisonResult.Comparison source
                : structuralResult.getComparisons()) {
            String targetKey = revisionKey(source.getHistory());
            ProfileArtifactUniquenessReport artifactReport = artifactsByTarget.remove(targetKey);
            if (artifactReport == null) {
                throw new IOException("缺少完整产物唯一性报告：" + targetKey);
            }
            ProfileStructuralDifferenceReport.Comparison target = new
                    ProfileStructuralDifferenceReport.Comparison();
            target.setTarget(toEvidence(source.getHistory()));
            target.setBlocking(source.isBlocking());
            target.setJavaSimilarity(source.getJavaSimilarity());
            target.setJavaLimit(ProfileStructuralComparisonGate.JAVA_SIMILARITY_LIMIT);
            target.setNativeSimilarity(source.getNativeSimilarity());
            target.setNativeLimit(ProfileStructuralComparisonGate.NATIVE_SIMILARITY_LIMIT);
            target.setArtifactUniqueness(toArtifactEvidence(artifactReport));
            List<String> failures = failureReasons(source, artifactReport);
            target.setFailureReasons(failures);
            target.setPassed(failures.isEmpty());
            comparisons.add(target);
            allFailures.addAll(failures);
        }
        if (!artifactsByTarget.isEmpty()) {
            throw new IOException("存在没有结构比较对象的完整产物报告："
                    + artifactsByTarget.keySet());
        }
        report.setComparisons(comparisons);
        report.setFailureReasons(allFailures);
        report.setPassed(allFailures.isEmpty());
        return report;
    }

    private static List<String> failureReasons(
            ProfileStructuralComparisonResult.Comparison structural,
            ProfileArtifactUniquenessReport artifact) {
        if (!structural.isBlocking()) {
            return Collections.emptyList();
        }
        String target = revisionKey(structural.getHistory());
        List<String> failures = new ArrayList<>();
        if (structural.getJavaSimilarity() > ProfileStructuralComparisonGate.JAVA_SIMILARITY_LIMIT) {
            failures.add("Java 可变层相似度超限：target=" + target + "，actual="
                    + structural.getJavaSimilarity() + "，limit="
                    + ProfileStructuralComparisonGate.JAVA_SIMILARITY_LIMIT);
        }
        if (structural.getNativeSimilarity()
                > ProfileStructuralComparisonGate.NATIVE_SIMILARITY_LIMIT) {
            failures.add("Native 可变层相似度超限：target=" + target + "，actual="
                    + structural.getNativeSimilarity() + "，limit="
                    + ProfileStructuralComparisonGate.NATIVE_SIMILARITY_LIMIT);
        }
        if (!artifact.isPassed()) {
            failures.add("Profile 专属标识或完整壳产物发生复用：target=" + target);
        }
        return failures;
    }

    private static Map<String, ProfileArtifactUniquenessReport> indexArtifactReports(
            ProfileStructuralRevision candidate,
            List<ProfileArtifactUniquenessReport> reports) throws IOException {
        Map<String, ProfileArtifactUniquenessReport> result = new LinkedHashMap<>();
        for (ProfileArtifactUniquenessReport report : reports) {
            if (report == null || !sameReference(candidate, report.getLeft())) {
                throw new IOException("完整产物报告候选对象不匹配");
            }
            String key = revisionKey(report.getRight());
            if (result.put(key, report) != null) {
                throw new IOException("完整产物报告比较对象重复：" + key);
            }
        }
        return result;
    }

    private static ProfileStructuralDifferenceReport.RevisionEvidence toEvidence(
            ProfileStructuralRevision source) {
        ProfileStructuralDifferenceReport.RevisionEvidence target =
                new ProfileStructuralDifferenceReport.RevisionEvidence();
        target.setApplicationId(source.getApplicationId());
        target.setProfileId(source.getProfileId());
        target.setRevisionId(source.getRevisionId());
        target.setStatus(source.getStatus());
        target.setJavaFeatures(toFeatureEvidence(source.getJavaFeatures()));
        target.setNativeFeatures(toFeatureEvidence(source.getNativeFeatures()));
        return target;
    }

    private static ProfileStructuralDifferenceReport.FeatureEvidence toFeatureEvidence(
            StructuralFeatureSet source) {
        ProfileStructuralDifferenceReport.FeatureEvidence target =
                new ProfileStructuralDifferenceReport.FeatureEvidence();
        target.setLanguage(source.getLanguage());
        target.setScope(source.getScope());
        List<String> features = new ArrayList<>(source.getFeatures());
        Collections.sort(features);
        target.setFeatures(features);
        return target;
    }

    private static ProfileStructuralDifferenceReport.ArtifactUniqueness toArtifactEvidence(
            ProfileArtifactUniquenessReport source) {
        ProfileStructuralDifferenceReport.ArtifactUniqueness target =
                new ProfileStructuralDifferenceReport.ArtifactUniqueness();
        target.setPassed(source.isPassed());
        List<ProfileStructuralDifferenceReport.IdentifierConflict> conflicts = new ArrayList<>();
        for (ProfileArtifactUniquenessReport.IdentifierConflict sourceConflict
                : source.getIdentifierConflicts()) {
            ProfileStructuralDifferenceReport.IdentifierConflict targetConflict =
                    new ProfileStructuralDifferenceReport.IdentifierConflict();
            targetConflict.setLeftField(sourceConflict.getLeftField());
            targetConflict.setRightField(sourceConflict.getRightField());
            targetConflict.setValue(sourceConflict.getValue());
            conflicts.add(targetConflict);
        }
        target.setIdentifierConflicts(conflicts);
        Map<String, ProfileStructuralDifferenceReport.ArtifactHashComparison> hashes =
                new LinkedHashMap<>();
        for (Map.Entry<String, ProfileArtifactUniquenessReport.ArtifactHashComparison> entry
                : source.getArtifactHashes().entrySet()) {
            ProfileStructuralDifferenceReport.ArtifactHashComparison hash =
                    new ProfileStructuralDifferenceReport.ArtifactHashComparison();
            hash.setCandidateSha256(entry.getValue().getLeftSha256());
            hash.setTargetSha256(entry.getValue().getRightSha256());
            hash.setIdentical(entry.getValue().isIdentical());
            hashes.put(entry.getKey(), hash);
        }
        target.setArtifactHashes(hashes);
        return target;
    }

    private static ProfileStructuralRevision toStructuralRevision(
            ProfileStructuralDifferenceReport.RevisionEvidence evidence,
            RevisionStatus actualStatus) throws IOException {
        try {
            return new ProfileStructuralRevision(evidence.getApplicationId(),
                    evidence.getProfileId(), evidence.getRevisionId(), actualStatus,
                    toFeatureSet(evidence.getJavaFeatures()),
                    toFeatureSet(evidence.getNativeFeatures()));
        } catch (RuntimeException exception) {
            throw new IOException("差异化报告结构特征无效", exception);
        }
    }

    private static StructuralFeatureSet toFeatureSet(
            ProfileStructuralDifferenceReport.FeatureEvidence evidence) throws IOException {
        if (evidence == null || evidence.getFeatures() == null
                || evidence.getFeatures().isEmpty()) {
            throw new IOException("差异化报告缺少结构特征");
        }
        Set<String> features = new LinkedHashSet<>(evidence.getFeatures());
        if (features.size() != evidence.getFeatures().size()) {
            throw new IOException("差异化报告结构特征包含重复项");
        }
        return new StructuralFeatureSet(evidence.getLanguage(), evidence.getScope(), features);
    }

    private static void requireCandidateMatches(ProfileStructuralRevision candidate,
                                                ProfileRevisionManifest persisted)
            throws IOException {
        if (persisted.getStatus() != RevisionStatus.CANDIDATE
                || candidate.getStatus() != RevisionStatus.CANDIDATE
                || !persisted.getApplicationId().equals(candidate.getApplicationId())
                || !persisted.getProfileId().equals(candidate.getProfileId())
                || !persisted.getRevisionId().equals(candidate.getRevisionId())) {
            throw new IOException("差异化报告候选 Revision 与状态源不匹配");
        }
    }

    private static void validateReportTarget(ProfileStructuralDifferenceReport report,
                                             ProfileRevisionManifest revision)
            throws IOException {
        if (report == null
                || report.getSchemaVersion()
                != ProfileSchemaVersions.STRUCTURAL_DIFFERENCE_REPORT
                || report.getGeneratedAtEpochMillis() <= 0L || report.getCandidate() == null
                || !revision.getApplicationId().equals(report.getCandidate().getApplicationId())
                || !revision.getProfileId().equals(report.getCandidate().getProfileId())
                || !revision.getRevisionId().equals(report.getCandidate().getRevisionId())) {
            throw new IOException("差异化报告目标或版本不匹配：profileId="
                    + revision.getProfileId() + "，revisionId=" + revision.getRevisionId());
        }
        toFeatureSet(report.getCandidate().getJavaFeatures());
        toFeatureSet(report.getCandidate().getNativeFeatures());
    }

    private Path reportPath(String profileId, String revisionId) {
        return revisions.getRevisionManifestPath(profileId, revisionId).getParent()
                .resolve("reports").resolve(REPORT_FILE_NAME);
    }

    private static boolean sameReference(ProfileStructuralRevision revision,
                                         ProfileArtifactUniquenessReport.ProfileReference reference) {
        return revision.getApplicationId().equals(reference.getApplicationId())
                && revision.getProfileId().equals(reference.getProfileId())
                && revision.getRevisionId().equals(reference.getRevisionId());
    }

    private static String revisionKey(ProfileStructuralRevision revision) {
        return revision.getApplicationId() + "/" + revision.getProfileId() + "/"
                + revision.getRevisionId();
    }

    private static String revisionKey(
            ProfileArtifactUniquenessReport.ProfileReference reference) {
        return reference.getApplicationId() + "/" + reference.getProfileId() + "/"
                + reference.getRevisionId();
    }

    private static void writeAtomically(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".structural-difference.",
                ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("文件系统不支持差异化报告原子写入：" + path,
                        exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static final class StoredReport {
        private final Path path;
        private final String sha256;
        private final ProfileStructuralDifferenceReport report;

        private StoredReport(Path path, String sha256,
                             ProfileStructuralDifferenceReport report) {
            this.path = path;
            this.sha256 = sha256;
            this.report = report;
        }

        public Path getPath() {
            return path;
        }

        public String getSha256() {
            return sha256;
        }

        public ProfileStructuralDifferenceReport getReport() {
            return report;
        }
    }
}
