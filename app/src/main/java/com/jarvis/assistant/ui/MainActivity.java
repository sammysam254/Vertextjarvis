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
    private static final String PREF = "jarvis_prefs";

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
                    if (tvStatus != null && content != null && !"command".equals(type))
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

        if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Starting...");

        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                if (speech != null)
                    speech.speak("Yes Sir, at your service. How may I assist you?");
            });
            btnActivate.setOnLongClickListener(v -> {
                showOptionsMenu();
                return true;
            });
        }

        // Init everything on background thread — never block UI
        new Thread(() -> {
            try {
                // Init speech
                speech = new JarvisSpeech(getApplicationContext());
                Thread.sleep(800);

                // Start service
                handler.post(this::startJarvisService);
                Thread.sleep(1000);

                // Greet
                speech.greetOnStart();

                handler.post(() -> {
                    if (tvStatus != null)
                        tvStatus.setText("Say \"Jarvis\" to activate");
                });

                // Ask permissions after 3s
                Thread.sleep(3000);
                handler.post(this::requestPermissions);

            } catch (Exception e) {
                Log.e(TAG, "Init thread: " + e.getMessage());
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
        } catch (Exception e) {
            Log.e(TAG, "startService: " + e.getMessage());
        }
    }

    private void requestPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : getPermList()) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED)
                missing.add(p);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERM_REQ);
        } else {
            // All granted — check special permissions
            handler.postDelayed(this::checkOverlay, 500);
        }
    }

    private String[] getPermList() {
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
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        handler.postDelayed(this::checkOverlay, 500);
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Needed — Display Over Apps")
                .setMessage("Sir, please find J.A.R.V.I.S in the list and toggle ON.\n\nThis allows J.A.R.V.I.S to assist you while using other apps.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        startActivityForResult(
                            new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())), OVERLAY_REQ);
                    } catch (Exception e) { checkAccessibility(); }
                })
                .setNegativeButton("Skip", (d, w) -> checkAccessibility())
                .show();
        } else {
            checkAccessibility();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_REQ) checkAccessibility();
    }

    private void checkAccessibility() {
        if (!isAccessibilityOn()) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Needed — Accessibility")
                .setMessage("Sir, to enable screen reading:\n\n" +
                    "1. Tap 'Open Settings' below\n" +
                    "2. Scroll down to 'Downloaded Apps'\n" +
                    "3. Tap 'J.A.R.V.I.S Screen Monitor'\n" +
                    "4. Toggle it ON and tap Allow\n" +
                    "5. Press back to return here")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) { checkNotifications(); }
                })
                .setNegativeButton("Skip", (d, w) -> checkNotifications())
                .show();
        } else {
            checkNotifications();
        }
    }

    private void checkNotifications() {
        if (!isNotificationListenerOn()) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Needed — Notifications")
                .setMessage("Sir, to enable notification reading:\n\n" +
                    "1. Tap 'Open Settings' below\n" +
                    "2. Find J.A.R.V.I.S in the list\n" +
                    "3. Toggle it ON and tap Allow\n" +
                    "4. Press back to return here")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        startActivity(new Intent(
                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                })
                .setNegativeButton("Skip", null)
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

    private boolean isNotificationListenerOn() {
        try {
            String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private void showOptionsMenu() {
        String[] options = {
            "Enter Gemini API Key",
            "Re-check Permissions",
            "Test Voice",
            "Clear AI Memory"
        };
        new AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S Settings")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: showApiKeyDialog(); break;
                    case 1: checkOverlay(); break;
                    case 2:
                        if (speech != null)
                            speech.speak("Voice test successful Sir. Gemini is online.");
                        break;
                    case 3:
                        new com.jarvis.assistant.utils.JarvisAI(this).clearHistory();
                        Toast.makeText(this, "Memory cleared Sir.", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .show();
    }

    private void showApiKeyDialog() {
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
