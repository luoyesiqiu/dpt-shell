package com.luoyesiqiu.shell.util;

import android.content.pm.ApplicationInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EnvUtils {
    private static final String TAG = "EnvUtils";
    public static String getAbiDirName() {

        try {
            Class<?> clazz = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = clazz.getDeclaredMethod("getRuntime");
            Object runtime = getRuntime.invoke(null);
            Method vmInstructionSet = clazz.getDeclaredMethod("vmInstructionSet");
            return (String) vmInstructionSet.invoke(runtime);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "arm64";
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
