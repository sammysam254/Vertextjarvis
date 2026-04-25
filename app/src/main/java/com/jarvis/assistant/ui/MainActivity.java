package com.jarvis.assistant.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.jarvis.assistant.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Global crash catcher
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("JARVIS_CRASH", "UNCAUGHT: " + throwable.getMessage(), throwable);
        });

        try {
            setContentView(R.layout.activity_simple);
            TextView tv = findViewById(R.id.tv_status_simple);
            if (tv != null) tv.setText("J.A.R.V.I.S Online, Sir.");
        } catch (Exception e) {
            Log.e("JARVIS_CRASH", "onCreate failed: " + e.getMessage(), e);
        }
    }
}
