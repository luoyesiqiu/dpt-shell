package com.luoye.dpt.profile;

import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSourceConfig;
import com.luoye.dpt.profile.model.RevisionStatus;
import com.luoye.dpt.profile.validation.ProfileModelValidator;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileModelValidatorTest {

    @Test
    public void acceptsCompleteVersionOneDocuments() {
        ProfileModelValidator.validate(source());
        ProfileModelValidator.validate(identity());
        ProfileModelValidator.validate(revision());
        ProfileModelValidator.validate(activePointer());
        ProfileModelValidator.validate(contract());
        ProfileModelValidator.validate(artifactHashes());
        ProfileModelValidator.validate(gateReport());
    }

    @Test
    public void rejectsInvalidApplicationId() {
        ProfileSourceConfig source = source();
        source.setApplicationId("singleSegment");

        assertInvalid("applicationId", () -> ProfileModelValidator.validate(source));
    }

    @Test
    public void rejectsMalformedProfileAndRevisionIds() {
        ProfileIdentityLock identity = identity();
        identity.setProfileId("profile-alpha");
        assertInvalid("profileId", () -> ProfileModelValidator.validate(identity));

        ProfileRevisionManifest revision = revision();
        revision.setRevisionId("1");
        assertInvalid("revisionId", () -> ProfileModelValidator.validate(revision));
    }

    @Test
    public void requiresExactlyArm64Abi() {
        ProfileSourceConfig source = source();
        source.setAbis(Arrays.asList("arm64-v8a", "x86_64"));
        assertInvalid("abis", () -> ProfileModelValidator.validate(source));

        ProfileContract contract = contract();
        contract.setAbis(Arrays.asList("armeabi-v7a"));
        assertInvalid("abis", () -> ProfileModelValidator.validate(contract));
    }

    @Test
    public void rejectsIdentifiersOutsideProfileNamespace() {
        ProfileIdentityLock identity = identity();
        identity.setProxyApplicationClassName("other.namespace.Proxy");
        ProfileIdentityLock invalidNamespaceIdentity = identity;
        assertInvalid("proxyApplicationClassName",
                () -> ProfileModelValidator.validate(invalidNamespaceIdentity));

        identity = identity();
        identity.setSoName("../libescape.so");
        ProfileIdentityLock invalidSoIdentity = identity;
        assertInvalid("soName", () -> ProfileModelValidator.validate(invalidSoIdentity));
    }

    @Test
    public void rejectsDuplicateOrMalformedJniMappings() {
        ProfileIdentityLock identity = identity();
        identity.setJniMethods(Arrays.asList(
                new JniMethodMapping("INIT_APP", "abc", "()V"),
                new JniMethodMapping("INIT_APP", "def", "(Ljava/lang/String;)V")));
        ProfileIdentityLock duplicateJniIdentity = identity;
        assertInvalid("jniMethods", () -> ProfileModelValidator.validate(duplicateJniIdentity));

        identity = identity();
        identity.setJniMethods(Arrays.asList(new JniMethodMapping("INIT_APP", "abc", "(broken")));
        ProfileIdentityLock invalidSignatureIdentity = identity;
        assertInvalid("jvmSignature", () -> ProfileModelValidator.validate(invalidSignatureIdentity));
    }

    @Test
    public void rejectsUnsupportedSchemaAndUnsafeArtifactPath() {
        ProfileSourceConfig source = source();
        source.setSchemaVersion(99);
        assertInvalid("schemaVersion", () -> ProfileModelValidator.validate(source));

        ArtifactHashManifest hashes = artifactHashes();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../outside", "d".repeat(64));
        hashes.setSha256(entries);
        assertInvalid("sha256", () -> ProfileModelValidator.validate(hashes));
    }

    @Test
    public void rejectsMalformedSha256AndMissingRequiredField() {
        ProfileRevisionManifest revision = revision();
        revision.setOverlaySha256("short");
        assertInvalid("overlaySha256", () -> ProfileModelValidator.validate(revision));

        ProfileGateReport report = gateReport();
        report.setCandidateApkSha256(null);
        assertInvalid("candidateApkSha256", () -> ProfileModelValidator.validate(report));
    }

    private static ProfileSourceConfig source() {
        ProfileSourceConfig value = new ProfileSourceConfig();
        value.setApplicationId("com.example.alpha");
        value.setRecipeId("recipe-a");
        value.setOverlayDir("overlay");
        value.setAbis(Arrays.asList("arm64-v8a"));
        return value;
    }

    private static ProfileIdentityLock identity() {
        ProfileIdentityLock value = new ProfileIdentityLock();
        value.setProfileId("pf_0123456789abcdef");
        value.setApplicationId("com.example.alpha");
        value.setIdentitySeed("00112233445566778899aabbccddeeff");
        value.setShellPackageName("p.a1b2c3d4");
        value.setProxyApplicationClassName("p.a1b2c3d4.Abc");
        value.setProxyComponentFactoryClassName("p.a1b2c3d4.Def");
        value.setJniBridgeClassName("p.a1b2c3d4.Ghi");
        value.setSoName("libj1k2l3m4.so");
        value.setShellConfigAssetName("n1o2p3q4");
        value.setDexAssetName("r1s2t3u4.zip");
        value.setCodeItemAssetName("v1w2x3y4");
        value.setNativeLibAssetDirName("z1a2b3c4");
        value.setRuntimeDirName("d1e2f3g4");
        value.setJunkClassName("p.a1b2c3d4.Hij");
        value.setRc4KeySymbol("k1l2m3n4");
        value.setBuildKey("0123456789abcdef");
        value.setJniMethods(jniMappings());
        return value;
    }

    private static ProfileRevisionManifest revision() {
        ProfileRevisionManifest value = new ProfileRevisionManifest();
        value.setProfileId("pf_0123456789abcdef");
        value.setApplicationId("com.example.alpha");
        value.setRevisionId("r000001");
        value.setStatus(RevisionStatus.CANDIDATE);
        value.setRecipeId("recipe-a");
        value.setOverlaySha256("a".repeat(64));
        value.setGeneratorVersion("1");
        value.setCoreVersion("2.20.0");
        value.setToolchainVersion("agp-8.10.0");
        value.setGeneratedSourceSha256("b".repeat(64));
        value.setCreatedAtEpochMillis(123456789L);
        return value;
    }

    private static ActiveRevisionPointer activePointer() {
        ActiveRevisionPointer value = new ActiveRevisionPointer();
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setProfileManifestSha256("c".repeat(64));
        return value;
    }

    private static ProfileContract contract() {
        ProfileContract value = new ProfileContract();
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setApplicationId("com.example.alpha");
        value.setProtocolVersion(1);
        value.setGeneratorVersion("1");
        value.setAbis(Arrays.asList("arm64-v8a"));
        value.setProxyApplicationClassName("p.a1b2c3d4.Abc");
        value.setProxyComponentFactoryClassName("p.a1b2c3d4.Def");
        value.setJniBridgeClassName("p.a1b2c3d4.Ghi");
        value.setJniMethods(jniMappings());
        value.setSoName("libj1k2l3m4.so");
        value.setShellConfigAssetName("n1o2p3q4");
        value.setDexAssetName("r1s2t3u4.zip");
        value.setCodeItemAssetName("v1w2x3y4");
        value.setNativeLibAssetDirName("z1a2b3c4");
        value.setRuntimeDirName("d1e2f3g4");
        value.setJunkClassName("p.a1b2c3d4.Hij");
        value.setRc4KeySymbol("k1l2m3n4");
        value.setBuildKey("0123456789abcdef");
        return value;
    }

    private static List<JniMethodMapping> jniMappings() {
        List<JniMethodMapping> mappings = new ArrayList<>();
        int index = 0;
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            mappings.add(new JniMethodMapping(semantic.getSemanticId(), "m" + index++,
                    semantic.getJvmSignature()));
        }
        return mappings;
    }

    private static ArtifactHashManifest artifactHashes() {
        ArtifactHashManifest value = new ArtifactHashManifest();
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("shell-files/dex/classes.dex", "d".repeat(64));
        value.setSha256(entries);
        return value;
    }

    private static ProfileGateReport gateReport() {
        ProfileGateReport value = new ProfileGateReport();
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setCandidateApkSha256("e".repeat(64));
        value.setOverallStatus(ProfileGateReport.Status.PENDING);
        value.setGeneratedAtEpochMillis(123456789L);
        value.setResults(new LinkedHashMap<>());
        return value;
    }

    private static void assertInvalid(String field, Runnable action) {
        try {
            action.run();
            Assert.fail("Expected ProfileValidationException for " + field);
        } catch (ProfileValidationException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(field));
        }
    }
}
