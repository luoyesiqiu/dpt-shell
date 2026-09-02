package com.example.dptfixture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class FixtureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FixtureProbe.record("activity");
        verifyClassLoader();
        startService(new Intent(this, FixtureService.class));
        Intent broadcast = new Intent(this, FixtureReceiver.class);
        broadcast.setAction(getPackageName() + ".FIXTURE_PROBE");
        sendBroadcast(broadcast);
        getContentResolver().query(Uri.parse("content://" + getPackageName()
                + ".fixture-provider/status"), null, null, null, null);
        new Handler(Looper.getMainLooper()).postDelayed(this::writeResult, 1_500L);
    }

    private void verifyClassLoader() {
        try {
            Class<?> restored = getClassLoader().loadClass(getClass().getName());
            FixtureProbe.record(restored == getClass() ? "class-loader" : "class-loader-mismatch");
        } catch (ClassNotFoundException exception) {
            FixtureProbe.record("class-loader-missing");
        }
    }

    private void writeResult() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), 0);
            String factory = Build.VERSION.SDK_INT >= 28 ? info.appComponentFactory : null;
            FixtureProbe.writeResult(this, getApplication().getClass().getName(), info.className,
                    factory);
        } catch (Exception exception) {
            FixtureProbe.writeFailure(this, exception);
        }
    }
}
