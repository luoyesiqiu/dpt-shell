package com.luoyesiqiu.shell.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.net.URL;

import dalvik.system.PathClassLoader;

/**
 * Created by luoyesiqiu
 */
public class ShellClassLoader extends PathClassLoader {

    private static final String TAG = "dpt";

    public ShellClassLoader(String dexPath,ClassLoader classLoader) {
        super(dexPath,classLoader);
    }

    public ShellClassLoader(String dexPath, String librarySearchPath,ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) {
        Log.d(TAG, "try loadClass: name = [" + name + "], resolve = [" + resolve + "]");
        Class<?> clazz = null;//findLoadedClass(name);
        try {
            clazz = findClass(name);
            Log.d(TAG, "loadClass() find1 = " + clazz);

        }
        catch (Throwable e){
            try {
                clazz = super.loadClass(name, resolve);
                Log.d(TAG, "loadClass() find2 = " + clazz);
            }
            catch (Throwable e2){
                Log.w(TAG, "loadClass classLoader: " + super.toString(), e);
            }

        }

        return clazz;
    }


    @Override
    public String findLibrary(String name) {
        Log.d(TAG, "findLibrary() called with: name = [" + name + "]");
        return super.findLibrary(name);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Log.d(TAG, "finalize() called");
    }

    @Override
    protected URL findResource(String name) {
        Log.d(TAG, "findResource() called with: name = [" + name + "]");

        return super.findResource(name);
    }

    public static ClassLoader loadDex(String apkPath){
        String sourcePath = apkPath;
        String nativePath = apkPath.substring(0,apkPath.lastIndexOf("/")) + File.separator + "lib" + File.separator + (SystemUtils.is64Bits() ? "arm64":"arm");
        Log.d(TAG, "loadDex() called with: sourcePath = [" + sourcePath + "]");
        Log.d(TAG, "loadDex() called with: nativePath = [" + nativePath + "]");


        return new ShellClassLoader(sourcePath ,nativePath,ClassLoader.getSystemClassLoader());
    }

    public static ClassLoader loadDex(Context context){
        return new ShellClassLoader(context.getApplicationInfo().sourceDir ,context.getApplicationInfo().nativeLibraryDir,ClassLoader.getSystemClassLoader());
    }


}
