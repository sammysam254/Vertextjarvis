package com.jarvis.assistant.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
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
    private static final String PREFS = "jarvis_prefs";
    private static final String KEY_SETUP_DONE = "setup_done";

    private TextView tvStatus;
    private Button btnActivate;
    private JarvisSpeech speech;
    private boolean receiverRegistered = false;
    private Handler handler = new Handler();

    private BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                if ("com.jarvis.UI_UPDATE".equals(intent.getAction())) {
                    String content = intent.getStringExtra("content");
                    String type = intent.getStringExtra("type");
                    if (content != null) {
                        runOnUiThread(() -> {
                            if (tvStatus != null) tvStatus.setText(content);
                        });
                    }
                } else if ("com.jarvis.STATE_CHANGE".equals(intent.getAction())) {
                    String state = intent.getStringExtra("state");
                    runOnUiThread(() -> updateStatus(state));
                }
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Global crash handler
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            Log.e("JARVIS_CRASH", "FATAL: " + e.getMessage(), e));

        try {
            setContentView(R.layout.activity_simple);

            // Bind views
            tvStatus = findViewById(R.id.tv_status_simple);
            btnActivate = findViewById(R.id.btn_test);

            if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Starting up...");

            // Button listeners
            if (btnActivate != null) {
                btnActivate.setOnClickListener(v -> {
                    if (speech != null)
                        speech.speak("Yes Sir, at your service. How may I assist you?");
                    if (tvStatus != null) tvStatus.setText("Listening for your command, Sir...");
                });
                btnActivate.setOnLongClickListener(v -> {
                    showSettingsDialog();
                    return true;
                });
            }

            // Init speech immediately — no waiting
            speech = new JarvisSpeech(this);

            // Start service immediately — don't wait for permissions
            handler.postDelayed(this::startJarvisService, 300);

            // Do permissions in background — non-blocking
            handler.postDelayed(this::checkPermissionsQuietly, 1500);

        } catch (Exception e) {
            Log.e(TAG, "onCreate crash: " + e.getMessage(), e);
        }
    }

    private void startJarvisService() {
        try {
            Intent si = new Intent(this, JarvisService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(si);
            else
                startService(si);

            if (tvStatus != null) tvStatus.setText("Listening for \"Jarvis\"...");

            // Greet after short delay
            handler.postDelayed(() -> {
                if (speech != null) speech.greetOnStart();
            }, 1000);

        } catch (Exception e) {
            Log.e(TAG, "startService: " + e.getMessage());
            if (tvStatus != null) tvStatus.setText("Service error — tap ACTIVATE to retry");
        }
    }

    private void checkPermissionsQuietly() {
        // Build list of missing permissions
        List<String> missing = new ArrayList<>();
        String[] needed = buildPermissionList();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                missing.add(p);
        }

        if (!missing.isEmpty()) {
            // Request quietly — no blocking dialog first
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERM_REQ);
        }

        // Check overlay separately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            handler.postDelayed(this::promptOverlay, 3000);
        }
    }

    private String[] buildPermissionList() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.RECORD_AUDIO);
        list.add(Manifest.permission.CALL_PHONE);
        list.add(Manifest.permission.READ_CONTACTS);
        list.add(Manifest.permission.WRITE_CONTACTS);
        list.add(Manifest.permission.READ_PHONE_STATE);
        list.add(Manifest.permission.SEND_SMS);
        list.add(Manifest.permission.READ_SMS);
        list.add(Manifest.permission.RECEIVE_SMS);
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        list.add(Manifest.permission.READ_CALENDAR);
        list.add(Manifest.permission.WRITE_CALENDAR);
        list.add(Manifest.permission.PROCESS_OUTGOING_CALLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
            list.add(Manifest.permission.READ_MEDIA_IMAGES);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
            list.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        return list.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        // Count denied
        int denied = 0;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) denied++;
        if (denied > 0) {
            // Show one small toast — not a blocking dialog
            Toast.makeText(this,
                denied + " permissions denied. Some features may be limited.",
                Toast.LENGTH_LONG).show();
        }
    }

    private void promptOverlay() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("One More Step")
                .setMessage("Allow J.A.R.V.I.S to display over other apps?\n\n" +
                    "Find J.A.R.V.I.S in the list and toggle ON.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                        startActivityForResult(i, OVERLAY_REQ);
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                })
                .setNegativeButton("Skip", null)
                .show();
        } catch (Exception e) { Log.e(TAG, "promptOverlay: " + e.getMessage()); }
    }

    private void updateStatus(String state) {
        if (tvStatus == null || state == null) return;
        switch (state) {
            case "WAITING_WAKE_WORD": tvStatus.setText("Listening for \"Jarvis\"..."); break;
            case "AWAKE_LISTENING_COMMAND": tvStatus.setText("Awaiting command, Sir..."); break;
            case "PROCESSING": tvStatus.setText("Processing..."); break;
            default: tvStatus.setText("Standing by, Sir.");
        }
    }

    private void showSettingsDialog() {
        try {
            SettingsHelper.show(this, () -> {
                Toast.makeText(this, "Saved, Sir.", Toast.LENGTH_SHORT).show();
                if (speech != null) speech = new JarvisSpeech(this);
            });
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
