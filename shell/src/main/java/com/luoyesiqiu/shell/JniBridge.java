package com.luoyesiqiu.shell;

import android.content.Context;
import android.util.Log;

/**
 * Created by luoyesiqiu
 */
public class JniBridge {
    private static final String TAG = JniBridge.class.getSimpleName();

    public static native void craoc(String applicationClassName);
    public static native void craa(Context context, String applicationClassName);
    public static native void ia(Context context,ClassLoader classLoader);
    public static native String rcf(ClassLoader classLoader);
    public static native void mde(ClassLoader oldClassLoader,ClassLoader newClassLoader);
    public static native void rde(ClassLoader oldClassLoader,ClassLoader newClassLoader);
    public static native String gap(ClassLoader classLoader);

    static {
        try {
            System.loadLibrary("dpt");
        }
        catch (Throwable e){
            Log.w(TAG,e);
        }
    }

}
