package com.luoyesiqiu.shell;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import com.luoyesiqiu.shell.util.FileUtils;

/**
 * Created by luoyesiqiu
 */
public class ProxyApplication extends Application {
    private static final String TAG = ProxyApplication.class.getSimpleName();
    private String realApplicationName = "";
    private Application realApplication = null;

    private void replaceApplication() {
        if (Global.sNeedCalledApplication && !TextUtils.isEmpty(realApplicationName)) {
            realApplication = (Application) JniBridge.ra(realApplicationName);
            Log.d(TAG, "applicationExchange: " + realApplicationName + ", realApplication: " + realApplication.getClass().getName());

            JniBridge.craa(getApplicationContext(), realApplicationName);
            JniBridge.craoc(realApplicationName);
            Global.sNeedCalledApplication = false;
        }
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "dpt onCreate");
        replaceApplication();
    }
    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        Log.d(TAG, "createPackageContext: " + realApplicationName);
        if(!TextUtils.isEmpty(realApplicationName)){
            replaceApplication();
            return realApplication;
        }
        return super.createPackageContext(packageName, flags);
    }

    @Override
    public String getPackageName() {
        if(!TextUtils.isEmpty(realApplicationName)){
            return "";
        }
        return super.getPackageName();
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG,"dpt attachBaseContext classloader = " + base.getClassLoader());
        realApplicationName = FileUtils.readAppName(this);
        if(!Global.sIsReplacedClassLoader) {
            ApplicationInfo applicationInfo = base.getApplicationInfo();
            if(applicationInfo == null) {
                throw new NullPointerException("application info is null");
            }
            FileUtils.unzipLibs(applicationInfo.sourceDir,applicationInfo.dataDir);
            JniBridge.loadShellLibs(applicationInfo.dataDir);
            Log.d(TAG,"ProxyApplication init");
            JniBridge.ia();
            ClassLoader targetClassLoader = base.getClassLoader();
            JniBridge.cbde(targetClassLoader);
            Global.sIsReplacedClassLoader = true;
        }
    }

}
