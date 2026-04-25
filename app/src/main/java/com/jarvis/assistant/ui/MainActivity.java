package com.jarvis.assistant.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERM_ALL = 200;
    private static final int OVERLAY_REQUEST = 201;

    private TextView tvStatus;
    private JarvisSpeech jarvisSpeech;
    private boolean receiverRegistered = false;

    private final String[] ALL_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.PROCESS_OUTGOING_CALLS,
    };

    private BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if ("com.jarvis.UI_UPDATE".equals(intent.getAction())) {
                    String type = intent.getStringExtra("type");
                    String content = intent.getStringExtra("content");
                    runOnUiThread(() -> {
                        if ("response".equals(type) && tvStatus != null)
                            tvStatus.setText(content);
                    });
                } else if ("com.jarvis.STATE_CHANGE".equals(intent.getAction())) {
                    runOnUiThread(() -> updateStatus(intent.getStringExtra("state")));
                }
            } catch (Exception e) { Log.e(TAG, "Receiver: " + e.getMessage()); }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
            Log.e("JARVIS_CRASH", "FATAL: " + e.getMessage(), e));
        try {
            setContentView(R.layout.activity_simple);
            tvStatus = findViewById(R.id.tv_status_simple);
            if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Initializing...");

            Button btn = findViewById(R.id.btn_test);
            if (btn != null) {
                btn.setText("ACTIVATE");
                btn.setOnClickListener(v -> {
                    if (jarvisSpeech != null)
                        jarvisSpeech.speak("Yes Sir, at your service. How may I assist you?");
                });
                btn.setOnLongClickListener(v -> {
                    showSettingsDialog();
                    return true;
                });
            }

            // Init speech first
            jarvisSpeech = new JarvisSpeech(this);

            // Start permission flow
            new Handler().postDelayed(this::startPermissionFlow, 800);

        } catch (Exception e) {
            Log.e(TAG, "onCreate: " + e.getMessage(), e);
        }
    }

    private void startPermissionFlow() {
        List<String> missing = new ArrayList<>();
        List<String> permList = new ArrayList<>();

        // Add base permissions
        for (String p : ALL_PERMISSIONS) {
            permList.add(p);
        }

        // Add version-specific permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permList.add(Manifest.permission.POST_NOTIFICATIONS);
            permList.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permList.add(Manifest.permission.BLUETOOTH_CONNECT);
            permList.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        for (String p : permList) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }

        if (!missing.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("J.A.R.V.I.S Needs Your Permission")
                .setMessage("To fully serve you Sir, I require access to:\n\n" +
                    "📞 Phone & Contacts — to make calls\n" +
                    "💬 Messages — to send SMS\n" +
                    "🎤 Microphone — to hear your commands\n" +
                    "📷 Camera — for flashlight control\n" +
                    "📍 Location — for navigation\n" +
                    "📅 Calendar — to manage your schedule\n" +
                    "🔔 Notifications — to keep you informed\n\n" +
                    "Please grant all permissions Sir.")
                .setPositiveButton("Grant All", (d, w) ->
                    ActivityCompat.requestPermissions(this,
                        missing.toArray(new String[0]), PERM_ALL))
                .setNegativeButton("Skip", (d, w) -> checkOverlay())
                .setCancelable(false)
                .show();
        } else {
            checkOverlay();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        // Check for denied
        boolean anyDenied = false;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) { anyDenied = true; break; }
        }
        if (anyDenied) {
            new AlertDialog.Builder(this)
                .setTitle("Permissions Incomplete")
                .setMessage("Some permissions were denied Sir. " +
                    "J.A.R.V.I.S may have limited functionality.\n\n" +
                    "You can grant them anytime via App Settings.")
                .setPositiveButton("Open App Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Continue", (d, w) -> checkOverlay())
                .show();
        } else {
            checkOverlay();
        }
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Display Over Apps")
                .setMessage("Sir, please:\n\n" +
                    "1. Find 'J.A.R.V.I.S' in the list\n" +
                    "2. Toggle 'Allow display over other apps' ON\n" +
                    "3. Press back to return here\n\n" +
                    "This allows J.A.R.V.I.S to show you information " +
                    "while using other apps.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, OVERLAY_REQUEST);
                    } catch (Exception e) {
                        // Some devices don't support direct link
                        try {
                            startActivity(new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                        } catch (Exception ex) {
                            checkAccessibility();
                        }
                    }
                })
                .setNegativeButton("Skip", (d, w) -> checkAccessibility())
                .show();
        } else {
            checkAccessibility();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            checkAccessibility();
        }
    }

    private void checkAccessibility() {
        if (!isAccessibilityEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Accessibility Service")
                .setMessage("Sir, in Accessibility Settings:\n\n" +
                    "1. Scroll to 'Downloaded Apps' or 'Installed Services'\n" +
                    "2. Tap 'J.A.R.V.I.S Screen Monitor'\n" +
                    "3. Toggle it ON → tap Allow\n" +
                    "4. Press back to return\n\n" +
                    "This enables screen monitoring and smart assistance.")
                .setPositiveButton("Open Accessibility", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Skip", (d, w) -> checkNotificationListener())
                .show();
        } else {
            checkNotificationListener();
        }
    }

    private void checkNotificationListener() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Notification Access")
                .setMessage("Sir, in Notification Access settings:\n\n" +
                    "1. Find 'J.A.R.V.I.S' in the list\n" +
                    "2. Toggle it ON → tap Allow\n" +
                    "3. Press back to return\n\n" +
                    "This lets me read and announce your notifications.")
                .setPositiveButton("Open Settings", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Skip", (d, w) -> launchService())
                .show();
        } else {
            launchService();
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> services =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo s : services)
                if (s.getId().contains(getPackageName())) return true;
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private void launchService() {
        new Handler().postDelayed(() -> {
            try {
                Intent si = new Intent(this, JarvisService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(si);
                else
                    startService(si);

                updateStatus("WAITING_WAKE_WORD");

                // Check if Gemini key is set
                SharedPreferences prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE);
                String geminiKey = prefs.getString("gemini_key", "").trim();

                new Handler().postDelayed(() -> {
                    if (geminiKey.isEmpty()) {
                        jarvisSpeech.speak(
                            "Good day Sir. J.A.R.V.I.S is online. " +
                            "Please long press the activate button to add your Gemini API key " +
                            "to enable full intelligence and premium voice.");
                        showSettingsDialog();
                    } else {
                        jarvisSpeech.speak(
                            "Good day Sir. J.A.R.V.I.S is fully operational " +
                            "and at your service. Simply say Jarvis to activate me.");
                    }
                }, 500);

            } catch (Exception e) {
                Log.e(TAG, "Service: " + e.getMessage(), e);
            }
        }, 500);
    }

    private void updateStatus(String state) {
        if (tvStatus == null) return;
        switch (state != null ? state : "") {
            case "WAITING_WAKE_WORD": tvStatus.setText("Listening for \"Jarvis\"..."); break;
            case "AWAKE_LISTENING_COMMAND": tvStatus.setText("Awaiting command, Sir..."); break;
            case "PROCESSING": tvStatus.setText("Processing..."); break;
            default: tvStatus.setText("Standing by, Sir.");
        }
    }

    private void showSettingsDialog() {
        SettingsHelper.show(this, () -> {
            jarvisSpeech.speak(
                "Configuration saved Sir. Intelligence and voice systems updated.");
            SharedPreferences prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE);
            // Reinit speech with new key
            jarvisSpeech = new JarvisSpeech(this);
        });
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
        try { if (jarvisSpeech != null) jarvisSpeech.shutdown(); }
        catch (Exception e) { Log.e(TAG, "onDestroy: " + e.getMessage()); }
    }
}
