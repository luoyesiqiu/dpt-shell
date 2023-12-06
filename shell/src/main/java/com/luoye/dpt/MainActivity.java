package com.luoye.dpt;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String TAG = "dpt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //In most cases, as follow code don't be run.
        TextView showTextView = findViewById(R.id.show_text);
        StringBuilder showText = new StringBuilder();
        showText.append("dpt-shell seem not working.\n");
        showText.append("Application: ");
        showText.append(getApplication().getClass().getName());
        showTextView.setText(showText);
    }

}