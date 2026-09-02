package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.cli.ProfileCommandMain;
import com.luoye.dpt.profile.error.ProfileCommandException;
import com.luoye.dpt.profile.error.ProfileErrorCode;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishService;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class ProfileCommandErrorTest {
    private static final String APPLICATION_ID = "com.example.alpha";
    private static final List<String> REQUIRED_GATES = Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsStableMissingParameterAndProfileNotFoundErrorsInChinese() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();

        assertError(ProfileErrorCode.MISSING_PARAMETER, "缺少必填参数",
                () -> ProfileCommandMain.main(new String[]{"create", "--profiles-root",
                        profilesRoot.toString()}));
        assertError(ProfileErrorCode.PROFILE_NOT_FOUND, "Profile 不存在",
                () -> ProfileCommandMain.main(new String[]{"show", "--profiles-root",
                        profilesRoot.toString(), "--active-profiles-root",
                        activeProfilesRoot.toString(), "--application-id", APPLICATION_ID}));
    }

    @Test
    public void reportsMalformedCommandOptionsAsMissingParameters() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();

        assertError(ProfileErrorCode.MISSING_PARAMETER, "命令参数格式错误",
                () -> ProfileCommandMain.main(new String[]{"create", "--profiles-root",
                        profilesRoot.toString(), "--application-id"}));
    }

    @Test
    public void reportsNoActiveRevisionAndInvalidCandidateWithStableCodes() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest candidate = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, candidate,
                temporaryFolder.newFolder("invalid-candidate-artifacts").toPath());
        ProfilePublishService service = new ProfilePublishService(profilesRoot, activeProfilesRoot);

        assertError(ProfileErrorCode.ACTIVE_REVISION_MISSING, "没有活动 Revision",
                () -> service.rollback(APPLICATION_ID, null, candidate.getRevisionId()));
        assertError(ProfileErrorCode.INVALID_CANDIDATE, "候选 Revision 无效",
                () -> service.publish(APPLICATION_ID, null, candidate.getRevisionId(),
                        temporaryFolder.newFolder("missing-report").toPath()));
    }

    @Test
    public void reportsRepeatedPublishAndIllegalRollbackWithStableCodes() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest first = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("first-artifacts").toPath());
        Path firstReport = reportDirectory("first-report", passingReport(created, first));
        ProfilePublishService service = new ProfilePublishService(profilesRoot, activeProfilesRoot);
        service.publish(APPLICATION_ID, null, first.getRevisionId(), firstReport);

        assertError(ProfileErrorCode.ALREADY_PUBLISHED, "已经发布",
                () -> service.publish(APPLICATION_ID, null, first.getRevisionId(), firstReport));

        ProfileRevisionManifest second = revisions.createCandidate(candidate(created, "recipe-b"));
        assertError(ProfileErrorCode.ILLEGAL_ROLLBACK, "不能回滚",
                () -> service.rollback(APPLICATION_ID, null, second.getRevisionId()));
    }

    @Test
    public void reportsRollbackTargetMismatchAsIllegalRollback() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest active = revisions.createCandidate(candidate(created, "recipe-a"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, active,
                temporaryFolder.newFolder("active-artifacts").toPath());
        ProfilePublishService service = new ProfilePublishService(profilesRoot, activeProfilesRoot);
        service.publish(APPLICATION_ID, null, active.getRevisionId(),
                reportDirectory("active-report", passingReport(created, active)));
        ProfileRevisionManifest wrongTarget = candidate(created, "recipe-b");
        wrongTarget.setApplicationId("com.example.other");
        ProfileRevisionManifest storedWrongTarget = revisions.createCandidate(wrongTarget);

        assertError(ProfileErrorCode.ILLEGAL_ROLLBACK, "不能回滚",
                () -> service.rollback(APPLICATION_ID, null, storedWrongTarget.getRevisionId()));
    }

    private void assertError(ProfileErrorCode code, String chineseMessage, ThrowingRunnable runnable)
            throws Exception {
        try {
            runnable.run();
            Assert.fail("Expected ProfileCommandException");
        } catch (ProfileCommandException expected) {
            Assert.assertEquals(code, expected.getErrorCode());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(code.getCode()));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(chineseMessage));
        }
    }

    private Path reportDirectory(String name, ProfileGateReport report) throws IOException {
        Path directory = temporaryFolder.newFolder(name).toPath();
        Files.writeString(directory.resolve("gate-report.json"), JSON.toJSONString(report),
                StandardCharsets.UTF_8);
        return directory;
    }

    private static ProfileGateReport passingReport(ProfileCreationResult created,
                                                   ProfileRevisionManifest revision) {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(created.getProfileId());
        report.setRevisionId(revision.getRevisionId());
        report.setCandidateApkSha256("c".repeat(64));
        report.setOverallStatus(ProfileGateReport.Status.PASSED);
        report.setGeneratedAtEpochMillis(System.currentTimeMillis());
        LinkedHashMap<String, ProfileGateReport.Result> results = new LinkedHashMap<>();
        for (String gate : REQUIRED_GATES) {
            ProfileGateReport.Result result = new ProfileGateReport.Result();
            result.setStatus(ProfileGateReport.Status.PASSED);
            results.put(gate, result);
        }
        report.setResults(results);
        return report;
    }

    private static ProfileRevisionManifest candidate(ProfileCreationResult created, String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(created.getProfileId());
        value.setApplicationId(APPLICATION_ID);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("toolchain-1");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
