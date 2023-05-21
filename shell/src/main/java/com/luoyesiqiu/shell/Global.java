package com.luoyesiqiu.shell;

/**
 * @author luoyesiqiu
 */
public class Global {
    public static final String APACHE_HTTP_LIB = "/system/framework/org.apache.http.legacy.jar";
    public static final String FAKE_APK_NAME = "i11111i111";
    public volatile static boolean sIsReplacedClassLoader = false;
    public volatile static boolean sNeedCalledApplication = true;
    public volatile static boolean sLoadedDexes = false;

}
