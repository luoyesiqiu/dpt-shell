package com.luoyesiqiu.shell;

/**
 * @author luoyesiqiu
 */
public class Global {
    public volatile static boolean sIsReplacedClassLoader = false;
    public volatile static boolean sNeedCalledApplication = true;
    public volatile static boolean sLoadedDexes = false;
}
