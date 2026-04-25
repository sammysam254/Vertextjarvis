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
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.jarvis.assistant.utils.JarvisSpeech;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 100;

    // Permissions needed
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    };

    private TextView tvStatus;
    private TextView tvLastCommand;
    private TextView tvLastResponse;
    private RecyclerView rvConversation;
    private ConversationAdapter conversationAdapter;
    private List<ConversationItem> conversationItems;
    private View waveformView;
    private ImageView ivJarvisLogo;
    private JarvisSpeech speech;

    private BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.jarvis.UI_UPDATE".equals(action)) {
                String type = intent.getStringExtra("type");
                String content = intent.getStringExtra("content");
                runOnUiThread(() -> handleUIUpdate(type, content));
            } else if ("com.jarvis.STATE_CHANGE".equals(action)) {
                String state = intent.getStringExtra("state");
                runOnUiThread(() -> updateStatusDisplay(state));
            } else if ("com.jarvis.AUDIO_LEVEL".equals(action)) {
                float rms = intent.getFloatExtra("rms", 0);
                runOnUiThread(() -> updateAudioLevel(rms));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initConversation();
        checkAndRequestPermissions();
        registerReceivers();

        speech = new JarvisSpeech(this);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvLastCommand = findViewById(R.id.tv_last_command);
        tvLastResponse = findViewById(R.id.tv_last_response);
        rvConversation = findViewById(R.id.rv_conversation);
        ivJarvisLogo = findViewById(R.id.iv_jarvis_logo);

        // Settings button
        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        // Manual activate button
        View btnActivate = findViewById(R.id.btn_activate);
        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> {
                speech.speak("Yes Sir, at your service. What do you require?");
            });
        }
    }

    private void initConversation() {
        conversationItems = new ArrayList<>();
        conversationAdapter = new ConversationAdapter(conversationItems);
        rvConversation.setLayoutManager(new LinearLayoutManager(this));
        rvConversation.setAdapter(conversationAdapter);

        // Welcome message
        addConversation("system", "J.A.R.V.I.S online. Say \"Jarvis\" to activate.");
    }

    private void handleUIUpdate(String type, String content) {
        if ("command".equals(type)) {
            addConversation("user", content);
        } else if ("response".equals(type)) {
            addConversation("jarvis", content);
        }
    }

    private void addConversation(String from, String text) {
        conversationItems.add(new ConversationItem(from, text));
        conversationAdapter.notifyItemInserted(conversationItems.size() - 1);
        rvConversation.scrollToPosition(conversationItems.size() - 1);
    }

    private void updateStatusDisplay(String state) {
        switch (state) {
            case "WAITING_WAKE_WORD":
                tvStatus.setText("Listening for \"Jarvis\"...");
                break;
            case "AWAKE_LISTENING_COMMAND":
                tvStatus.setText("Awaiting your command, Sir...");
                break;
            case "PROCESSING":
                tvStatus.setText("Processing...");
                break;
            default:
                tvStatus.setText("Standing by...");
        }
    }

    private void updateAudioLevel(float rms) {
        // Could animate a waveform here
        float scale = 1f + (rms / 20f);
        ivJarvisLogo.setScaleX(Math.min(scale, 1.3f));
        ivJarvisLogo.setScaleY(Math.min(scale, 1.3f));
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText etApiKey = dialogView.findViewById(R.id.et_api_key);

        SharedPreferences prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
        String savedKey = prefs.getString("api_key", "");
        etApiKey.setText(savedKey);
        etApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this, R.style.JarvisDialogStyle)
            .setTitle("J.A.R.V.I.S Configuration")
            .setMessage("Enter your Anthropic API key to enable AI intelligence:")
            .setView(dialogView)
            .setPositiveButton("Save", (d, w) -> {
                String key = etApiKey.getText().toString().trim();
                prefs.edit().putString("api_key", key).apply();
                Toast.makeText(this, "API key saved, Sir.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Permissions", (d, w) -> checkSpecialPermissions())
            .show();
    }

    private void checkSpecialPermissions() {
        // Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Please grant overlay permission for J.A.R.V.I.S to function fully.")
                .setPositiveButton("Grant", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
        }

        // Accessibility permission
        new AlertDialog.Builder(this)
            .setTitle("Accessibility Service")
            .setMessage("Enable J.A.R.V.I.S Accessibility Service for screen monitoring and enhanced functionality.")
            .setPositiveButton("Open Settings", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("Skip", null)
            .show();
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.jarvis.UI_UPDATE");
        filter.addAction("com.jarvis.STATE_CHANGE");
        filter.addAction("com.jarvis.AUDIO_LEVEL");
        registerReceiver(uiReceiver, filter);
    }

    private void checkAndRequestPermissions() {
        List<String> missing = new ArrayList<>();
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]),
                PERMISSIONS_REQUEST_CODE);
        } else {
            startJarvisService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            startJarvisService();
        }
    }

    private void startJarvisService() {
        Intent serviceIntent = new Intent(this, JarvisService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.d(TAG, "JarvisService started from MainActivity.");

        // Post-launch greeting
        new android.os.Handler().postDelayed(() -> {
            JarvisSpeech s = new JarvisSpeech(this);
            s.greetOnStart();
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(uiReceiver);
        } catch (Exception ignored) {}
    }

    // ─── INNER CLASSES ────────────────────────────────────────────────────────

    public static class ConversationItem {
        public final String from;
        public final String text;
        public ConversationItem(String from, String text) {
            this.from = from;
            this.text = text;
        }
    }
}
