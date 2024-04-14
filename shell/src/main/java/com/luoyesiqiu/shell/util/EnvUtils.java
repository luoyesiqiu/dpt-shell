package com.luoyesiqiu.shell.util;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;

public class EnvUtils {
    private static final String TAG = "EnvUtils";
    public static String getAbiDirName(String apkPath) {
        String[] abiArray = {"arm", "arm64", "x86", "x86_64"};

        for (String abi : abiArray) {
            File nativeLibPath = new File(apkPath.substring(0,apkPath.lastIndexOf("/")) + File.separator + "lib" ,abi);
            if(nativeLibPath.exists()) {
                return abi;
            }
        }
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        for (String supportedAbi : supportedAbis) {
            if(supportedAbi.contains("x86_64")) {
                return "x86_64";
            }
            if(supportedAbi.contains("x86")) {
                return "x86";
            }
            else if(supportedAbi.contains("arm64-v8a")) {
                return "arm64";
            }
            else if(supportedAbi.contains("armeabi-v7a")) {
                return "arm";
            }
        }
        return null;
    }

    public static ApplicationInfo getApplicationInfo() {
        try {
            Class<?> ActivityThreadClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = ActivityThreadClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object ActivityThreadObj = sCurrentActivityThreadField.get(null);
            Field mBoundApplicationField = ActivityThreadClass.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            Object mBoundApplicationObj = mBoundApplicationField.get(ActivityThreadObj);
            Class<?>  AppBindDataClass = mBoundApplicationObj.getClass();
            Field appInfoField = AppBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            return (ApplicationInfo)appInfoField.get(mBoundApplicationObj);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
