package com.luoye.dpt.profile;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ActiveRevisionPointer;
import com.luoye.dpt.profile.model.ArtifactHashManifest;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileGateReport;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.model.ProfileSourceConfig;
import com.luoye.dpt.profile.model.RevisionStatus;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileModelsTest {

    @Test
    public void sourceConfigRoundTripsWithSchemaVersion() {
        ProfileSourceConfig source = new ProfileSourceConfig();
        source.setApplicationId("com.example.alpha");
        source.setRecipeId("recipe-a");
        source.setOverlayDir("overlay");
        source.setAbis(Arrays.asList("arm64-v8a"));

        ProfileSourceConfig parsed = JSON.parseObject(JSON.toJSONString(source), ProfileSourceConfig.class);

        Assert.assertEquals(ProfileSchemaVersions.SOURCE_CONFIG, parsed.getSchemaVersion());
        Assert.assertEquals("com.example.alpha", parsed.getApplicationId());
        Assert.assertEquals("recipe-a", parsed.getRecipeId());
        Assert.assertEquals(Arrays.asList("arm64-v8a"), parsed.getAbis());
    }

    @Test
    public void identityLockCarriesStableCrossLayerIdentifiers() {
        ProfileIdentityLock identity = new ProfileIdentityLock();
        identity.setProfileId("pf_0123456789abcdef");
        identity.setApplicationId("com.example.alpha");
        identity.setIdentitySeed("00112233445566778899aabbccddeeff");
        identity.setShellPackageName("p.a1b2c3d4");
        identity.setProxyApplicationClassName("p.a1b2c3d4.Abc");
        identity.setProxyComponentFactoryClassName("p.a1b2c3d4.Def");
        identity.setJniBridgeClassName("p.a1b2c3d4.Ghi");
        identity.setSoName("libj1k2l3m4.so");
        identity.setShellConfigAssetName("n1o2p3q4");
        identity.setDexAssetName("r1s2t3u4.zip");
        identity.setCodeItemAssetName("v1w2x3y4");
        identity.setNativeLibAssetDirName("z1a2b3c4");
        identity.setRuntimeDirName("d1e2f3g4");
        identity.setJunkClassName("p.a1b2c3d4.Hij");
        identity.setRc4KeySymbol("k1l2m3n4");
        identity.setBuildKey("0123456789abcdef");
        identity.setJniMethods(Arrays.asList(new JniMethodMapping("INIT_APP", "o1p2q3", "()V")));

        ProfileIdentityLock parsed = JSON.parseObject(JSON.toJSONString(identity), ProfileIdentityLock.class);

        Assert.assertEquals(ProfileSchemaVersions.IDENTITY_LOCK, parsed.getSchemaVersion());
        Assert.assertEquals(identity.getProfileId(), parsed.getProfileId());
        Assert.assertEquals(identity.getJniBridgeClassName(), parsed.getJniBridgeClassName());
        Assert.assertEquals("INIT_APP", parsed.getJniMethods().get(0).getSemanticId());
        Assert.assertEquals("()V", parsed.getJniMethods().get(0).getJvmSignature());
    }

    @Test
    public void revisionAndActivePointerRoundTripIndependently() {
        ProfileRevisionManifest revision = new ProfileRevisionManifest();
        revision.setProfileId("pf_0123456789abcdef");
        revision.setApplicationId("com.example.alpha");
        revision.setRevisionId("r000001");
        revision.setStatus(RevisionStatus.CANDIDATE);
        revision.setRecipeId("recipe-a");
        revision.setOverlaySha256("a".repeat(64));
        revision.setGeneratorVersion("1");
        revision.setCoreVersion("2.20.0");
        revision.setToolchainVersion("agp-8.10.0");
        revision.setGeneratedSourceSha256("b".repeat(64));
        revision.setCreatedAtEpochMillis(123456789L);

        ProfileRevisionManifest parsedRevision = JSON.parseObject(
                JSON.toJSONString(revision), ProfileRevisionManifest.class);
        Assert.assertEquals(ProfileSchemaVersions.REVISION_MANIFEST, parsedRevision.getSchemaVersion());
        Assert.assertEquals(RevisionStatus.CANDIDATE, parsedRevision.getStatus());

        ActiveRevisionPointer pointer = new ActiveRevisionPointer();
        pointer.setProfileId(revision.getProfileId());
        pointer.setRevisionId(revision.getRevisionId());
        pointer.setProfileManifestSha256("c".repeat(64));

        ActiveRevisionPointer parsedPointer = JSON.parseObject(
                JSON.toJSONString(pointer), ActiveRevisionPointer.class);
        Assert.assertEquals(ProfileSchemaVersions.ACTIVE_POINTER, parsedPointer.getSchemaVersion());
        Assert.assertEquals("r000001", parsedPointer.getRevisionId());
    }

    @Test
    public void publishedContractHashesAndGateReportCarryVersionedMetadata() {
        ProfileContract contract = new ProfileContract();
        contract.setProfileId("pf_0123456789abcdef");
        contract.setRevisionId("r000001");
        contract.setApplicationId("com.example.alpha");
        contract.setProtocolVersion(1);
        contract.setGeneratorVersion("1");
        contract.setAbis(Arrays.asList("arm64-v8a"));
        contract.setProxyApplicationClassName("p.a1b2c3d4.Abc");
        contract.setProxyComponentFactoryClassName("p.a1b2c3d4.Def");
        contract.setJniBridgeClassName("p.a1b2c3d4.Ghi");
        contract.setJniMethods(Arrays.asList(new JniMethodMapping("INIT_APP", "o1p2q3", "()V")));
        contract.setSoName("libj1k2l3m4.so");
        contract.setShellConfigAssetName("n1o2p3q4");
        contract.setDexAssetName("r1s2t3u4.zip");
        contract.setCodeItemAssetName("v1w2x3y4");
        contract.setNativeLibAssetDirName("z1a2b3c4");
        contract.setRuntimeDirName("d1e2f3g4");
        contract.setJunkClassName("p.a1b2c3d4.Hij");
        contract.setRc4KeySymbol("k1l2m3n4");
        contract.setBuildKey("0123456789abcdef");

        ProfileContract parsedContract = JSON.parseObject(
                JSON.toJSONString(contract), ProfileContract.class);
        Assert.assertEquals(ProfileSchemaVersions.PROFILE_CONTRACT, parsedContract.getSchemaVersion());
        Assert.assertEquals(Arrays.asList("arm64-v8a"), parsedContract.getAbis());

        ArtifactHashManifest hashes = new ArtifactHashManifest();
        hashes.setProfileId(contract.getProfileId());
        hashes.setRevisionId(contract.getRevisionId());
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("shell-files/dex/classes.dex", "d".repeat(64));
        hashes.setSha256(entries);
        ArtifactHashManifest parsedHashes = JSON.parseObject(
                JSON.toJSONString(hashes), ArtifactHashManifest.class);
        Assert.assertEquals(ProfileSchemaVersions.ARTIFACT_HASHES, parsedHashes.getSchemaVersion());
        Assert.assertEquals("d".repeat(64), parsedHashes.getSha256().get("shell-files/dex/classes.dex"));

        ProfileGateReport report = new ProfileGateReport();
        report.setProfileId(contract.getProfileId());
        report.setRevisionId(contract.getRevisionId());
        report.setCandidateApkSha256("e".repeat(64));
        report.setOverallStatus(ProfileGateReport.Status.PENDING);
        report.setGeneratedAtEpochMillis(123456789L);
        report.setResults(new LinkedHashMap<>());
        ProfileGateReport parsedReport = JSON.parseObject(
                JSON.toJSONString(report), ProfileGateReport.class);
        Assert.assertEquals(ProfileSchemaVersions.GATE_REPORT, parsedReport.getSchemaVersion());
        Assert.assertEquals(ProfileGateReport.Status.PENDING, parsedReport.getOverallStatus());
    }
}
