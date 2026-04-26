package com.jarvis.assistant.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.jarvis.assistant.R;
import com.jarvis.assistant.services.JarvisService;
import com.jarvis.assistant.utils.JarvisSpeech;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERM_REQ = 200;
    private static final int OVERLAY_REQ = 201;

    private TextView tvStatus;
    private Button btnActivate;
    private JarvisSpeech speech;
    private boolean receiverRegistered = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                String content = intent.getStringExtra("content");
                String type = intent.getStringExtra("type");
                runOnUiThread(() -> {
                    if (tvStatus != null && content != null
                            && !"command".equals(type)) {
                        tvStatus.setText(content);
                    }
                });
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Crash safety net
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            Log.e("JARVIS_CRASH", "FATAL: " + e.getMessage(), e));

        // Set layout FIRST — before anything else
        setContentView(R.layout.activity_simple);

        // Bind views
        tvStatus = findViewById(R.id.tv_status_simple);
        btnActivate = findViewById(R.id.btn_test);

        // Show UI immediately
        if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Ready");

        // Button — tap to speak, long press for settings
        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                if (tvStatus != null) tvStatus.setText("Listening...");
                if (speech != null)
                    speech.speak("Yes Sir, at your service.");
            });
            btnActivate.setOnLongClickListener(v -> {
                showSettingsDialog();
                return true;
            });
        }

        // All heavy work OFF the main thread — delayed
        handler.postDelayed(this::initBackground, 200);
    }

    private void initBackground() {
        // 1. Start service (lightweight)
        startJarvisService();

        // 2. Init TTS off main thread
        new Thread(() -> {
            speech = new JarvisSpeech(getApplicationContext());
            // Greet after TTS warms up
            handler.postDelayed(() -> {
                if (speech != null) speech.greetOnStart();
                if (tvStatus != null) tvStatus.setText("Say \"Jarvis\" to activate");
            }, 1500);
        }).start();

        // 3. Request permissions after UI is settled
        handler.postDelayed(this::requestAllPermissions, 2000);
    }

    private void startJarvisService() {
        try {
            Intent si = new Intent(this, JarvisService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(si);
            else
                startService(si);
            Log.d(TAG, "JarvisService started");
        } catch (Exception e) {
            Log.e(TAG, "startService: " + e.getMessage());
        }
    }

    private void requestAllPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : buildPermList()) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED)
                missing.add(p);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERM_REQ);
        } else {
            checkOverlayPermission();
        }
    }

    private String[] buildPermList() {
        List<String> l = new ArrayList<>();
        l.add(Manifest.permission.RECORD_AUDIO);
        l.add(Manifest.permission.CALL_PHONE);
        l.add(Manifest.permission.READ_CONTACTS);
        l.add(Manifest.permission.WRITE_CONTACTS);
        l.add(Manifest.permission.READ_PHONE_STATE);
        l.add(Manifest.permission.SEND_SMS);
        l.add(Manifest.permission.READ_SMS);
        l.add(Manifest.permission.RECEIVE_SMS);
        l.add(Manifest.permission.CAMERA);
        l.add(Manifest.permission.ACCESS_FINE_LOCATION);
        l.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        l.add(Manifest.permission.READ_CALENDAR);
        l.add(Manifest.permission.WRITE_CALENDAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            l.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            l.add(Manifest.permission.BLUETOOTH_CONNECT);
            l.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        return l.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int req,
            String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        // Check overlay after permissions
        handler.postDelayed(this::checkOverlayPermission, 500);
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Display Over Apps")
                .setMessage("Find J.A.R.V.I.S in the list and toggle ON, Sir.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())),
                            OVERLAY_REQ);
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                })
                .setNegativeButton("Skip", null)
                .show();
        } else {
            promptAccessibility();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_REQ) promptAccessibility();
    }

    private void promptAccessibility() {
        // Only show if not already enabled
        if (!isAccessibilityOn()) {
            new AlertDialog.Builder(this)
                .setTitle("Accessibility Service")
                .setMessage("Sir, go to:\n\nDownloaded Apps → " +
                    "J.A.R.V.I.S Screen Monitor → Toggle ON\n\n" +
                    "This enables screen reading and smart assistance.")
                .setPositiveButton("Open", (d, w) ->
                    startActivity(new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
        }
    }

    private boolean isAccessibilityOn() {
        try {
            String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private void showSettingsDialog() {
        try {
            SettingsHelper.show(this, () ->
                Toast.makeText(this, "Saved, Sir.", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { Log.e(TAG, "Settings: " + e.getMessage()); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (!receiverRegistered) {
                IntentFilter f = new IntentFilter();
                f.addAction("com.jarvis.UI_UPDATE");
                f.addAction("com.jarvis.STATE_CHANGE");
                registerReceiver(uiReceiver, f);
                receiverRegistered = true;
            }
        } catch (Exception e) { Log.e(TAG, "onResume: " + e.getMessage()); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (receiverRegistered) {
                unregisterReceiver(uiReceiver);
                receiverRegistered = false;
            }
        } catch (Exception e) { Log.e(TAG, "onPause: " + e.getMessage()); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (speech != null) speech.shutdown(); }
        catch (Exception e) { Log.e(TAG, "onDestroy: " + e.getMessage()); }
    }
}
