 package com.luoyesiqiu.shell;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.luoye.dpt.ThisApplication;
import com.luoyesiqiu.shell.util.ShellClassLoader;
import com.luoyesiqiu.shell.util.StringUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.lang.reflect.Method;

@RequiresApi(api = 28)
public class ProxyComponentFactory extends AppComponentFactory {
    private static final String TAG = "dpt " + ProxyComponentFactory.class.getSimpleName();
    private static AppComponentFactory sAppComponentFactory;

    private String getTargetClassName(ClassLoader classLoader){
        return JniBridge.rcf(classLoader);
    }

    private AppComponentFactory getTargetAppComponentFactory(ClassLoader classLoader){
        if(sAppComponentFactory == null){
            String targetClassName = getTargetClassName(classLoader);
            Log.d(TAG,"targetClassName = " + targetClassName);
            if(!StringUtils.isEmpty(targetClassName)) {
                try {
                    sAppComponentFactory = (AppComponentFactory) Class.forName(targetClassName).newInstance();
                    return sAppComponentFactory;
                } catch (Exception e) {
                }
            }
        }

        return sAppComponentFactory;
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
        ProxyApplication.initialized = true;
        JniBridge.ia(null,cl);
        String apkPath = JniBridge.gap(cl);
        ClassLoader classLoader = ShellClassLoader.loadDex(apkPath);

        JniBridge.mde(cl,classLoader);

        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);
        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateApplication", ClassLoader.class, String.class);
                return (Application) method.invoke(targetAppComponentFactory, cl, className);

            } catch (Exception e) {
            }
        }
        return super.instantiateApplication(cl, ThisApplication.class.getName());

    }

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo) {
        Log.d(TAG, "instantiateClassLoader() called with: cl = [" + cl + "], aInfo = [" + aInfo + "]");
        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);

        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateClassLoader", ClassLoader.class, ApplicationInfo.class);
                return (ClassLoader) method.invoke(targetAppComponentFactory, cl, aInfo);

            } catch (Exception e) {
            }
        }
        return super.instantiateClassLoader(cl, aInfo);
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
    public Service instantiateService(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateService() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        AppComponentFactory targetAppComponentFactory = getTargetAppComponentFactory(cl);

        if(targetAppComponentFactory != null) {
            try {
                Method method = AppComponentFactory.class.getDeclaredMethod("instantiateReceiver", ClassLoader.class, String.class, Intent.class);
                return (Service) method.invoke(targetAppComponentFactory, cl, className, intent);

            } catch (Exception e) {
            }
        }
        return super.instantiateService(cl, className, intent);
    }


    @NonNull
    @Override
    public ContentProvider instantiateProvider(@NonNull ClassLoader cl, @NonNull String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
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
