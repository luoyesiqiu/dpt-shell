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

    private Application getOrAttachRealApplication() {
        if (realApplication == null) {
            realApplication = (Application) JniBridge.ra(realApplicationName);
            if (realApplication == null) {
                throw new IllegalStateException("Could not attach " + realApplicationName);
            }
        }
        return realApplication;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "dpt onCreate");
        if (Global.sNeedCalledApplication && !TextUtils.isEmpty(realApplicationName)) {
            Application application = getOrAttachRealApplication();
            Global.sNeedCalledApplication = false;
            application.onCreate();
        }
    }
    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        Log.d(TAG, "createPackageContext: " + realApplicationName);
        if(!TextUtils.isEmpty(realApplicationName)){
            // Providers need the application context before Android dispatches onCreate().
            return getOrAttachRealApplication();
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
        if(!Global.sIsReplacedClassLoader) {
            ApplicationInfo applicationInfo = base.getApplicationInfo();
            if(applicationInfo == null) {
                throw new NullPointerException("application info is null");
            }
            FileUtils.unzipLibs(applicationInfo.sourceDir, applicationInfo.dataDir);
            JniBridge.loadShellLibs(applicationInfo.dataDir);
            Log.d(TAG,"ProxyApplication init");
            JniBridge.ia();
            ClassLoader targetClassLoader = base.getClassLoader();
            JniBridge.cbde(targetClassLoader);
            Global.sIsReplacedClassLoader = true;
        }

        realApplicationName = JniBridge.rapn();
    }

}
