package com.luoyesiqiu.shell;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.luoyesiqiu.shell.util.FileUtils;
import com.luoyesiqiu.shell.util.ShellClassLoader;

/**
 * Created by luoyesiqiu
 */
public class ProxyApplication extends Application {
    private static final String TAG = ProxyApplication.class.getSimpleName();

    public static boolean initialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "dpt onCreate");

        Log.d(TAG, "onCreate() classLoader = " + getApplicationContext().getClassLoader());

        String realApplicationName = FileUtils.readAppName(getApplicationContext());


        if (!TextUtils.isEmpty(realApplicationName)) {
            JniBridge.craa(getApplicationContext(), realApplicationName);
        }


        if (!TextUtils.isEmpty(realApplicationName)) {
            JniBridge.craoc(realApplicationName);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG,"dpt attachBaseContext");

        Log.d(TAG,"attachBaseContext classloader = " + base.getClassLoader());


        if(!initialized) {
            Log.d(TAG,"ProxyApplication init");
            JniBridge.ia(base,base.getClassLoader());

            ClassLoader oldClassLoader = base.getClassLoader();

            ClassLoader shellClassLoader = ShellClassLoader.loadDex(base);

            JniBridge.mde(oldClassLoader,shellClassLoader);

        }
    }

}
