 package com.luoyesiqiu.shell;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;

import com.luoyesiqiu.shell.util.ShellClassLoader;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.lang.reflect.Method;

@RequiresApi(api = 28)
public class ProxyComponentFactory extends AppComponentFactory {
    private static final String TAG = "dpt " + ProxyComponentFactory.class.getSimpleName();
    private static AppComponentFactory sAppComponentFactory;
    private ClassLoader newClassLoader;
    private ClassLoader shellClassLoader;

    private String getTargetClassName(ClassLoader classLoader){
        return JniBridge.rcf(classLoader);
    }

    /**
     * 使用App的ClassLoader来加载目标AppComponentFactory.
     * @param appClassLoader App的ClassLoader
     * @return 目标AppComponentFactory
     */
    private AppComponentFactory getTargetAppComponentFactory(ClassLoader appClassLoader){
        if(sAppComponentFactory == null){
            String targetClassName = getTargetClassName(appClassLoader);
            Log.d(TAG,"targetClassName = " + targetClassName);
            if(!TextUtils.isEmpty(targetClassName)) {
                try {
                    sAppComponentFactory = (AppComponentFactory) Class.forName(targetClassName,true,appClassLoader).newInstance();
                    return sAppComponentFactory;
                } catch (Exception e) {
                }
            }
        }

        return sAppComponentFactory;
    }

    private ClassLoader init(ClassLoader cl){
        if(!Global.sLoadedDexes){
            Global.sLoadedDexes = true;

            JniBridge.ia(null,cl);
            String apkPath = JniBridge.gap();
            String dexPath = JniBridge.gdp();
            Log.d(TAG, "init dexPath: " + dexPath + ",apkPath: " + apkPath);
            newClassLoader = ShellClassLoader.loadDex(apkPath,dexPath);
            Log.d(TAG,"ProxyComponentFactory init() shell classLoader = " + cl);
            Log.d(TAG,"ProxyComponentFactory init() app classLoader = " + newClassLoader);
            return newClassLoader;
        }
        Log.d(TAG,"ProxyComponentFactory init() tail shell classLoader = " + cl);
        Log.d(TAG,"ProxyComponentFactory init() tail app classLoader = " + newClassLoader);
        return newClassLoader;
    }

    @Override
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateActivity() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");

        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);
        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateActivity", ClassLoader.class, String.class, Intent.class);
                return (Activity) method.invoke(targetAppComponentFactory, cl, className, intent);

            } catch (Exception e) {
            }
        }
        return super.instantiateActivity(cl, className, intent);
    }

    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateApplication() called with: cl = [" + cl + "], className = [" + className + "]");
        ClassLoader appClassLoader = init(cl);

        AppComponentFactory targetAppComponentFactory = null;
        String applicationName = JniBridge.rapn(null);
        if(!Global.sIsReplacedClassLoader){
            JniBridge.mde(cl, appClassLoader);
            Global.sIsReplacedClassLoader = true;
            shellClassLoader = cl;
            targetAppComponentFactory = getTargetAppComponentFactory(cl);
        }
        else{
            targetAppComponentFactory = getTargetAppComponentFactory(appClassLoader);
        }

        ClassLoader apacheHttpLibLoader = ShellClassLoader.loadDex(Global.APACHE_HTTP_LIB);
        JniBridge.mde(cl, apacheHttpLibLoader);
        JniBridge.rde(cl, "base.apk");
        Global.sNeedCalledApplication = false;

        if(targetAppComponentFactory != null) {
            try {
                Method method = targetAppComponentFactory.getClass().getDeclaredMethod("instantiateApplication", ClassLoader.class, String.class);
                Log.d(TAG, "instantiateApplication target applicationName = " + applicationName);

                if(!TextUtils.isEmpty(applicationName)) {
                    Log.d(TAG, "instantiateApplication application name and AppComponentFactory specified");

                    return (Application) method.invoke(targetAppComponentFactory, cl, applicationName);
                }
                else{
                    Log.d(TAG, "instantiateApplication app does not specify application name");

                    return (Application) method.invoke(targetAppComponentFactory, shellClassLoader,className);
                }

            } catch (Exception e) {
                Log.e(TAG,"instantiateApplication",e);
            }
        }

        //AppComponentFactory no specified
        if(!TextUtils.isEmpty(applicationName)) {

            Log.d(TAG, "instantiateApplication application name specified but AppComponentFactory no specified");
            return super.instantiateApplication(cl, applicationName);
        }
        else{

            Log.d(TAG, "instantiateApplication application name and AppComponentFactory no specified");
            return super.instantiateApplication(shellClassLoader, className);
        }
    }

    /**
     * This method add in Android 10
     */
    @Override
    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo) {
        Log.d(TAG, "instantiateClassLoader() called with: cl = [" + cl + "], aInfo = [" + aInfo + "]");
        ClassLoader classLoader = init(cl);

        shellClassLoader = cl;

        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(classLoader);

        Global.sIsReplacedClassLoader = true;

        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateClassLoader", ClassLoader.class, ApplicationInfo.class);
                return (ClassLoader) method.invoke(targetAppComponentFactory, classLoader, aInfo);

            } catch (Exception e) {
            }
        }
        return super.instantiateClassLoader(classLoader, aInfo);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateReceiver() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);

        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateReceiver", ClassLoader.class, String.class, Intent.class);
                return (BroadcastReceiver) method.invoke(targetAppComponentFactory, cl, className, intent);

            } catch (Exception e) {
            }
        }
        return super.instantiateReceiver(cl, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateService() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);

        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateService", ClassLoader.class, String.class, Intent.class);
                return (Service) method.invoke(targetAppComponentFactory, cl, className, intent);

            } catch (Exception e) {
            }
        }
        return super.instantiateService(cl, className, intent);
    }


    @NonNull
    @Override
    public ContentProvider instantiateProvider(@NonNull ClassLoader cl, @NonNull String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateProvider() called with: cl = [" + cl + "], className = [" + className + "]");
        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);
        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateProvider", ClassLoader.class, String.class);
                return (ContentProvider) method.invoke(targetAppComponentFactory, cl, className);

            } catch (Exception e) {
            }
        }
        return super.instantiateProvider(cl, className);
    }

}
