package com.luoye.dpt.builder;

import com.luoye.dpt.config.Const;
import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileContract;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileContractPackagingTest {

    @After
    public void resetGlobalShellConfig() {
        ShellConfig.getInstance().init(Const.SHELL_PACKAGE_NAME_AUTO);
    }

    @Test
    public void profileContractControlsShellConfigManifestAndPackagingIdentifiers() {
        ProfileContract contract = profileContract();
        InspectablePackage androidPackage = new InspectablePackage(contract);

        Assert.assertEquals(contract.getProxyApplicationClassName(),
                androidPackage.getProxyApplicationName());
        Assert.assertEquals(contract.getProxyComponentFactoryClassName(),
                androidPackage.getProxyComponentFactory());
        Assert.assertEquals(contract.getShellConfigAssetName(), androidPackage.shellConfigAssetName());
        Assert.assertEquals(contract.getDexAssetName(), androidPackage.dexAssetName());
        Assert.assertEquals(contract.getDexAssetName() + ".unaligned",
                androidPackage.unalignedDexAssetName());
        Assert.assertEquals(contract.getCodeItemAssetName(), androidPackage.codeItemAssetName());
        Assert.assertEquals(contract.getNativeLibAssetDirName(),
                androidPackage.nativeLibAssetDirName());
        Assert.assertEquals(contract.getRuntimeDirName(), androidPackage.runtimeDirName());
        Assert.assertEquals(contract.getRc4KeySymbol(), androidPackage.rc4KeySymbol());
        Assert.assertEquals(contract.getSoName(), androidPackage.shellSoName());
        Assert.assertEquals(contract.getBuildKey(), androidPackage.buildKey());

        ShellConfig config = ShellConfig.getInstance();
        config.init(Const.SHELL_PACKAGE_NAME_AUTO);
        config.applyProfileContract(contract);
        Assert.assertEquals("p.a1b2c3d4", config.getShellPackageName());
        Assert.assertEquals(contract.getProxyApplicationClassName(),
                config.getProxyApplicationClassName());
        Assert.assertEquals(contract.getProxyComponentFactoryClassName(),
                config.getProxyComponentFactoryClassName());
        Assert.assertEquals("Lp/a1b2c3d4/Ghi;", config.getJniClassNameSig());
        Assert.assertEquals("m9", config.getJniStaticInitializeMethodName());
        Assert.assertEquals(contract.getNativeLibAssetDirName(),
                config.getNativeLibAssetDirName());
        Assert.assertEquals(contract.getRuntimeDirName(), config.getRuntimeDirName());
        Assert.assertEquals(contract.getRc4KeySymbol(), config.getRc4KeySymbol());
        Assert.assertEquals(contract.getBuildKey(), config.getBuildKey());
    }

    @Test
    public void packageCopiesProfileContractBeforeTheCallerCanMutateIt() {
        ProfileContract contract = profileContract();
        InspectablePackage androidPackage = new InspectablePackage(contract);

        contract.setProxyApplicationClassName("p.a1b2c3d4.Mutated");
        contract.setBuildKey("fedcba9876543210");

        Assert.assertEquals("p.a1b2c3d4.Abc", androidPackage.getProxyApplicationName());
        Assert.assertEquals("0123456789abcdef", androidPackage.buildKey());
    }

    @Test
    public void staticInitializeMethodFallsBackToTheLegacyNameWithoutAProfile() {
        ShellConfig config = ShellConfig.getInstance();
        config.init(Const.SHELL_PACKAGE_NAME_AUTO);

        Assert.assertEquals("clinit", config.getJniStaticInitializeMethodName());
    }

    private static ProfileContract profileContract() {
        ProfileContract value = new ProfileContract();
        value.setSchemaVersion(ProfileSchemaVersions.PROFILE_CONTRACT);
        value.setProtocolVersion(ProfileSchemaVersions.PROFILE_PROTOCOL);
        value.setGeneratorVersion("generator-1");
        value.setProfileId("pf_0123456789abcdef");
        value.setRevisionId("r000001");
        value.setApplicationId("com.example.alpha");
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

    private static final class InspectablePackage extends AndroidPackage {
        private InspectablePackage(ProfileContract contract) {
            super(builder(contract));
        }

        private static InspectableBuilder builder(ProfileContract contract) {
            InspectableBuilder builder = new InspectableBuilder();
            builder.profileContract(contract);
            return builder;
        }

        @Override
        public void writeProxyAppName(String manifestDir) {
        }

        @Override
        public void writeProxyComponentFactoryName(String manifestDir) {
        }

        @Override
        public void setExtractNativeLibs(String manifestDir) {
        }

        @Override
        public void setDebuggable(String manifestDir, boolean debuggable) {
        }

        @Override
        protected File getOutAssetsDir(String packageDir) {
            return new File(packageDir);
        }

        @Override
        public String getLibDir(String packageDir) {
            return packageDir;
        }

        @Override
        public String getDexDir(String packageDir) {
            return packageDir;
        }

        @Override
        protected String getManifestFilePath(String packageOutDir) {
            return packageOutDir;
        }

        @Override
        public void saveApplicationName(String packageOutDir) {
        }

        @Override
        public void saveAppComponentFactory(String packageOutDir) {
        }

        @Override
        protected boolean sign(String packagePath, String keyStorePath, String signedPackagePath,
                               String keyAlias, String storePassword, String keyPassword) {
            return false;
        }

        private String shellConfigAssetName() {
            return getShellConfigAssetName();
        }

        private String dexAssetName() {
            return getDexAssetName();
        }

        private String unalignedDexAssetName() {
            return getUnalignedDexAssetName();
        }

        private String codeItemAssetName() {
            return getCodeItemAssetName();
        }

        private String nativeLibAssetDirName() {
            return getNativeLibAssetDirName();
        }

        private String runtimeDirName() {
            return getRuntimeDirName();
        }

        private String rc4KeySymbol() {
            return getRc4KeySymbol();
        }

        private String shellSoName() {
            return getShellSoName();
        }

        private String buildKey() {
            return getBuildKey();
        }
    }

    private static final class InspectableBuilder extends AndroidPackage.Builder {
        @Override
        public AndroidPackage build() {
            throw new UnsupportedOperationException("test builder is only used as constructor input");
        }
    }
}
