package com.jarvis.assistant.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.jarvis.assistant.R;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_splash);
        } catch (Exception e) {
            Log.e("SplashActivity", "Layout error: " + e.getMessage());
        }
        new Handler().postDelayed(() -> {
            try {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } catch (Exception e) {
                Log.e("SplashActivity", "Launch error: " + e.getMessage());
            }
        }, 1500);
    }
}
