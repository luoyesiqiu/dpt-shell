package com.luoye.dpt.profile.archive;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;
import com.luoye.dpt.profile.storage.ProfileRegistry;
import com.luoye.dpt.util.MessageDigestUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class ProfileArchiveTest {
    private static final String APPLICATION_ID = "com.example.archive";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void packsDeterministicallyAndReadsOnARegistryFreeDirectory() throws Exception {
        Fixture fixture = fixture();
        Path first = temporaryFolder.newFolder("dist-1").toPath()
                .resolve("profile.candidate.dptprofile");
        Path second = temporaryFolder.newFolder("dist-2").toPath()
                .resolve("profile.candidate.dptprofile");

        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), first);
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), second);

        Assert.assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        Path extraction = temporaryFolder.newFolder("extract").toPath();
        try (ProfileArchiveBundle bundle = new ProfileArchiveReader().read(first, extraction)) {
            Assert.assertEquals(ProfileArchiveStatus.CANDIDATE, bundle.getStatus());
            Assert.assertEquals(APPLICATION_ID, bundle.getManifest().getApplicationId());
            Assert.assertEquals(fixture.profileId, bundle.getContract().getProfileId());
            Assert.assertTrue(Files.isRegularFile(bundle.getShellDexPath()));
            Assert.assertTrue(Files.isRegularFile(bundle.getArm64SoPath()));
        }
        Assert.assertTrue("reader must not escape extraction root",
                Files.list(extraction).findAny().isPresent());
        Assert.assertFalse("archive consumption must not create registry state",
                Files.exists(extraction.resolve("index.json")));
    }

    @Test
    public void rejectsCandidateWhenAProductionReaderIsRequested() throws Exception {
        Fixture fixture = fixture();
        Path archive = temporaryFolder.newFolder("candidate").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), archive);

        try {
            new ProfileArchiveReader().readVerified(archive,
                    temporaryFolder.newFolder("verified-extract").toPath());
            Assert.fail("candidate archive must not be consumable as verified");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("candidate"));
        }
    }

    @Test
    public void verifiedReaderCleansCallerExtractionRootWhenCandidateIsRejected() throws Exception {
        Fixture fixture = fixture();
        Path archive = temporaryFolder.newFolder("candidate-cleanup").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), archive);
        Path extraction = temporaryFolder.newFolder("candidate-cleanup-extract").toPath();

        try {
            new ProfileArchiveReader().readVerified(archive, extraction);
            Assert.fail("candidate archive must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("candidate"));
        }

        try (java.util.stream.Stream<Path> children = Files.list(extraction)) {
            Assert.assertFalse("failed verified read must clean extracted artifacts",
                    children.findAny().isPresent());
        }
    }

    @Test
    public void rejectsTamperingAndPathTraversalBeforeExtraction() throws Exception {
        Fixture fixture = fixture();
        Path archive = temporaryFolder.newFolder("tamper").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), archive);
        Path tampered = temporaryFolder.newFolder("tampered-copy").toPath()
                .resolve("profile.candidate.dptprofile");
        tamperEntry(archive, tampered, "profile.json");
        try {
            new ProfileArchiveReader().read(tampered,
                    temporaryFolder.newFolder("tamper-extract").toPath());
            Assert.fail("tampered archive must be rejected");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), !expected.getMessage().isEmpty());
        }

        Path malicious = temporaryFolder.newFolder("malicious").toPath()
                .resolve("bad.dptprofile");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(malicious.toFile()))) {
            output.putNextEntry(new ZipEntry("../escaped.txt"));
            output.write("escape".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path extraction = temporaryFolder.newFolder("safe-extract").toPath();
        try {
            new ProfileArchiveReader().read(malicious, extraction);
            Assert.fail("path traversal archive must be rejected");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), !expected.getMessage().isEmpty());
        }
        Assert.assertFalse(Files.exists(extraction.getParent().resolve("escaped.txt")));
    }

    private static void tamperEntry(Path source, Path target, String targetName) throws Exception {
        try (ZipFile zip = new ZipFile(source.toFile());
             ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry sourceEntry = entries.nextElement();
                ZipEntry copy = new ZipEntry(sourceEntry.getName());
                copy.setMethod(ZipEntry.STORED);
                byte[] content;
                try (java.io.InputStream input = zip.getInputStream(sourceEntry)) {
                    content = input.readAllBytes();
                }
                if (targetName.equals(sourceEntry.getName())) {
                    content[0] ^= 1;
                }
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(content);
                copy.setSize(content.length);
                copy.setCompressedSize(content.length);
                copy.setCrc(crc.getValue());
                copy.setTime(0L);
                output.putNextEntry(copy);
                output.write(content);
                output.closeEntry();
            }
        }
    }

    @Test
    public void validatesApplicationIdWithoutProfileRegistry() throws Exception {
        Fixture fixture = fixture();
        Path archive = temporaryFolder.newFolder("validate").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), archive);
        Path extraction = temporaryFolder.newFolder("validate-extract").toPath();
        try (ProfileArchiveBundle ignored = new ProfileArchiveReader().read(archive, extraction,
                "com.example.other")) {
            Assert.fail("applicationId mismatch must fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("applicationId"));
        }
    }

    @Test
    public void manifestCodecRejectsUnknownFieldsVersionsAndStatuses() throws Exception {
        Fixture fixture = fixture();
        Path archive = temporaryFolder.newFolder("manifest-strict").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(), archive);
        byte[] manifest;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            manifest = zip.getInputStream(zip.getEntry(ProfileArchiveReader.PROFILE_MANIFEST_ENTRY))
                    .readAllBytes();
        }
        JSONObject object = JSON.parseObject(manifest);
        // Fastjson drops null members while parsing; use a valid digest for strict codec input.
        object.put("reportsSha256", "a".repeat(64));
        object.put("unexpected", "value");
        try {
            ProfileArchiveManifestCodec.deserialize(JSON.toJSONBytes(object));
            Assert.fail("unknown manifest field must fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("未知字段"));
        }
        object.remove("unexpected");
        object.put("formatVersion", 99);
        try {
            ProfileArchiveManifestCodec.deserialize(JSON.toJSONBytes(object));
            Assert.fail("unsupported manifest version must fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("formatVersion"));
        }
        object.put("formatVersion", 1);
        object.put("status", "broken");
        try {
            ProfileArchiveManifestCodec.deserialize(JSON.toJSONBytes(object));
            Assert.fail("unsupported manifest status must fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("status"));
        }
    }

    @Test
    public void writerEnforcesCandidateAndVerifiedFilenameBoundaries() throws Exception {
        Fixture fixture = fixture();
        try {
            new ProfileArchiveWriter().packCandidate(fixture.bundle.getRoot(),
                    temporaryFolder.newFolder("wrong-candidate-name").toPath()
                            .resolve("profile.dptprofile"));
            Assert.fail("candidate output must use candidate suffix");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("candidate"));
        }
        try {
            new ProfileArchiveWriter().packVerified(fixture.bundle.getRoot(),
                    temporaryFolder.newFolder("wrong-verified-name").toPath()
                            .resolve("profile.candidate.dptprofile"),
                    temporaryFolder.newFolder("reports-empty").toPath());
            Assert.fail("verified output must not use candidate suffix");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("verified"));
        }
    }

    @Test
    public void verifiedArchiveCannotBypassPromotionGate() throws Exception {
        Fixture fixture = fixture();
        Path reports = temporaryFolder.newFolder("direct-verified-reports").toPath();
        Files.writeString(reports.resolve("gate-report.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(reports.resolve("scan-baseline.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(reports.resolve("scan-candidate.json"), "{}", StandardCharsets.UTF_8);

        try {
            new ProfileArchiveWriter().packVerified(fixture.bundle.getRoot(),
                    temporaryFolder.newFolder("direct-verified-output").toPath()
                            .resolve("profile.dptprofile"), reports);
            Assert.fail("verified archives must only be produced by promote");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("promote"));
        }
    }

    @Test
    public void rejectsGeneratorVersionMismatchBetweenManifestAndContract() throws Exception {
        Fixture fixture = fixture();
        Path source = copyBundle(fixture.bundle.getRoot(), "generator-mismatch-source");
        Path contractPath = source.resolve(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY);
        JSONObject contract = JSON.parseObject(Files.readAllBytes(contractPath));
        contract.put("generatorVersion", "profile-generator-other");
        byte[] contractBytes = JSON.toJSONBytes(contract);
        Files.write(contractPath, contractBytes);

        Path hashesPath = source.resolve(ProfileArchiveReader.ARTIFACT_HASHES_ENTRY);
        JSONObject hashes = JSON.parseObject(Files.readAllBytes(hashesPath));
        hashes.getJSONObject("sha256").put(ProfileArchiveReader.PROFILE_CONTRACT_ENTRY,
                MessageDigestUtils.sha256(contractBytes));
        Files.write(hashesPath, JSON.toJSONBytes(hashes));

        Path archive = temporaryFolder.newFolder("generator-mismatch-archive").toPath()
                .resolve("profile.candidate.dptprofile");
        new ProfileArchiveWriter().packCandidate(source, archive);
        try (ProfileArchiveBundle ignored = new ProfileArchiveReader().read(archive)) {
            Assert.fail("manifest and contract generator versions must match");
        } catch (Exception expected) {
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("generatorVersion"));
        }
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles-" + System.nanoTime()).toPath();
        Path activeRoot = temporaryFolder.newFolder("active-" + System.nanoTime()).toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileBundle bundle = ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID,
                revision, temporaryFolder.newFolder("artifacts-" + System.nanoTime()).toPath());
        // Keep the fixture explicit: archive reading must not rely on these state roots.
        Assert.assertEquals(created.getProfileId(),
                new ProfileRegistry(profilesRoot).findProfileId(APPLICATION_ID).orElse(null));
        return new Fixture(bundle, created.getProfileId());
    }

    private Path copyBundle(Path source, String name) throws Exception {
        Path target = temporaryFolder.newFolder(name).toPath();
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }

    private static final class Fixture {
        private final ProfileBundle bundle;
        private final String profileId;

        private Fixture(ProfileBundle bundle, String profileId) {
            this.bundle = bundle;
            this.profileId = profileId;
        }
    }
}
