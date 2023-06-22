package com.luoye.dpt;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.ContentProvider;
import android.content.Intent;
import android.util.Log;
@TargetApi(28)
public class MyAppComponentFactory extends AppComponentFactory {
    public static final String TAG = MyAppComponentFactory.class.getSimpleName();
    @Override
    public Service instantiateService( ClassLoader cl,  String className,  Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateService() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        return super.instantiateService(cl, className, intent);
    }

    @Override
    public Application instantiateApplication( ClassLoader cl,  String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateApplication() called with: cl = [" + cl + "], className = [" + className + "]");
        return super.instantiateApplication(cl, className);
    }

    @Override
    public Activity instantiateActivity( ClassLoader cl,  String className,  Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateActivity() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        return super.instantiateActivity(cl, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider( ClassLoader cl,  String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateProvider() called with: cl = [" + cl + "], className = [" + className + "]");
        return super.instantiateProvider(cl, className);
    }
}
