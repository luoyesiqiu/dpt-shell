package com.luoye.dpt.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.util.Locale;

public class ShellConfig {
    private static final ShellConfig INSTANCE = new ShellConfig();
    private String applicationName;
    private String appComponentFactoryName;

    @JSONField(name = "signature")
    private SignatureConfig signatureConfig;

    @JSONField(name = "shellPkgName")
    private String shellPackageName;

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
        this.shellPackageName = StringUtils.isBlank(shellConfig.getShellPackageName()) ? Const.DEFAULT_SHELL_PACKAGE_NAME : shellConfig.getShellPackageName();
        this.signatureConfig = shellConfig.getSignatureConfig();
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

    public String toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("app_name", getApplicationName());
        jsonObject.put("acf_name", getAppComponentFactoryName());
        String jniClassName = String.format(Locale.US, "%s/%s",
                getShellPackageName(),
                Const.KEY_JNI_BASE_CLASS_NAME
        );
        jsonObject.put("jni_cls_name", jniClassName);
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
