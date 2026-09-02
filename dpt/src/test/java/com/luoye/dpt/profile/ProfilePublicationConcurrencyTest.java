package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.generation.ProfileCodeGenerator;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishService;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ProfilePublicationConcurrencyTest {
    private static final String APPLICATION_A = "com.example.concurrent.alpha";
    private static final String APPLICATION_B = "com.example.concurrent.beta";
    private static final List<String> REQUIRED_GATES = Arrays.asList(
            "contract", "arm64", "static-identifiers", "structural-difference", "size",
            "permissions-components", "performance", "behavior", "scan");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void buildsTwoProfilesConcurrentlyIntoIsolatedRevisionDirectories() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("build-profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("build-active").toPath();
        ProfileCreationResult alpha = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_A);
        ProfileCreationResult beta = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_B);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProfileRevisionManifest> alphaBuild = executor.submit(() -> {
                start.await();
                return new ProfileBuildService(profilesRoot, activeRoot).build(
                        APPLICATION_A, null, "recipe-a", null);
            });
            Future<ProfileRevisionManifest> betaBuild = executor.submit(() -> {
                start.await();
                return new ProfileBuildService(profilesRoot, activeRoot).build(
                        APPLICATION_B, null, "recipe-b", null);
            });
            start.countDown();

            ProfileRevisionManifest alphaRevision = alphaBuild.get(20, TimeUnit.SECONDS);
            ProfileRevisionManifest betaRevision = betaBuild.get(20, TimeUnit.SECONDS);
            Assert.assertEquals(alpha.getProfileId(), alphaRevision.getProfileId());
            Assert.assertEquals(beta.getProfileId(), betaRevision.getProfileId());
            Assert.assertEquals(RevisionStatus.CANDIDATE, alphaRevision.getStatus());
            Assert.assertEquals(RevisionStatus.CANDIDATE, betaRevision.getStatus());
            Assert.assertTrue(Files.isDirectory(profilesRoot.resolve("generated")
                    .resolve(alpha.getProfileId()).resolve(alphaRevision.getRevisionId())));
            Assert.assertTrue(Files.isDirectory(profilesRoot.resolve("generated")
                    .resolve(beta.getProfileId()).resolve(betaRevision.getRevisionId())));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void serializesConcurrentPublishesAndLeavesOneValidAtomicPointer() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("publish-profiles").toPath();
        Path activeRoot = temporaryFolder.newFolder("publish-active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot)
                .create(APPLICATION_A);
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        ProfileRevisionManifest first = revisions.createCandidate(candidate(created,
                "recipe-a"));
        ProfileRevisionManifest second = revisions.createCandidate(candidate(created,
                "recipe-b"));
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_A, first,
                temporaryFolder.newFolder("publish-first-artifacts").toPath());
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_A, second,
                temporaryFolder.newFolder("publish-second-artifacts").toPath());
        Path firstReports = reportDirectory("concurrent-first-report", created, first);
        Path secondReports = reportDirectory("concurrent-second-report", created, second);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ActiveRevisionPointer> firstPublish = executor.submit(() -> {
                start.await();
                return new ProfilePublishService(profilesRoot, activeRoot).publish(
                        APPLICATION_A, null, first.getRevisionId(), firstReports);
            });
            Future<ActiveRevisionPointer> secondPublish = executor.submit(() -> {
                start.await();
                return new ProfilePublishService(profilesRoot, activeRoot).publish(
                        APPLICATION_A, null, second.getRevisionId(), secondReports);
            });
            start.countDown();
            Set<String> published = Set.of(
                    firstPublish.get(20, TimeUnit.SECONDS).getRevisionId(),
                    secondPublish.get(20, TimeUnit.SECONDS).getRevisionId());
            Assert.assertEquals(Set.of(first.getRevisionId(), second.getRevisionId()), published);
        } finally {
            executor.shutdownNow();
        }

        ActiveRevisionPointer active = new ProfileActiveRevisionStore(profilesRoot, activeRoot,
                revisions).loadActive(created.getProfileId());
        Assert.assertTrue(active.getRevisionId().equals(first.getRevisionId())
                || active.getRevisionId().equals(second.getRevisionId()));
        Path revisionsRoot = activeRoot.resolve(created.getProfileId()).resolve("revisions");
        assertReadOnly(revisionsRoot.resolve(first.getRevisionId()));
        assertReadOnly(revisionsRoot.resolve(second.getRevisionId()));
        try (Stream<Path> children = Files.list(revisionsRoot)) {
            Assert.assertFalse(children.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".")));
        }
    }

    private Path reportDirectory(String name, ProfileCreationResult created,
                                 ProfileRevisionManifest revision) throws Exception {
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
        Path directory = temporaryFolder.newFolder(name).toPath();
        Files.writeString(directory.resolve("gate-report.json"), JSON.toJSONString(report),
                StandardCharsets.UTF_8);
        return directory;
    }

    private static ProfileRevisionManifest candidate(ProfileCreationResult created,
                                                     String recipeId) {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId(created.getProfileId());
        value.setApplicationId(APPLICATION_A);
        value.setRecipeId(recipeId);
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion(ProfileCodeGenerator.GENERATOR_VERSION);
        value.setCoreVersion(ProfileCodeGenerator.IMMUTABLE_CORE_VERSION);
        value.setToolchainVersion("toolchain-1");
        value.setGeneratedSourceSha256("b".repeat(64));
        return value;
    }

    private static void assertReadOnly(Path revisionRoot) throws Exception {
        Assert.assertTrue(Files.isDirectory(revisionRoot));
        Assert.assertFalse(Files.getPosixFilePermissions(revisionRoot)
                .contains(PosixFilePermission.OWNER_WRITE));
        Assert.assertFalse(Files.getPosixFilePermissions(revisionRoot.resolve("profile.json"))
                .contains(PosixFilePermission.OWNER_WRITE));
    }
}
