package com.example.dptfixture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class FixtureService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FixtureProbe.record("service");
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
