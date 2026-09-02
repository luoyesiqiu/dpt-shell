package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.gate.scan.ProfileScanReport;
import com.luoye.dpt.profile.gate.scan.ProfileScanReportCodec;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleValidator;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class ProfileGradleEntrypointsIntegrationTest {
    private static final String APPLICATION_ID = "com.example.gradle";
    private static final List<String> REQUIRED_GATES = Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void profileCreateIsIdempotentAndDoesNotBuildOrActivateRevision() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();

        BuildResult first = runGradle(profilesRoot, activeProfilesRoot, "profileCreate",
                "-PprofileApplicationId=" + APPLICATION_ID);
        String profileId = new ProfileRegistry(profilesRoot).findProfileId(APPLICATION_ID).orElseThrow();
        Path identityPath = new ProfileIdentityStore(profilesRoot).getIdentityLockPath(profileId);
        Path sourceConfigPath = profilesRoot.resolve("source").resolve(profileId).resolve("profile.yaml");
        String identityBefore = Files.readString(identityPath);
        String sourceConfigBefore = Files.readString(sourceConfigPath);

        BuildResult second = runGradle(profilesRoot, activeProfilesRoot, "profileCreate",
                "-PprofileApplicationId=" + APPLICATION_ID);

        Assert.assertTrue(first.getOutput(), first.getOutput().contains("created=true"));
        Assert.assertTrue(second.getOutput(), second.getOutput().contains("created=false"));
        Assert.assertEquals(identityBefore, Files.readString(identityPath));
        Assert.assertEquals(sourceConfigBefore, Files.readString(sourceConfigPath));
        Assert.assertFalse(Files.exists(profilesRoot.resolve("revisions")));
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(profileId).resolve("active.json")));
    }

    @Test
    public void profileBuildWithNativeAssemblesValidatedCandidateBundle() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("bundle-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("bundle-active").toPath();
        runGradle(profilesRoot, activeProfilesRoot, "profileCreate",
                "-PprofileApplicationId=" + APPLICATION_ID);
        String profileId = new ProfileRegistry(profilesRoot).findProfileId(APPLICATION_ID)
                .orElseThrow();

        BuildResult build = runGradle(profilesRoot, activeProfilesRoot, "profileBuild",
                "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRecipe=recipe-a", "-PprofileBuildNative=true");

        ProfileRevisionManifest revision = new ProfileRevisionStore(profilesRoot)
                .load(profileId, "r000001");
        ProfileBundle bundle = new ProfileBundleValidator(profilesRoot).validateCandidate(
                APPLICATION_ID, profileId, revision.getRevisionId());
        Assert.assertTrue(build.getOutput(), build.getOutput().contains("PROFILE_BUNDLE_RESULT"));
        Assert.assertTrue(Files.size(bundle.getShellDexPath()) > 0L);
        Assert.assertTrue(Files.size(bundle.getArm64SoPath()) > 0L);
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(profileId)
                .resolve("active.json")));
    }

    @Test
    public void gradleEntrypointsEnforceStrictLifecycleWithoutImplicitSideEffects() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("lifecycle-profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("lifecycle-active").toPath();

        BuildResult missing = runGradleAndFail(profilesRoot, activeProfilesRoot, "profileShow",
                "-PprofileApplicationId=com.example.missing");
        Assert.assertTrue(missing.getOutput(), missing.getOutput().contains("DPT-PROFILE-1002"));
        Assert.assertFalse(Files.exists(profilesRoot.resolve("index.json")));

        runGradle(profilesRoot, activeProfilesRoot, "profileCreate",
                "-PprofileApplicationId=" + APPLICATION_ID);
        String profileId = new ProfileRegistry(profilesRoot).findProfileId(APPLICATION_ID).orElseThrow();
        Path identityPath = new ProfileIdentityStore(profilesRoot).getIdentityLockPath(profileId);
        Path sourceConfigPath = profilesRoot.resolve("source").resolve(profileId).resolve("profile.yaml");
        Path indexPath = profilesRoot.resolve("index.json");
        String identityBeforeShow = Files.readString(identityPath);
        String sourceBeforeShow = Files.readString(sourceConfigPath);
        String indexBeforeShow = Files.readString(indexPath);

        BuildResult shown = runGradle(profilesRoot, activeProfilesRoot, "profileShow",
                "-PprofileApplicationId=" + APPLICATION_ID);
        Assert.assertTrue(shown.getOutput(), shown.getOutput().contains("PROFILE_SHOW_RESULT"));
        Assert.assertTrue(shown.getOutput(), shown.getOutput().contains("\"activeRevisionId\":null"));
        Assert.assertEquals(identityBeforeShow, Files.readString(identityPath));
        Assert.assertEquals(sourceBeforeShow, Files.readString(sourceConfigPath));
        Assert.assertEquals(indexBeforeShow, Files.readString(indexPath));

        BuildResult firstBuild = runGradle(profilesRoot, activeProfilesRoot, "profileBuild",
                "-PprofileApplicationId=" + APPLICATION_ID, "-PprofileRecipe=recipe-a");
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest first = revisions.load(profileId, "r000001");
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, first,
                temporaryFolder.newFolder("first-artifacts").toPath());
        Assert.assertTrue(firstBuild.getOutput(), firstBuild.getOutput().contains("PROFILE_BUILD_RESULT"));
        Assert.assertEquals(RevisionStatus.CANDIDATE, first.getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(profileId).resolve("active.json")));

        Path missingReportDir = temporaryFolder.newFolder("missing-report").toPath();
        BuildResult failedPublish = runGradleAndFail(profilesRoot, activeProfilesRoot, "profilePublish",
                "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRevisionId=" + first.getRevisionId(),
                "-PprofileReportDir=" + missingReportDir);
        Assert.assertTrue(failedPublish.getOutput(),
                failedPublish.getOutput().contains("DPT-PROFILE-1004"));
        Assert.assertEquals(RevisionStatus.CANDIDATE,
                revisions.load(profileId, first.getRevisionId()).getStatus());
        Assert.assertFalse(Files.exists(activeProfilesRoot.resolve(profileId).resolve("active.json")));

        BuildResult firstPublish = runGradle(profilesRoot, activeProfilesRoot, "profilePublish",
                "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRevisionId=" + first.getRevisionId(),
                "-PprofileReportDir=" + reportDirectory("first-report", profileId, first));
        Assert.assertTrue(firstPublish.getOutput(),
                firstPublish.getOutput().contains("PROFILE_PUBLISH_RESULT"));
        Assert.assertEquals(first.getRevisionId(), activeRevisionId(profilesRoot,
                activeProfilesRoot, profileId));

        runGradle(profilesRoot, activeProfilesRoot, "profileBuild",
                "-PprofileApplicationId=" + APPLICATION_ID, "-PprofileRecipe=recipe-b");
        ProfileRevisionManifest second = revisions.load(profileId, "r000002");
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, second,
                temporaryFolder.newFolder("second-artifacts").toPath());
        BuildResult failedRollback = runGradleAndFail(profilesRoot, activeProfilesRoot,
                "profileRollback", "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRevisionId=" + second.getRevisionId());
        Assert.assertTrue(failedRollback.getOutput(),
                failedRollback.getOutput().contains("DPT-PROFILE-1006"));
        Assert.assertEquals(first.getRevisionId(), activeRevisionId(profilesRoot,
                activeProfilesRoot, profileId));

        runGradle(profilesRoot, activeProfilesRoot, "profilePublish",
                "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRevisionId=" + second.getRevisionId(),
                "-PprofileReportDir=" + reportDirectory("second-report", profileId, second));
        BuildResult rollback = runGradle(profilesRoot, activeProfilesRoot, "profileRollback",
                "-PprofileApplicationId=" + APPLICATION_ID,
                "-PprofileRevisionId=" + first.getRevisionId());
        Assert.assertTrue(rollback.getOutput(), rollback.getOutput().contains("PROFILE_ROLLBACK_RESULT"));
        Assert.assertEquals(first.getRevisionId(), activeRevisionId(profilesRoot,
                activeProfilesRoot, profileId));
    }

    private BuildResult runGradle(Path profilesRoot, Path activeProfilesRoot, String task,
                                  String... properties) {
        return runner(profilesRoot, activeProfilesRoot, task, properties).build();
    }

    private BuildResult runGradleAndFail(Path profilesRoot, Path activeProfilesRoot, String task,
                                         String... properties) {
        return runner(profilesRoot, activeProfilesRoot, task, properties).buildAndFail();
    }

    private GradleRunner runner(Path profilesRoot, Path activeProfilesRoot, String task,
                                String... properties) {
        List<String> arguments = new ArrayList<>(Arrays.asList(
                "-PprofileStateDir=" + profilesRoot,
                "-PprofileActiveDir=" + activeProfilesRoot));
        arguments.addAll(Arrays.asList(properties));
        arguments.add(task);
        return GradleRunner.create()
                .withProjectDir(findProjectRoot().toFile())
                .withArguments(arguments)
                .forwardOutput();
    }

    private Path reportDirectory(String name, String profileId, ProfileRevisionManifest revision)
            throws Exception {
        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(profileId);
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
        Path directory = temporaryFolder.newFolder(name).toPath();
        Files.writeString(directory.resolve("gate-report.json"), JSON.toJSONString(report));
        ProfileScanReport baseline = scanReport("a".repeat(64));
        ProfileScanReport candidate = scanReport(report.getCandidateApkSha256());
        ProfileScanReportCodec.write(directory.resolve("scan-baseline.json"), baseline);
        ProfileScanReportCodec.write(directory.resolve("scan-candidate.json"), candidate);
        return directory;
    }

    private static ProfileScanReport scanReport(String apkSha256) {
        ProfileScanReport report = new ProfileScanReport();
        report.setApplicationId(APPLICATION_ID);
        report.setApkSha256(apkSha256);
        report.setGeneratedAtEpochMillis(1_700_000_000_000L);
        report.setWindowId("window-1");
        report.setWindowStartEpochMillis(1_700_000_000_000L);
        report.setWindowEndEpochMillis(1_700_000_360_000L);
        LinkedHashMap<String, String> scanners = new LinkedHashMap<>();
        scanners.put("engine", "1");
        report.setScannerSet(scanners);
        return report;
    }

    private static String activeRevisionId(Path profilesRoot, Path activeProfilesRoot,
                                           String profileId) throws Exception {
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        return new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions)
                .loadActive(profileId).getRevisionId();
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isRegularFile(current.resolve("gradlew"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("dpt-shell project root not found");
    }
}
