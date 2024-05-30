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
    private void replaceApplication(){
        if (Global.sNeedCalledApplication && !TextUtils.isEmpty(realApplicationName)) {
            realApplication = (Application) JniBridge.ra(realApplicationName);
            Log.d(TAG, "applicationExchange: " + realApplicationName+"  realApplication="+realApplication.getClass().getName());

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
//        Log.d(TAG, "onCreate() classLoader = " + getApplicationContext().getClassLoader());

//        String realApplicationName = FileUtils.readAppName(getApplicationContext());
//
//        if (Global.sNeedCalledApplication && !TextUtils.isEmpty(realApplicationName)) {
//            Log.d(TAG, "onCreate: " + realApplicationName);
//            JniBridge.ra(realApplicationName);
//            JniBridge.craa(getApplicationContext(), realApplicationName);
//            JniBridge.craoc(realApplicationName);
//            Global.sNeedCalledApplication = false;
//        }
    }
    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        Log.d(TAG, "createPackageContext: " + realApplicationName);
        if(TextUtils.isEmpty(realApplicationName)){
            // 如果 AndroidManifest.xml 中配置的 Application 全类名为空
            // 说明没有进行 dex 加密操作 , 返回父类方法执行即可
            return super.createPackageContext(packageName, flags);
        }else{
            // 只有在创建 ContentProvider 时才调用到该 createPackageContext 方法 ,
            // 如果没有调用到该方法 , 说明该应用中没有配置 ContentProvider ;
            // 该方法不一定会调用到
            // 先进行 Application 替换
            replaceApplication();
            // Application 替换完成之后 , 再继续向下执行创建 ContentProvider
            return realApplication;
        }
    }

    @Override
    public String getPackageName() {
        if(TextUtils.isEmpty(realApplicationName)){
            // 如果 AndroidManifest.xml 中配置的 Application 全类名为空
            // 那么 不做任何操作
        }else{
            // 如果 AndroidManifest.xml 中配置的 Application 全类名不为空
            // 为了使 ActivityThread 的 installProvider 方法
            // 无法命中如下两个分支
            // 分支一 : context.getPackageName().equals(ai.packageName)
            // 分支二 : mInitialApplication.getPackageName().equals(ai.packageName)
            // 设置该方法返回值为空 , 上述两个分支就无法命中
            return "";
        }

        return super.getPackageName();
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG,"dpt attachBaseContext");
        Log.d(TAG,"attachBaseContext classloader = " + base.getClassLoader());
        realApplicationName = FileUtils.readAppName(this);
        if(!Global.sIsReplacedClassLoader) {
            ApplicationInfo applicationInfo = base.getApplicationInfo();
            if(applicationInfo == null) {
                throw new NullPointerException("application info is null");
            }
            FileUtils.unzipLibs(applicationInfo.sourceDir,applicationInfo.dataDir);
            JniBridge.loadShellLibs(applicationInfo.dataDir,applicationInfo.sourceDir);
            Log.d(TAG,"ProxyApplication init");
            JniBridge.ia();
            ClassLoader targetClassLoader = base.getClassLoader();
            JniBridge.mde(targetClassLoader);
            Global.sIsReplacedClassLoader = true;
        }
    }

}
