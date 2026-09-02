package com.example.dptfixture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FixtureReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        FixtureProbe.record("receiver");
    }
}
