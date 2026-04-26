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
                            && !"command".equals(type))
                        tvStatus.setText(content);
                });
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            Log.e("JARVIS_CRASH", "FATAL: " + e.getMessage(), e));

        setContentView(R.layout.activity_simple);

        tvStatus = findViewById(R.id.tv_status_simple);
        btnActivate = findViewById(R.id.btn_test);

        if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Ready");

        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                if (speech != null)
                    speech.speak("Yes Sir, at your service.");
            });
            btnActivate.setOnLongClickListener(v -> {
                showSettingsDialog();
                return true;
            });
        }

        // Step 1: Init TTS on background thread first
        new Thread(() -> {
            try {
                speech = new JarvisSpeech(getApplicationContext());
                // Wait for TTS to warm up
                Thread.sleep(1500);
                // Step 2: Greet
                speech.greetOnStart();
                // Step 3: Update UI
                handler.post(() -> {
                    if (tvStatus != null)
                        tvStatus.setText("Say \"Jarvis\" to activate");
                });
                // Step 4: Start service on main thread
                handler.postDelayed(() -> startJarvisService(), 500);
                // Step 5: Ask permissions
                handler.postDelayed(() -> requestAllPermissions(), 3000);
            } catch (Exception e) {
                Log.e(TAG, "Background init: " + e.getMessage());
            }
        }).start();
    }

    private void startJarvisService() {
        try {
            Intent si = new Intent(this, JarvisService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(si);
            else
                startService(si);
            Log.d(TAG, "Service started");
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
            checkOverlay();
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
        handler.postDelayed(this::checkOverlay, 300);
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Display Over Apps")
                .setMessage("Find J.A.R.V.I.S in the list and toggle ON Sir.")
                .setPositiveButton("Open", (d, w) -> {
                    try {
                        startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())),
                            OVERLAY_REQ);
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                })
                .setNegativeButton("Skip", null).show();
        }
    }

    private void showSettingsDialog() {
        try {
            SettingsHelper.show(this, () ->
                Toast.makeText(this, "Saved Sir.", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
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
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (receiverRegistered) {
                unregisterReceiver(uiReceiver);
                receiverRegistered = false;
            }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (speech != null) speech.shutdown(); }
        catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }
}
