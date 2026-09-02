package com.example.dptfixture;

import android.app.Application;

public final class FixtureApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FixtureProbe.record("application");
    }
}
