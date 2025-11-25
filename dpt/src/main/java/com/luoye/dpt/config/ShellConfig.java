package com.luoye.dpt.config;

import org.json.JSONObject;

import java.util.Locale;

public class ShellConfig {
    private static final ShellConfig INSTANCE = new ShellConfig();
    private String applicationName;
    private String appComponentFactoryName;

    public String getShellPackageName() {
        return shellPackageName;
    }

    public void init(String shellPackageName) {
        this.shellPackageName = shellPackageName;
    }

    private String shellPackageName;


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

    public String toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("app_name", getApplicationName());
        jsonObject.put("acf_name", getAppComponentFactoryName());
        String jniClassName = String.format(Locale.US, "%s/%s", getShellPackageName(), Const.KEY_JNI_BASE_CLASS_NAME);
        jsonObject.put("jni_cls_name", jniClassName);
        return jsonObject.toString();
    }

    public static ShellConfig getInstance() {
        return INSTANCE;
    }
}
