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
    public static native void rde(ClassLoader classLoader,String elementName);
    public static native String gap();
    public static native String gdp();
    public static native void ra(String originApplicationClassName);
    public static native String rapn(ClassLoader classLoader);

    static {
        try {
            System.loadLibrary("dpt");
        }
        catch (Throwable e){
            Log.w(TAG,e);
        }
    }

}
