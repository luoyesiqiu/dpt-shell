package com.luoyesiqiu.shell;

import android.content.Context;

/**
 * Created by luoyesiqiu
 */
public class JniBridge {
    public static native void craoc(String applicationClassName);
    public static native void craa(Context context, String applicationClassName);
    public static native void ia(Context context,ClassLoader classLoader);
    public static native String rcf(ClassLoader classLoader);
    public static native void mde(ClassLoader oldClassLoader,ClassLoader newClassLoader);
    public static native String gap(ClassLoader classLoader);

    static {
        System.loadLibrary("dpt");
    }

}
