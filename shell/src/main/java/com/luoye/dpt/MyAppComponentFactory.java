package com.luoye.dpt;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.ContentProvider;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(28)
public class MyAppComponentFactory extends AppComponentFactory {
    public static final String TAG = MyAppComponentFactory.class.getSimpleName();
    @NonNull
    @Override
    public Service instantiateService(@NonNull ClassLoader cl, @NonNull String className, @Nullable Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateService() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        return super.instantiateService(cl, className, intent);
    }

    @NonNull
    @Override
    public Application instantiateApplication(@NonNull ClassLoader cl, @NonNull String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateApplication() called with: cl = [" + cl + "], className = [" + className + "]");
        return super.instantiateApplication(cl, className);
    }

    @NonNull
    @Override
    public Activity instantiateActivity(@NonNull ClassLoader cl, @NonNull String className, @Nullable Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateActivity() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
        return super.instantiateActivity(cl, className, intent);
    }

    @NonNull
    @Override
    public ContentProvider instantiateProvider(@NonNull ClassLoader cl, @NonNull String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Log.d(TAG, "instantiateProvider() called with: cl = [" + cl + "], className = [" + className + "]");
        return super.instantiateProvider(cl, className);
    }
}
