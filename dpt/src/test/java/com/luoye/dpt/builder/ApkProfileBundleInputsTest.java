package com.luoye.dpt.builder;

import com.luoye.dpt.profile.ProfileTestArtifactFactory;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileActiveRevisionStore;
import com.luoye.dpt.profile.storage.ProfileBuildService;
import com.luoye.dpt.profile.storage.ProfileCreationResult;
import com.luoye.dpt.profile.storage.ProfileCreationService;
import com.luoye.dpt.profile.storage.ProfilePublishedBundleStore;
import com.luoye.dpt.profile.storage.ProfileRevisionStore;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class ApkProfileBundleInputsTest {
    private static final String APPLICATION_ID = "com.example.bundleinputs";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void apkReadsDexSoAndIdentifiersOnlyFromResolvedPublishedBundle() throws Exception {
        Fixture fixture = fixture();
        Path unpackedApk = temporaryFolder.newFolder("unpacked").toPath();
        writeManifest(unpackedApk, APPLICATION_ID);
        InspectableApk apk = new InspectableApk(builder(fixture));

        apk.resolveProfileInputs(unpackedApk);

        Assert.assertEquals(fixture.revisionRoot.resolve("shell-files/dex/classes.dex"),
                apk.proxyDexPath());
        Assert.assertEquals(fixture.revisionRoot.resolve("shell-files/libs"),
                apk.shellNativeLibsRoot());
        ProfileContract contract = apk.profileContract();
        Assert.assertEquals(fixture.profileId, contract.getProfileId());
        Assert.assertEquals(fixture.revisionId, contract.getRevisionId());

        byte[] dexBefore = Files.readAllBytes(apk.proxyDexPath());
        byte[] soBefore = Files.readAllBytes(fixture.revisionRoot.resolve("shell-files/libs/arm64")
                .resolve(contract.getSoName()));
        apk.copyNativeLibs(unpackedApk.toString());

        Path copiedSo = unpackedApk.resolve("assets").resolve(contract.getNativeLibAssetDirName())
                .resolve("arm64").resolve(contract.getSoName());
        Assert.assertTrue(Files.isRegularFile(copiedSo));
        Assert.assertArrayEquals(soBefore, Files.readAllBytes(copiedSo));
        Assert.assertArrayEquals(dexBefore, Files.readAllBytes(apk.proxyDexPath()));
        Assert.assertArrayEquals(soBefore, Files.readAllBytes(fixture.revisionRoot
                .resolve("shell-files/libs/arm64").resolve(contract.getSoName())));
    }

    @Test
    public void combinesGeneratedProfileDexWithoutLegacyPackageRename() throws Exception {
        Fixture fixture = fixture();
        Path unpackedApk = temporaryFolder.newFolder("combine-unpacked").toPath();
        Files.createDirectories(unpackedApk.resolve("assets"));
        writeManifest(unpackedApk, APPLICATION_ID);
        InspectableApk apk = new InspectableApk(builder(fixture));
        apk.resolveProfileInputs(unpackedApk);
        ProfileContract contract = apk.profileContract();
        byte[] compressedBusinessDex = new byte[] {1, 3, 5, 7, 9};
        Files.write(unpackedApk.resolve("assets").resolve(contract.getDexAssetName()),
                compressedBusinessDex);
        long shellDexSize = Files.size(apk.proxyDexPath());

        apk.combineDexZipWithShellDex(unpackedApk.toString());

        byte[] combined = Files.readAllBytes(unpackedApk.resolve("classes.dex"));
        Assert.assertEquals(shellDexSize + compressedBusinessDex.length + 4, combined.length);
        Assert.assertArrayEquals(compressedBusinessDex, Arrays.copyOfRange(combined,
                (int) shellDexSize, (int) shellDexSize + compressedBusinessDex.length));
        Assert.assertArrayEquals(new byte[] {0, 0, 0, (byte) compressedBusinessDex.length},
                Arrays.copyOfRange(combined, combined.length - 4, combined.length));
    }

    private Fixture fixture() throws Exception {
        Path profilesRoot = temporaryFolder.newFolder("profiles").toPath();
        Path activeProfilesRoot = temporaryFolder.newFolder("active").toPath();
        ProfileCreationResult created = new ProfileCreationService(profilesRoot).create(APPLICATION_ID);
        ProfileRevisionManifest revision = new ProfileBuildService(profilesRoot, activeProfilesRoot)
                .build(APPLICATION_ID, null, "recipe-a", null);
        ProfileTestArtifactFactory.assemble(profilesRoot, APPLICATION_ID, revision,
                temporaryFolder.newFolder("artifacts").toPath());
        new ProfilePublishedBundleStore(profilesRoot, activeProfilesRoot).publishCandidate(
                APPLICATION_ID, created.getProfileId(), revision.getRevisionId());
        ProfileRevisionStore revisions = new ProfileRevisionStore(profilesRoot);
        revisions.markVerified(created.getProfileId(), revision.getRevisionId());
        new ProfileActiveRevisionStore(profilesRoot, activeProfilesRoot, revisions).activate(
                created.getProfileId(), revision.getRevisionId());
        Path revisionRoot = activeProfilesRoot.resolve(created.getProfileId())
                .resolve("revisions").resolve(revision.getRevisionId());
        return new Fixture(profilesRoot, activeProfilesRoot, created.getProfileId(),
                revision.getRevisionId(), revisionRoot);
    }

    private static Apk.Builder builder(Fixture fixture) {
        Apk.Builder builder = new Apk.Builder();
        builder.profileRoots(fixture.profilesRoot, fixture.activeProfilesRoot);
        return builder;
    }

    private static void writeManifest(Path root, String applicationId) throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.ns("android", "http://schemas.android.com/apk/res/android", 1);
        NodeVisitor manifest = writer.child(null, "manifest");
        manifest.attr(null, "package", -1, NodeVisitor.TYPE_STRING, applicationId);
        NodeVisitor application = manifest.child(null, "application");
        application.end();
        manifest.end();
        writer.end();
        Files.write(root.resolve("AndroidManifest.xml"), writer.toByteArray());
    }

    private static final class InspectableApk extends Apk {
        private InspectableApk(Builder builder) {
            super(builder);
        }

        private Path proxyDexPath() {
            return Path.of(getProxyDexPath());
        }

        private Path shellNativeLibsRoot() {
            return getShellNativeLibsRoot().toPath();
        }

        private ProfileContract profileContract() {
            return getProfileContract();
        }

        @Override
        protected String getRenameDexPath() {
            throw new AssertionError("Profile APK must not enter legacy shell package renaming");
        }
    }

    private static final class Fixture {
        private final Path profilesRoot;
        private final Path activeProfilesRoot;
        private final String profileId;
        private final String revisionId;
        private final Path revisionRoot;

        private Fixture(Path profilesRoot, Path activeProfilesRoot, String profileId,
                        String revisionId, Path revisionRoot) {
            this.profilesRoot = profilesRoot;
            this.activeProfilesRoot = activeProfilesRoot;
            this.profileId = profileId;
            this.revisionId = revisionId;
            this.revisionRoot = revisionRoot;
        }
    }
}
