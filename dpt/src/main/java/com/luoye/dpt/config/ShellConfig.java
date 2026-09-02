package com.luoye.dpt.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.luoye.dpt.profile.contract.ProfileContractCodec;
import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.model.ProfileContract;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.util.Locale;

public class ShellConfig {
    private static final ShellConfig INSTANCE = new ShellConfig();
    private static final String LEGACY_STATIC_INITIALIZE_METHOD_NAME = "clinit";
    private String applicationName;
    private String appComponentFactoryName;

    @JSONField(name = "signature")
    private SignatureConfig signatureConfig;

    @JSONField(name = "shellPkgName")
    private String shellPackageName;

    @JSONField(name = "app_sign_sha256")
    private String appSignSha256;

    @JSONField(name = "dex_sign")
    private String dexSign;

    // Same 32-byte key as native read_shell_config aes_key. Not written to JSON.
    @JSONField(serialize = false)
    private byte[] insnsCryptKey;

    // One int, each bit is a switch (1 = disable). See Const.FLAG_DISABLE_*
    @JSONField(name = "risk_check_flags")
    private int riskCheckFlags;

    @JSONField(serialize = false, deserialize = false)
    private ProfileContract profileContract;

    private ShellConfig() {
    }

    public static class SignatureConfig {
        public String getKeystore() {
            return keystore;
        }

        public void setKeystore(String keystore) {
            this.keystore = keystore;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getStorePassword() {
            return storePassword;
        }

        public void setStorePassword(String storePassword) {
            this.storePassword = storePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }

        @JSONField(name = "keystore")
        private String keystore;
        @JSONField(name = "alias")
        private String alias;
        @JSONField(name = "storepass")
        private String storePassword;
        @JSONField(name = "keypass")
        private String keyPassword;

        public SignatureConfig() {
        }

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }

    public String getShellPackageName() {
        return shellPackageName;
    }

    public void setShellPackageName(String shellPackageName) {
        this.shellPackageName = shellPackageName;
    }

    public void init(String shellPackageName) {
        ShellConfig shellConfig = getInstance();
        shellConfig.setShellPackageName(shellPackageName);
        init(shellConfig);
    }

    public void init(ShellConfig shellConfig) {
        this.profileContract = null;
        this.shellPackageName = StringUtils.isBlank(shellConfig.getShellPackageName()) ? Const.DEFAULT_SHELL_PACKAGE_NAME : shellConfig.getShellPackageName();
        this.signatureConfig = shellConfig.getSignatureConfig();
        this.appSignSha256 = shellConfig.getAppSignSha256();
        this.riskCheckFlags = shellConfig.getRiskCheckFlags();
    }

    public String getSlashShellPackageName() {
        return getShellPackageName().replaceAll("\\.", "/");
    }

    public String getAppComponentFactoryName() {
        return appComponentFactoryName;
    }

    public void setAppComponentFactoryName(String appComponentFactoryName) {
        this.appComponentFactoryName = appComponentFactoryName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public SignatureConfig getSignatureConfig() {
        return signatureConfig;
    }

    public void setSignatureConfig(SignatureConfig signatureConfig) {
        this.signatureConfig = signatureConfig;
    }

    public String getAppSignSha256() {
        return appSignSha256;
    }

    public void setAppSignSha256(String appSignSha256) {
        this.appSignSha256 = appSignSha256;
    }


    public String getDexSign() {
        return dexSign;
    }

    public void setDexSign(String dexSign) {
        this.dexSign = dexSign;
    }

    public byte[] getInsnsCryptKey() {
        return insnsCryptKey;
    }

    public void setInsnsCryptKey(byte[] insnsCryptKey) {
        this.insnsCryptKey = insnsCryptKey;
    }

    public int getRiskCheckFlags() {
        return riskCheckFlags;
    }

    public void setRiskCheckFlags(int riskCheckFlags) {
        this.riskCheckFlags = riskCheckFlags;
    }

    public String getJniSlashClassName() {
        if (profileContract != null) {
            return profileContract.getJniBridgeClassName().replace('.', '/');
        }
        return String.format(Locale.US, "%s/%s",
                getSlashShellPackageName(),
                Const.KEY_JNI_BASE_CLASS_NAME
        );
    }

    public String getJniClassNameSig() {
        return String.format(Locale.US, "L%s;", getJniSlashClassName());
    }

    public String getJniStaticInitializeMethodName() {
        if (profileContract == null) {
            return LEGACY_STATIC_INITIALIZE_METHOD_NAME;
        }
        return ProfileJniSemantic.STATIC_INITIALIZE
                .requireMethodName(profileContract.getJniMethods());
    }

    public void applyProfileContract(ProfileContract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("profile contract must not be null");
        }
        profileContract = ProfileContractCodec.deserialize(ProfileContractCodec.serialize(contract));
        shellPackageName = packageNameOf(profileContract.getProxyApplicationClassName());
    }

    public String getProxyApplicationClassName() {
        return profileContract == null ? null : profileContract.getProxyApplicationClassName();
    }

    public String getProxyComponentFactoryClassName() {
        return profileContract == null ? null : profileContract.getProxyComponentFactoryClassName();
    }

    public String getNativeLibAssetDirName() {
        return profileContract == null ? Const.KEY_LIBS_DIR_NAME
                : profileContract.getNativeLibAssetDirName();
    }

    public String getRuntimeDirName() {
        return profileContract == null ? null : profileContract.getRuntimeDirName();
    }

    public String getRc4KeySymbol() {
        return profileContract == null ? Const.RC4_KEY_SYMBOL : profileContract.getRc4KeySymbol();
    }

    public String getBuildKey() {
        return profileContract == null ? null : profileContract.getBuildKey();
    }

    private static String packageNameOf(String className) {
        int separator = className.lastIndexOf('.');
        if (separator <= 0) {
            throw new IllegalArgumentException("profile proxy application class is invalid");
        }
        return className.substring(0, separator);
    }

    public String toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("app_name", getApplicationName());
        jsonObject.put("acf_name", getAppComponentFactoryName());
        String jniClassName = getJniSlashClassName();
        jsonObject.put("jni_cls_name", jniClassName);
        if (!StringUtils.isBlank(getAppSignSha256())) {
            jsonObject.put("app_sign_sha256", getAppSignSha256());
        }
        jsonObject.put("dex_sign", getDexSign());
        jsonObject.put("risk_check_flags", getRiskCheckFlags());
        return jsonObject.toString();
    }

    public static ShellConfig getInstance() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
