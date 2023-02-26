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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "dpt onCreate");

        Log.d(TAG, "onCreate() classLoader = " + getApplicationContext().getClassLoader());

        String realApplicationName = FileUtils.readAppName(getApplicationContext());

        if (Global.sNeedCalledApplication && !TextUtils.isEmpty(realApplicationName)) {
            Log.d(TAG, "onCreate: " + realApplicationName);
            JniBridge.craa(getApplicationContext(), realApplicationName);
            JniBridge.craoc(realApplicationName);
            Global.sNeedCalledApplication = false;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG,"dpt attachBaseContext");

        Log.d(TAG,"attachBaseContext classloader = " + base.getClassLoader());

        if(!Global.sIsReplacedClassLoader) {

            Log.d(TAG,"ProxyApplication init");
            JniBridge.ia(base,base.getClassLoader());

            ClassLoader oldClassLoader = base.getClassLoader();

            ClassLoader shellClassLoader = ShellClassLoader.loadDex(base);

            JniBridge.mde(oldClassLoader,shellClassLoader);
            Global.sIsReplacedClassLoader = true;
        }
    }

}
