package com.luoyesiqiu.shell;

import androidx.annotation.Keep;

import com.luoye.dpt.BuildConfig;

/**
 * @author luoyesiqiu
 */
public class Global {
    public static final String APACHE_HTTP_LIB = "/system/framework/org.apache.http.legacy.jar";
    public static final String ZIP_LIB_DIR = "vwwwwwvwww";
    public static final String LIB_DIR = "dpt-libs";
    public static final String SHELL_SO_NAME = BuildConfig.SO_NAME;
    @Keep
    public volatile static boolean sIsReplacedClassLoader = false;
    @Keep
    public volatile static boolean sNeedCalledApplication = true;

}
