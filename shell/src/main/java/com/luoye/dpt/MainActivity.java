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
        //以下代码不会被执行，因为运行时被覆盖
        TextView showTextView = findViewById(R.id.show_text);
        StringBuilder showText = new StringBuilder();
        showText.append("Application: ");
        showText.append(getApplication().getClass().getName());
        showTextView.setText(showText);
    }

}