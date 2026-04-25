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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.jarvis.assistant.R;
import com.jarvis.assistant.services.JarvisService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERM_ALL = 200;

    private TextView tvStatus;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean receiverRegistered = false;

    // ALL dangerous permissions in one list
    private String[] getAllPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        perms.add(Manifest.permission.READ_PHONE_STATE);
        perms.add(Manifest.permission.CALL_PHONE);
        perms.add(Manifest.permission.READ_CONTACTS);
        perms.add(Manifest.permission.WRITE_CONTACTS);
        perms.add(Manifest.permission.SEND_SMS);
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.RECEIVE_SMS);
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.READ_CALENDAR);
        perms.add(Manifest.permission.WRITE_CALENDAR);
        perms.add(Manifest.permission.PROCESS_OUTGOING_CALLS);
        // Android 13+ specific
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        // Android 12+ Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        return perms.toArray(new String[0]);
    }

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
            Button btn = findViewById(R.id.btn_test);
            if (btn != null) {
                btn.setOnClickListener(v ->
                    speak("Yes Sir, at your service. How may I assist you?"));
                btn.setOnLongClickListener(v -> { showSettingsDialog(); return true; });
            }
            initTTS();
            // First uninstall old version then install fresh to get all permissions
            requestAllPermissions();
        } catch (Exception e) {
            Log.e(TAG, "onCreate: " + e.getMessage(), e);
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                setBestFormalVoice();
                tts.setSpeechRate(0.88f);
                tts.setPitch(0.78f);
                ttsReady = true;
            }
        });
    }

    private void setBestFormalVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) { tts.setLanguage(Locale.UK); return; }
            Voice best = null;
            for (Voice v : voices) {
                String n = v.getName().toLowerCase();
                boolean eng = n.contains("en-gb") || n.contains("en_gb")
                           || n.contains("en-us") || n.contains("en_us");
                boolean quality = v.getQuality() >= Voice.QUALITY_NORMAL;
                boolean notNet = !v.isNetworkConnectionRequired();
                if (eng && quality && notNet) {
                    if (best == null) best = v;
                    // Prefer deeper/male sounding names
                    if (n.contains("male") || n.contains("en-gb-x")
                            || n.contains("en_gb")) { best = v; break; }
                }
            }
            if (best != null) tts.setVoice(best);
            else tts.setLanguage(Locale.UK);
        } catch (Exception e) {
            try { tts.setLanguage(Locale.UK); } catch (Exception ignored) {}
        }
    }

    public void speak(String text) {
        try {
            if (tts != null && ttsReady)
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j_" + System.currentTimeMillis());
        } catch (Exception e) { Log.e(TAG, "speak: " + e.getMessage()); }
    }

    private void requestAllPermissions() {
        // Find which ones are missing
        List<String> missing = new ArrayList<>();
        for (String p : getAllPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            // Show explanation first
            new AlertDialog.Builder(this)
                .setTitle("J.A.R.V.I.S Needs Permissions")
                .setMessage("To serve you fully, Sir, I require access to:\n\n" +
                    "• Phone & Contacts (to make calls)\n" +
                    "• Messages (to send SMS)\n" +
                    "• Microphone (to hear your commands)\n" +
                    "• Camera (for flashlight control)\n" +
                    "• Location (for navigation)\n" +
                    "• Calendar (to manage your schedule)\n" +
                    "• Storage (for file access)\n\n" +
                    "Please grant all permissions on the next screens, Sir.")
                .setPositiveButton("Grant All", (d, w) ->
                    ActivityCompat.requestPermissions(this,
                        missing.toArray(new String[0]), PERM_ALL))
                .setNegativeButton("Skip", (d, w) -> checkSpecialPermissions())
                .show();
        } else {
            checkSpecialPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        // Check if any were denied and re-ask
        List<String> denied = new ArrayList<>();
        for (int i = 0; i < perms.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED) denied.add(perms[i]);
        }
        if (!denied.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Some Permissions Denied")
                .setMessage("Some permissions were denied. J.A.R.V.I.S will have " +
                    "limited functionality, Sir. You can grant them anytime in App Settings.")
                .setPositiveButton("Open App Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Continue Anyway", (d, w) -> checkSpecialPermissions())
                .show();
        } else {
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        // Step 1: Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Display Over Apps — Required")
                .setMessage("Allow J.A.R.V.I.S to display over other apps, Sir. " +
                    "Find 'J.A.R.V.I.S' in the list and enable it.")
                .setPositiveButton("Grant", (d, w) -> {
                    startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                })
                .setNegativeButton("Skip", (d, w) -> checkAccessibility())
                .show();
        } else {
            checkAccessibility();
        }
    }

    private void checkAccessibility() {
        if (!isAccessibilityEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Accessibility Service — Required")
                .setMessage("In the Accessibility Settings:\n\n" +
                    "1. Scroll down to 'Downloaded apps'\n" +
                    "2. Tap 'J.A.R.V.I.S Screen Monitor'\n" +
                    "3. Toggle it ON\n" +
                    "4. Tap Allow\n\n" +
                    "Then return to J.A.R.V.I.S, Sir.")
                .setPositiveButton("Open Accessibility", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Skip", (d, w) -> checkNotificationAccess())
                .show();
        } else {
            checkNotificationAccess();
        }
    }

    private void checkNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Notification Access — Required")
                .setMessage("In Notification Access settings:\n\n" +
                    "1. Find 'J.A.R.V.I.S' in the list\n" +
                    "2. Toggle it ON\n" +
                    "3. Tap Allow\n\n" +
                    "Then return to J.A.R.V.I.S, Sir.")
                .setPositiveButton("Open Settings", (d, w) ->
                    startActivity(new Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Skip", (d, w) -> launchJarvisService())
                .show();
        } else {
            launchJarvisService();
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am =
                (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            var services = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (var s : services)
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

    private void launchJarvisService() {
        new Handler().postDelayed(() -> {
            try {
                Intent si = new Intent(this, JarvisService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(si);
                else
                    startService(si);
                updateStatus("WAITING_WAKE_WORD");
                new Handler().postDelayed(() ->
                    speak("Good day Sir. J.A.R.V.I.S is fully online and at your service. " +
                          "Simply say Jarvis to activate me at any time."), 600);
            } catch (Exception e) {
                Log.e(TAG, "Service error: " + e.getMessage(), e);
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
        try {
            EditText etKey = new EditText(this);
            etKey.setHint("sk-ant-api...");
            etKey.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            SharedPreferences p = getSharedPreferences("jarvis_prefs", MODE_PRIVATE);
            etKey.setText(p.getString("api_key", ""));
            new AlertDialog.Builder(this)
                .setTitle("J.A.R.V.I.S Configuration")
                .setMessage("Enter your Anthropic API key:\n(Long-press ACTIVATE to return here)")
                .setView(etKey)
                .setPositiveButton("Save", (d, w) -> {
                    p.edit().putString("api_key",
                        etKey.getText().toString().trim()).apply();
                    Toast.makeText(this, "Saved, Sir.", Toast.LENGTH_SHORT).show();
                    speak("API key saved Sir. Intelligence module is now active.");
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Re-check Permissions", (d, w) -> checkSpecialPermissions())
                .show();
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
        try { if (tts != null) { tts.stop(); tts.shutdown(); } }
        catch (Exception e) { Log.e(TAG, "onDestroy: " + e.getMessage()); }
    }
}
