package com.example.dptfixture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

@TargetApi(28)
public final class FixtureAppComponentFactory extends AppComponentFactory {
    @Override
    public ClassLoader instantiateClassLoader(ClassLoader classLoader,
                                              ApplicationInfo applicationInfo) {
        FixtureProbe.record("factory-class-loader");
        return super.instantiateClassLoader(classLoader, applicationInfo);
    }

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        FixtureProbe.record("factory-application");
        return super.instantiateApplication(classLoader, className);
    }

    @Override
    public Activity instantiateActivity(ClassLoader classLoader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        FixtureProbe.record("factory-activity");
        return super.instantiateActivity(classLoader, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader classLoader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        FixtureProbe.record("factory-service");
        return super.instantiateService(classLoader, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader classLoader, String className,
                                                 Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        FixtureProbe.record("factory-receiver");
        return super.instantiateReceiver(classLoader, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        FixtureProbe.record("factory-provider");
        return super.instantiateProvider(classLoader, className);
    }
}
