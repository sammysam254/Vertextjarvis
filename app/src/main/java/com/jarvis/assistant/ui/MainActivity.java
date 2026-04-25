package com.jarvis.assistant.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
    private static final int PERM_REQUEST_1 = 101;
    private static final int PERM_REQUEST_2 = 102;

    // ALL permissions - requested in two batches
    private static final String[] PERMISSIONS_BATCH_1 = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
    };

    private static final String[] PERMISSIONS_BATCH_2 = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    };

    private TextView tvStatus;
    private Button btnActivate;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean receiverRegistered = false;

    private BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if ("com.jarvis.UI_UPDATE".equals(intent.getAction())) {
                    String type = intent.getStringExtra("type");
                    String content = intent.getStringExtra("content");
                    runOnUiThread(() -> {
                        if (tvStatus != null && "response".equals(type)) {
                            tvStatus.setText(content);
                        }
                    });
                } else if ("com.jarvis.STATE_CHANGE".equals(intent.getAction())) {
                    String state = intent.getStringExtra("state");
                    runOnUiThread(() -> updateStatus(state));
                }
            } catch (Exception e) {
                Log.e(TAG, "Receiver: " + e.getMessage());
            }
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
            btnActivate = findViewById(R.id.btn_test);

            if (btnActivate != null) {
                btnActivate.setOnClickListener(v ->
                    speak("Yes Sir, at your service. How may I assist you?"));
                btnActivate.setOnLongClickListener(v -> {
                    showSettingsDialog();
                    return true;
                });
            }

            initTTS();
            requestBatch1();

        } catch (Exception e) {
            Log.e(TAG, "onCreate: " + e.getMessage(), e);
        }
    }

    // ─── TTS WITH BEST AVAILABLE MALE/FORMAL VOICE ───────────────────────────

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Try to set best formal voice
                setBestVoice();
                tts.setSpeechRate(0.88f);  // Slower = more authoritative
                tts.setPitch(0.80f);       // Lower = deeper, more formal
                ttsReady = true;
                Log.d(TAG, "TTS ready.");
            }
        });
    }

    private void setBestVoice() {
        try {
            // Priority: deep male en-GB voices
            Set<Voice> voices = tts.getVoices();
            if (voices == null) {
                tts.setLanguage(Locale.UK);
                return;
            }

            Voice bestVoice = null;

            // Look for high-quality deep male British voice
            for (Voice v : voices) {
                String name = v.getName().toLowerCase();
                boolean isEnglish = name.contains("en-gb") || name.contains("en_gb")
                    || name.contains("en-in") || name.contains("en_in");
                boolean isMale = name.contains("male") || name.contains("man")
                    || name.contains("guy") || name.contains("default");
                boolean isHighQuality = v.getQuality() >= Voice.QUALITY_NORMAL;

                if (isEnglish && isHighQuality) {
                    if (bestVoice == null) bestVoice = v;
                    if (isMale) { bestVoice = v; break; }
                }
            }

            if (bestVoice != null) {
                tts.setVoice(bestVoice);
                Log.d(TAG, "Using voice: " + bestVoice.getName());
            } else {
                tts.setLanguage(new Locale("en", "GB"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Voice selection error: " + e.getMessage());
            try { tts.setLanguage(Locale.UK); } catch (Exception ignored) {}
        }
    }

    public void speak(String text) {
        try {
            if (tts != null && ttsReady) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "j_" + System.currentTimeMillis());
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS speak error: " + e.getMessage());
        }
    }

    // ─── PERMISSIONS ──────────────────────────────────────────────────────────

    private void requestBatch1() {
        List<String> missing = getMissing(PERMISSIONS_BATCH_1);
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERM_REQUEST_1);
        } else {
            requestBatch2();
        }
    }

    private void requestBatch2() {
        List<String> missing = getMissing(PERMISSIONS_BATCH_2);
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERM_REQUEST_2);
        } else {
            checkSpecialPermissions();
        }
    }

    private List<String> getMissing(String[] perms) {
        List<String> missing = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        return missing;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST_1) {
            requestBatch2();
        } else if (requestCode == PERM_REQUEST_2) {
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission — Required")
                .setMessage("J.A.R.V.I.S needs to display over other apps to assist you effectively, Sir.")
                .setPositiveButton("Grant", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(i);
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
                .setMessage("Enable J.A.R.V.I.S Accessibility Service so I can monitor your screen and assist you contextually, Sir.")
                .setPositiveButton("Enable Now", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .setNegativeButton("Later", (d, w) -> checkNotificationAccess())
                .show();
        } else {
            checkNotificationAccess();
        }
    }

    private void checkNotificationAccess() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Notification Access — Required")
                .setMessage("Allow J.A.R.V.I.S to read notifications so I can keep you informed of important messages, Sir.")
                .setPositiveButton("Enable Now", (d, w) -> {
                    startActivity(new Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                })
                .setNegativeButton("Later", (d, w) -> launchJarvisService())
                .show();
        } else {
            launchJarvisService();
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager)
                getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> services =
                am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : services) {
                if (info.getId().contains(getPackageName())) return true;
            }
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

    // ─── SERVICE LAUNCH ───────────────────────────────────────────────────────

    private void launchJarvisService() {
        new Handler().postDelayed(() -> {
            try {
                Intent si = new Intent(this, JarvisService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(si);
                } else {
                    startService(si);
                }
                updateStatus("WAITING_WAKE_WORD");
                new Handler().postDelayed(() ->
                    speak("Good day Sir. J.A.R.V.I.S is fully online and at your service. " +
                          "Simply say Jarvis to activate me."), 500);
            } catch (Exception e) {
                Log.e(TAG, "Service error: " + e.getMessage(), e);
            }
        }, 600);
    }

    private void updateStatus(String state) {
        if (tvStatus == null) return;
        switch (state) {
            case "WAITING_WAKE_WORD":
                tvStatus.setText("Listening for \"Jarvis\"..."); break;
            case "AWAKE_LISTENING_COMMAND":
                tvStatus.setText("Awaiting your command, Sir..."); break;
            case "PROCESSING":
                tvStatus.setText("Processing..."); break;
            default:
                tvStatus.setText("Standing by, Sir.");
        }
    }

    // ─── SETTINGS ─────────────────────────────────────────────────────────────

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
                .setMessage("Anthropic API key (for AI intelligence):\n\nLong-press ACTIVATE to return here.")
                .setView(etKey)
                .setPositiveButton("Save", (d, w) -> {
                    p.edit().putString("api_key",
                        etKey.getText().toString().trim()).apply();
                    Toast.makeText(this, "Saved, Sir.", Toast.LENGTH_SHORT).show();
                    speak("API key saved, Sir. Intelligence module now active.");
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Permissions", (d, w) -> checkSpecialPermissions())
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Settings: " + e.getMessage());
        }
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
        try {
            if (tts != null) { tts.stop(); tts.shutdown(); }
        } catch (Exception e) { Log.e(TAG, "onDestroy: " + e.getMessage()); }
    }
}
