package com.jarvis.assistant.services;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Log;
import com.jarvis.assistant.utils.JarvisAI;
import com.jarvis.assistant.utils.JarvisSpeech;
import com.jarvis.assistant.utils.JarvisUtils;
import java.util.Calendar;

public class JarvisCommandProcessor {

    private static final String TAG = "CommandProcessor";
    private final Context context;
    private final JarvisSpeech speech;
    private final JarvisAI ai;
    private final Handler handler;

    // Last known screen content from accessibility service
    public static String lastScreenContent = "";
    public static String lastAppName = "";

    public JarvisCommandProcessor(Context context) {
        this.context = context;
        this.speech = new JarvisSpeech(context);
        this.ai = new JarvisAI(context);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void processCommand(String command) {
        if (command == null || command.trim().isEmpty()) return;
        String cmd = command.toLowerCase().trim();
        Log.d(TAG, "Command: " + cmd);
        broadcastToUI("command", cmd);

        // ── TIME & DATE ──────────────────────────────────────────────────────
        if (has(cmd, "time")) { handleTime(); }
        else if (has(cmd, "date", "day", "today")) { handleDate(); }

        // ── CALLS ────────────────────────────────────────────────────────────
        else if (has(cmd, "call", "ring", "dial", "phone")) { handleCall(cmd); }

        // ── MESSAGES ─────────────────────────────────────────────────────────
        else if (has(cmd, "text", "sms", "message", "send message")) { handleSMS(cmd); }

        // ── WIFI ──────────────────────────────────────────────────────────────
        else if (has(cmd, "wifi", "wi-fi", "internet")) { handleWifi(cmd); }

        // ── BLUETOOTH ────────────────────────────────────────────────────────
        else if (has(cmd, "bluetooth")) { handleBluetooth(cmd); }

        // ── VOLUME ───────────────────────────────────────────────────────────
        else if (has(cmd, "volume", "louder", "quieter", "mute", "silent", "unmute")) { handleVolume(cmd); }

        // ── FLASHLIGHT ───────────────────────────────────────────────────────
        else if (has(cmd, "flashlight", "torch", "light")) { handleFlashlight(cmd); }

        // ── ALARM ────────────────────────────────────────────────────────────
        else if (has(cmd, "alarm", "wake me", "remind me", "set alarm")) { handleAlarm(cmd); }

        // ── SCREENSHOT ───────────────────────────────────────────────────────
        else if (has(cmd, "screenshot", "capture screen", "take screenshot")) { handleScreenshot(); }

        // ── READ SCREEN ──────────────────────────────────────────────────────
        else if (has(cmd, "read screen", "what's on screen", "what is on screen",
                "read this", "what does it say", "read page")) { handleReadScreen(); }

        // ── OPEN APP ─────────────────────────────────────────────────────────
        else if (has(cmd, "open", "launch", "start")) { handleOpenApp(cmd); }

        // ── NAVIGATE ─────────────────────────────────────────────────────────
        else if (has(cmd, "navigate", "directions", "take me to", "get to", "go to")) { handleNavigation(cmd); }

        // ── SEARCH ───────────────────────────────────────────────────────────
        else if (has(cmd, "search", "google", "look up", "find")) { handleSearch(cmd); }

        // ── MUSIC ────────────────────────────────────────────────────────────
        else if (has(cmd, "play music", "play song", "music")) { handleMusic(); }

        // ── BATTERY ──────────────────────────────────────────────────────────
        else if (has(cmd, "battery", "charge", "power")) { handleBattery(); }

        // ── SETTINGS ─────────────────────────────────────────────────────────
        else if (has(cmd, "settings", "open settings")) { handleSettings(); }

        // ── DISMISS ──────────────────────────────────────────────────────────
        else if (has(cmd, "goodbye", "bye", "sleep", "dismissed", "that will be all",
                "stand down")) { handleDismiss(); }

        // ── SELF ─────────────────────────────────────────────────────────────
        else if (has(cmd, "who are you", "what are you", "introduce")) { handleIntro(); }

        // ── AI FALLBACK ──────────────────────────────────────────────────────
        else { handleAI(cmd); }
    }

    // ── HANDLERS ─────────────────────────────────────────────────────────────

    private void handleTime() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        String ap = h >= 12 ? "PM" : "AM";
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        respond(String.format("It is %d:%02d %s, Sir.", h12, m, ap));
    }

    private void handleDate() {
        Calendar c = Calendar.getInstance();
        String[] days = {"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
        String[] months = {"January","February","March","April","May","June",
            "July","August","September","October","November","December"};
        respond(String.format("Today is %s, %s %d %d, Sir.",
            days[c.get(Calendar.DAY_OF_WEEK)-1],
            months[c.get(Calendar.MONTH)],
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.YEAR)));
    }

    private void handleCall(String cmd) {
        String target = cmd.replaceAll("(call|phone|ring|dial|please|jarvis)", "").trim();
        if (target.isEmpty()) { respond("Who would you like me to call, Sir?"); return; }
        respond("Placing a call to " + target + ", Sir.");
        handler.postDelayed(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_CALL);
                i.setData(Uri.parse("tel:" + Uri.encode(target)));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) {
                respond("I was unable to place that call Sir. Please check contacts.");
            }
        }, 1500);
    }

    private void handleSMS(String cmd) {
        respond("Opening messaging for you, Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("smsto:"));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleWifi(String cmd) {
        respond(has(cmd, "off", "disable") ? "Opening Wi-Fi settings Sir." : "Opening Wi-Fi settings Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleBluetooth(String cmd) {
        if (has(cmd, "off", "disable", "turn off")) {
            try {
                BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if (bt != null) bt.disable();
                respond("Bluetooth disabled, Sir.");
            } catch (Exception e) {
                respond("Opening Bluetooth settings, Sir.");
                openBluetoothSettings();
            }
        } else {
            respond("Opening Bluetooth settings, Sir.");
            openBluetoothSettings();
        }
    }

    private void openBluetoothSettings() {
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleVolume(String cmd) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        if (has(cmd, "mute", "silent", "silence")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_SILENT); } catch (Exception e) {}
            respond("Device silenced, Sir.");
        } else if (has(cmd, "unmute", "normal", "ring")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL); } catch (Exception e) {}
            respond("Sound restored, Sir.");
        } else if (has(cmd, "max", "full", "maximum")) {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
            respond("Volume set to maximum, Sir.");
        } else if (has(cmd, "up", "raise", "higher", "louder", "increase")) {
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(max, cur + 2), 0);
            respond("Volume raised, Sir.");
        } else if (has(cmd, "down", "lower", "quieter", "decrease")) {
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, cur - 2), 0);
            respond("Volume lowered, Sir.");
        }
    }

    private void handleFlashlight(String cmd) {
        boolean on = !has(cmd, "off", "disable", "turn off");
        JarvisUtils.toggleFlashlight(context, on);
        respond(on ? "Torch activated, Sir." : "Torch deactivated, Sir.");
    }

    private void handleAlarm(String cmd) {
        int hour = JarvisUtils.parseHourFromCommand(cmd);
        int minute = JarvisUtils.parseMinuteFromCommand(cmd);
        if (hour >= 0) {
            int h12 = hour % 12; if (h12 == 0) h12 = 12;
            String ap = hour >= 12 ? "PM" : "AM";
            respond(String.format("Setting alarm for %d:%02d %s, Sir.", h12, minute, ap));
            handler.postDelayed(() -> {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                i.putExtra(AlarmClock.EXTRA_MESSAGE, "J.A.R.V.I.S Alarm");
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }, 1500);
        } else {
            respond("Opening alarm clock, Sir.");
            handler.postDelayed(() -> {
                Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }, 1000);
        }
    }

    private void handleScreenshot() {
        respond("Taking a screenshot now, Sir.");
        handler.postDelayed(() -> {
            try {
                // Use accessibility service to take screenshot (API 28+)
                Intent i = new Intent("com.jarvis.TAKE_SCREENSHOT");
                context.sendBroadcast(i);
                // Also try the system screenshot
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_SYSRQ"});
            } catch (Exception e) {
                Log.e(TAG, "Screenshot: " + e.getMessage());
            }
        }, 500);
    }

    private void handleReadScreen() {
        if (lastScreenContent != null && !lastScreenContent.isEmpty()) {
            respond("Here is what I can see on your screen, Sir. " + lastScreenContent);
        } else {
            respond("I can see you are" +
                (lastAppName.isEmpty() ? " on your device" : " using " + lastAppName) +
                " Sir. Enable accessibility service for me to read screen content.");
        }
    }

    private void handleOpenApp(String cmd) {
        String appName = cmd.replaceAll("(open|launch|start|app|please|jarvis|the)", "").trim();
        if (appName.isEmpty()) { respond("Which application, Sir?"); return; }
        respond("Opening " + appName + " for you, Sir.");
        handler.postDelayed(() -> JarvisUtils.openAppByName(context, appName), 1000);
    }

    private void handleNavigation(String cmd) {
        String dest = cmd.replaceAll(
            "(navigate|navigation|directions|take me to|get to|go to|jarvis|please)", "").trim();
        if (dest.isEmpty()) { respond("Where would you like to go, Sir?"); return; }
        respond("Opening navigation to " + dest + ", Sir.");
        handler.postDelayed(() -> {
            Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(dest));
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) {
                // fallback to maps
                Uri mapsUri = Uri.parse("https://maps.google.com/?q=" + Uri.encode(dest));
                Intent maps = new Intent(Intent.ACTION_VIEW, mapsUri);
                maps.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(maps); } catch (Exception ex) { Log.e(TAG, ex.getMessage()); }
            }
        }, 1000);
    }

    private void handleSearch(String cmd) {
        String query = cmd.replaceAll("(search|google|look up|find|for|please|jarvis)", "").trim();
        if (query.isEmpty()) { respond("What shall I search for, Sir?"); return; }
        respond("Searching for " + query + ", Sir.");
        handler.postDelayed(() -> {
            Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query));
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleMusic() {
        respond("Opening music for you, Sir.");
        handler.postDelayed(() -> JarvisUtils.openMusicApp(context), 1000);
    }

    private void handleBattery() {
        int level = JarvisUtils.getBatteryLevel(context);
        String status = JarvisUtils.getBatteryStatus(context);
        respond("Battery is at " + level + " percent and " + status + ", Sir.");
    }

    private void handleSettings() {
        respond("Opening device settings, Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleDismiss() {
        String[] msgs = {
            "Understood Sir. Standing by.",
            "Very well Sir. I shall remain on standby.",
            "Of course Sir. Good day.",
            "As you wish Sir.",
            "Thank you Sir. J.A.R.V.I.S signing off for now."
        };
        respond(msgs[(int)(Math.random() * msgs.length)]);
    }

    private void handleIntro() {
        respond("I am J.A.R.V.I.S — your personal AI assistant Sir. " +
            "I run continuously in the background, always ready. " +
            "Say Jarvis followed by any command and I shall execute it immediately.");
    }

    private void handleAI(String cmd) {
        // Include screen context if available
        String fullQuery = cmd;
        if (!lastScreenContent.isEmpty()) {
            fullQuery = cmd + "\n\n[Screen context: " + lastScreenContent + "]";
        }
        final String q = fullQuery;
        // Using backend speak
        ai.query(q, response -> {
            broadcastToUI("response", response);
            speech.speak(response);
        });
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private void respond(String text) {
        Log.d(TAG, "JARVIS: " + text);
        broadcastToUI("response", text);
        speech.speak(text);
    }

    private void broadcastToUI(String type, String content) {
        Intent i = new Intent("com.jarvis.UI_UPDATE");
        i.putExtra("type", type);
        i.putExtra("content", content);
        context.sendBroadcast(i);
    }

    private boolean has(String text, String... keywords) {
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }
}
