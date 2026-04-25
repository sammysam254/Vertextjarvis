package com.jarvis.assistant.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.jarvis.assistant.R;
import com.jarvis.assistant.services.JarvisService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
    };

    private TextView tvStatus;
    private RecyclerView rvConversation;
    private ConversationAdapter conversationAdapter;
    private List<ConversationAdapter.ConversationItem> conversationItems = new ArrayList<>();
    private boolean receiverRegistered = false;
    private TextToSpeech tts;

    private BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if ("com.jarvis.UI_UPDATE".equals(action)) {
                    String type = intent.getStringExtra("type");
                    String content = intent.getStringExtra("content");
                    runOnUiThread(() -> {
                        if ("command".equals(type)) addConversation("user", content);
                        else if ("response".equals(type)) addConversation("jarvis", content);
                    });
                } else if ("com.jarvis.STATE_CHANGE".equals(action)) {
                    String state = intent.getStringExtra("state");
                    runOnUiThread(() -> updateStatus(state));
                }
            } catch (Exception e) {
                Log.e(TAG, "Receiver error: " + e.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
            Log.e("JARVIS_CRASH", "FATAL: " + throwable.getMessage(), throwable));

        try {
            setContentView(R.layout.activity_simple);
            initViews();
            initTTS();
            requestPermissionsThenStart();
        } catch (Exception e) {
            Log.e(TAG, "onCreate error: " + e.getMessage(), e);
        }
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status_simple);
        if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S — Initializing...");

        Button btnActivate = findViewById(R.id.btn_test);
        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                speak("Yes Sir, at your service. How may I assist you?");
                addConversation("jarvis", "Yes Sir, at your service.");
            });
            btnActivate.setOnLongClickListener(v -> {
                showSettingsDialog();
                return true;
            });
        }

        // Set up conversation list if present
        rvConversation = findViewById(R.id.rv_conversation);
        if (rvConversation != null) {
            conversationAdapter = new ConversationAdapter(conversationItems);
            rvConversation.setLayoutManager(new LinearLayoutManager(this));
            rvConversation.setAdapter(conversationAdapter);
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.UK);
                tts.setSpeechRate(0.92f);
                tts.setPitch(0.85f);
            }
        });
    }

    private void speak(String text) {
        try {
            if (tts != null) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_" + System.currentTimeMillis());
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS error: " + e.getMessage());
        }
    }

    private void requestPermissionsThenStart() {
        List<String> missing = new ArrayList<>();
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        } else {
            launchJarvisService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        launchJarvisService();
    }

    private void launchJarvisService() {
        new Handler().postDelayed(() -> {
            try {
                Intent serviceIntent = new Intent(this, JarvisService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                if (tvStatus != null) tvStatus.setText("J.A.R.V.I.S Online — Say \"Jarvis\"");
                addConversation("jarvis", "Good day, Sir. J.A.R.V.I.S is online and at your service.");
                speak("Good day Sir. J.A.R.V.I.S is online and at your service.");
            } catch (Exception e) {
                Log.e(TAG, "Service start error: " + e.getMessage(), e);
                if (tvStatus != null) tvStatus.setText("Service error — check logs");
            }
        }, 800);
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

    private void addConversation(String from, String text) {
        try {
            conversationItems.add(new ConversationAdapter.ConversationItem(from, text));
            if (conversationAdapter != null)
                conversationAdapter.notifyItemInserted(conversationItems.size() - 1);
            if (rvConversation != null)
                rvConversation.scrollToPosition(conversationItems.size() - 1);
        } catch (Exception e) {
            Log.e(TAG, "addConversation error: " + e.getMessage());
        }
    }

    private void showSettingsDialog() {
        try {
            EditText etApiKey = new EditText(this);
            etApiKey.setHint("sk-ant-...");
            etApiKey.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            SharedPreferences prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
            etApiKey.setText(prefs.getString("api_key", ""));

            new AlertDialog.Builder(this)
                .setTitle("J.A.R.V.I.S Configuration")
                .setMessage("Enter Anthropic API key.\n\nLong-press ACTIVATE anytime to return here.")
                .setView(etApiKey)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putString("api_key",
                        etApiKey.getText().toString().trim()).apply();
                    Toast.makeText(this, "API key saved, Sir.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Accessibility", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ex) { Log.e(TAG, ex.getMessage()); }
                })
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Settings error: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (!receiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.jarvis.UI_UPDATE");
                filter.addAction("com.jarvis.STATE_CHANGE");
                registerReceiver(uiReceiver, filter);
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
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
