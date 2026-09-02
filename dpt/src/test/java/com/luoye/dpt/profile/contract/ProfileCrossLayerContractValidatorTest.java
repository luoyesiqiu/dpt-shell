package com.luoye.dpt.profile.contract;

import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.validation.ProfileValidationException;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProfileCrossLayerContractValidatorTest {

    @Test
    public void acceptsArtifactsThatMatchTheProfileContract() {
        ProfileContract contract = profileContract();

        ProfileCrossLayerContractValidator.validate(contract, matchingArtifacts(contract));
    }

    @Test
    public void rejectsJavaNativeDeclarationWithMismatchedSignature() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        List<ProfileCrossLayerContractValidator.JniDeclaration> declarations =
                new ArrayList<>(artifacts.getJavaNativeMethods());
        String initAppMethodName = ProfileJniSemantic.INIT_APP.requireMethodName(
                contract.getJniMethods());
        for (int index = 0; index < declarations.size(); index++) {
            if (initAppMethodName.equals(declarations.get(index).getMethodName())) {
                declarations.set(index, new ProfileCrossLayerContractValidator.JniDeclaration(
                        initAppMethodName, "(Ljava/lang/String;)V"));
                break;
            }
        }
        artifacts.setJavaNativeMethods(declarations);

        ProfileValidationException exception = assertInvalid("javaNativeMethods",
                () -> ProfileCrossLayerContractValidator.validate(contract, artifacts));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(
                "semanticId=INIT_APP"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("expected=()V"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(
                "actual=(Ljava/lang/String;)V"));
    }

    @Test
    public void rejectsDuplicateJavaNativeDeclarationWhenTheFirstSignatureIsNull() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        List<ProfileCrossLayerContractValidator.JniDeclaration> declarations =
                new ArrayList<>(artifacts.getJavaNativeMethods());
        declarations.add(0, new ProfileCrossLayerContractValidator.JniDeclaration("m0", null));
        artifacts.setJavaNativeMethods(declarations);

        assertInvalid("javaNativeMethods", () -> ProfileCrossLayerContractValidator.validate(
                contract, artifacts));
    }

    @Test
    public void rejectsMissingNativeRegistration() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        List<ProfileCrossLayerContractValidator.JniDeclaration> declarations =
                new ArrayList<>(artifacts.getNativeRegisteredMethods());
        declarations.remove(0);
        artifacts.setNativeRegisteredMethods(declarations);

        assertInvalid("nativeRegisteredMethods", () -> ProfileCrossLayerContractValidator.validate(
                contract, artifacts));
    }

    @Test
    public void rejectsUnexpectedNativeRegistration() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        List<ProfileCrossLayerContractValidator.JniDeclaration> declarations =
                new ArrayList<>(artifacts.getNativeRegisteredMethods());
        declarations.add(new ProfileCrossLayerContractValidator.JniDeclaration("mExtra", "()V"));
        artifacts.setNativeRegisteredMethods(declarations);

        assertInvalid("nativeRegisteredMethods", () -> ProfileCrossLayerContractValidator.validate(
                contract, artifacts));
    }

    @Test
    public void rejectsManifestEntrypointMissingFromShellDex() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        artifacts.setShellDexClassNames(Collections.singleton(contract.getProxyApplicationClassName()));

        assertInvalid("manifest.proxyComponentFactoryClassName",
                () -> ProfileCrossLayerContractValidator.validate(contract, artifacts));
    }

    @Test
    public void rejectsBuildConstantMismatch() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        artifacts.setBuildKey("fedcba9876543210");

        assertInvalid("buildConstants.buildKey",
                () -> ProfileCrossLayerContractValidator.validate(contract, artifacts));
    }

    @Test
    public void rejectsJunkClassBuildConstantMismatch() {
        ProfileContract contract = profileContract();
        ProfileCrossLayerContractValidator.Artifacts artifacts = matchingArtifacts(contract);
        artifacts.setJunkClassName("p.a1b2c3d4.OtherJunk");

        assertInvalid("buildConstants.junkClassName",
                () -> ProfileCrossLayerContractValidator.validate(contract, artifacts));
    }

    private static ProfileCrossLayerContractValidator.Artifacts matchingArtifacts(
            ProfileContract contract) {
        List<ProfileCrossLayerContractValidator.JniDeclaration> declarations = new ArrayList<>();
        for (JniMethodMapping mapping : contract.getJniMethods()) {
            declarations.add(new ProfileCrossLayerContractValidator.JniDeclaration(
                    mapping.getMethodName(), mapping.getJvmSignature()));
        }

        Set<String> shellDexClassNames = new LinkedHashSet<>();
        shellDexClassNames.add(contract.getProxyApplicationClassName());
        shellDexClassNames.add(contract.getProxyComponentFactoryClassName());

        return new ProfileCrossLayerContractValidator.Artifacts()
                .setPackagerJniClassNameSig("Lp/a1b2c3d4/Ghi;")
                .setPackagerStaticInitializeMethodName("m9")
                .setJavaJniBridgeClassName(contract.getJniBridgeClassName())
                .setJavaNativeMethods(declarations)
                .setNativeJniBridgeClassName(contract.getJniBridgeClassName())
                .setNativeRegisteredMethods(declarations)
                .setManifestApplicationClassName(contract.getProxyApplicationClassName())
                .setManifestComponentFactoryClassName(contract.getProxyComponentFactoryClassName())
                .setShellDexClassNames(shellDexClassNames)
                .setSoName(contract.getSoName())
                .setShellConfigAssetName(contract.getShellConfigAssetName())
                .setDexAssetName(contract.getDexAssetName())
                .setCodeItemAssetName(contract.getCodeItemAssetName())
                .setNativeLibAssetDirName(contract.getNativeLibAssetDirName())
                .setRuntimeDirName(contract.getRuntimeDirName())
                .setJunkClassName(contract.getJunkClassName())
                .setRc4KeySymbol(contract.getRc4KeySymbol())
                .setBuildKey(contract.getBuildKey());
    }

    private static ProfileContract profileContract() {
        ProfileContract value = new ProfileContract();
        value.setSchemaVersion(ProfileSchemaVersions.PROFILE_CONTRACT);
        value.setProtocolVersion(ProfileSchemaVersions.PROFILE_PROTOCOL);
        value.setGeneratorVersion("generator-1");
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setApplicationId("com.example.alpha");
        value.setAbis(Collections.singletonList("arm64-v8a"));
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
        Collections.rotate(mappings, 1);
        return mappings;
    }

    private static ProfileValidationException assertInvalid(String field, Runnable action) {
        try {
            action.run();
            Assert.fail("Expected ProfileValidationException for " + field);
            throw new AssertionError("unreachable");
        } catch (ProfileValidationException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(field));
            return expected;
        }
    }
}
