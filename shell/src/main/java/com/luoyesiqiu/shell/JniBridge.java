package com.luoyesiqiu.shell;

import android.content.Context;
import android.util.Log;

import com.luoyesiqiu.shell.util.EnvUtils;

import java.io.File;

/**
 * Created by luoyesiqiu
 */
public class JniBridge {
    private static final String TAG = JniBridge.class.getSimpleName();
    public static native void craoc(String applicationClassName);
    public static native void craa(Context context, String applicationClassName);
    public static native void ia(Context context);
    public static native String rcf();
    public static native void mde(ClassLoader oldClassLoader,ClassLoader newClassLoader);
    public static native void rde(ClassLoader classLoader,String elementName);
    public static native String gap();
    public static native String gdp();
    public static native void ra(String originApplicationClassName);
    public static native String rapn();

    public static void loadShellLibs(String workspacePath,String apkPath) {
        final String[] allowLibNames = {Global.SHELL_SO_NAME};
        try {
            String abiDirName = EnvUtils.getAbiDirName(apkPath);
            File shellLibsFile = new File(workspacePath + File.separator + Global.LIB_DIR + File.separator + abiDirName);
            File[] files = shellLibsFile.listFiles();
            if(files != null) {
                for(File shellLibPath : files) {
                    String fullLibPath = shellLibPath.getAbsolutePath();
                    for(String libName : allowLibNames) {
                        String libSuffix = File.separator + libName;
                        if(fullLibPath.endsWith(libSuffix)) {
                            System.load(fullLibPath);
                        }
                    }
                }
            }
        }
        catch (Throwable e){
            Log.w(TAG,e);
        }
    }

}
