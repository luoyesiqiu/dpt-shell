package com.luoyesiqiu.shell.util;

import android.content.Context;
import android.util.Log;

import com.luoyesiqiu.shell.JniBridge;

import java.io.File;

import dalvik.system.PathClassLoader;

/**
 * Created by luoyesiqiu
 */
public class ShellClassLoader extends PathClassLoader {

    private static final String TAG = "dpt";

    public ShellClassLoader(String dexPath, String librarySearchPath,ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) {
        Log.d(TAG, "try loadClass: name = [" + name + "], resolve = [" + resolve + "]");
        Class<?> clazz = findLoadedClass(name);
        if(clazz != null){
            Log.d(TAG, Thread.currentThread().getName() + "," + "loaded class = " + clazz);
            return clazz;
        }
        try {
            clazz = findClass(name);
            Log.d(TAG, Thread.currentThread().getName() + "," + "loadClass() find1 = " + clazz);

        }
        catch (Throwable e){
            try {
                clazz = super.loadClass(name, resolve);
                Log.d(TAG, Thread.currentThread().getName() + "," + "loadClass() find2 = " + clazz);
            }
            catch (Throwable e2){
                Log.w(TAG, Thread.currentThread().getName() + "," + "loadClass classLoader: " + super.toString(), e);
            }

        }

        return clazz;
    }

    public static ClassLoader loadDex(String dexPath){
        return new ShellClassLoader(dexPath ,null,ClassLoader.getSystemClassLoader());
    }

    public static ClassLoader loadDex(String apkPath,String dexPath){
        File nativePath64 = new File(apkPath.substring(0,apkPath.lastIndexOf("/")) + File.separator + "lib" + File.separator + "arm64");
        String nativePath = nativePath64.exists() ? nativePath64.getAbsolutePath() : nativePath64.getParent() + File.separator + "arm";

        Log.d(TAG, "loadDex() called with: sourcePath = [" + apkPath + "]");
        Log.d(TAG, "loadDex() called with: nativePath = [" + nativePath + "]");

        return new ShellClassLoader(dexPath ,nativePath,ClassLoader.getSystemClassLoader());
    }

    public static ClassLoader loadDex(Context context){
        return new ShellClassLoader(JniBridge.gdp(),context.getApplicationInfo().nativeLibraryDir,ClassLoader.getSystemClassLoader());
    }
}
