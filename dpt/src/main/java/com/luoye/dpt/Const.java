package com.luoye.dpt;

/**
 * @author luoyesiqiu
 */
public class Const {
    public static final String OPTION_OPEN_NOISY_LOG_LONG = "noisy-log";
    public static final String OPTION_OPEN_NOISY_LOG = "l";
    public static final String OPTION_NO_SIGN_APK_LONG = "no-sign";
    public static final String OPTION_NO_SIGN_APK = "s";
    public static final String OPTION_DUMP_CODE_LONG = "dump-code";
    public static final String OPTION_DUMP_CODE = "d";
    public static final String OPTION_APK_FILE = "f";
    public static final String OPTION_APK_FILE_LONG = "apk-file";
    public static final String STORE_PASSWORD = "android";
    public static final String KEY_PASSWORD = "android";
    public static final String KEY_ALIAS = "androiddebugkey";
    public static final String DEFAULT_THREAD_NAME = "dpt";

    public static final String ROOT_OF_OUT_DIR = System.getProperty("java.io.tmpdir");
    public static final String PROXY_APPLICATION_NAME = "com.luoyesiqiu.shell.ProxyApplication";
    public static final String PROXY_COMPONENT_FACTORY = "com.luoyesiqiu.shell.ProxyComponentFactory";
    public static final short MULTI_DEX_CODE_VERSION = 1;
}
