package com.jarvis.assistant.services;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Log;
import com.jarvis.assistant.utils.JarvisAI;
import com.jarvis.assistant.utils.JarvisSpeech;
import com.jarvis.assistant.utils.JarvisUtils;
import java.util.Calendar;
import java.util.List;

public class JarvisCommandProcessor {

    private static final String TAG = "CommandProcessor";
    private final Context context;
    private final JarvisSpeech speech;
    private final JarvisAI ai;
    private final Handler handler;

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
        String cmd = command.toLowerCase().trim()
            .replaceAll("^(jarvis|travis|davis|jervis)\\s*", "").trim();
        if (cmd.isEmpty()) return;

        Log.d(TAG, "Command: " + cmd);
        broadcastUI("command", cmd);

        // Route command
        if (has(cmd, "time", "what time")) { handleTime(); }
        else if (has(cmd, "date", "day", "today")) { handleDate(); }
        else if (has(cmd, "call", "ring", "dial", "phone")) { handleCall(cmd); }
        else if (has(cmd, "text", "sms", "send message")) { handleSMS(); }
        else if (has(cmd, "wifi", "wi-fi")) { handleWifi(); }
        else if (has(cmd, "bluetooth")) { handleBluetooth(); }
        else if (has(cmd, "volume", "louder", "quieter", "mute", "unmute", "silent")) { handleVolume(cmd); }
        else if (has(cmd, "flashlight", "torch")) { handleFlashlight(cmd); }
        else if (has(cmd, "alarm", "wake me", "remind me")) { handleAlarm(cmd); }
        else if (has(cmd, "screenshot", "capture screen")) { handleScreenshot(); }
        else if (has(cmd, "read screen", "what on screen", "what's on screen")) { handleReadScreen(); }
        else if (has(cmd, "open", "launch", "start")) { handleOpen(cmd); }
        else if (has(cmd, "navigate", "directions", "take me to", "go to")) { handleNavigate(cmd); }
        else if (has(cmd, "search", "google", "look up")) { handleSearch(cmd); }
        else if (has(cmd, "music", "spotify", "play")) { handleMusic(); }
        else if (has(cmd, "battery", "charge")) { handleBattery(); }
        else if (has(cmd, "settings")) { handleSettings(); }
        else if (has(cmd, "stop", "quiet", "shut up")) { speech.stop(); }
        else if (has(cmd, "goodbye", "bye", "dismissed")) { handleDismiss(); }
        else if (has(cmd, "who are you", "what are you")) { handleIntro(); }
        else { handleAI(cmd); }
    }

    // ── INSTANT HANDLERS (no network needed) ──────────────────────────────────

    private void handleTime() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        String ap = h >= 12 ? "PM" : "AM";
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        respond(String.format("It is %d:%02d %s Sir.", h12, m, ap));
    }

    private void handleDate() {
        Calendar c = Calendar.getInstance();
        String[] days = {"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
        String[] months = {"January","February","March","April","May","June",
            "July","August","September","October","November","December"};
        respond(String.format("Today is %s %s %d Sir.",
            days[c.get(Calendar.DAY_OF_WEEK)-1],
            months[c.get(Calendar.MONTH)],
            c.get(Calendar.DAY_OF_MONTH)));
    }

    private void handleBattery() {
        int level = JarvisUtils.getBatteryLevel(context);
        String status = JarvisUtils.getBatteryStatus(context);
        respond("Battery is at " + level + " percent, " + status + " Sir.");
    }

    private void handleDismiss() {
        String[] msgs = {"Understood Sir.", "Very well Sir.", "Standing by Sir."};
        respond(msgs[(int)(Math.random() * msgs.length)]);
    }

    private void handleIntro() {
        respond("I am Jarvis, your personal AI assistant Sir. Always at your service.");
    }

    // ── DEVICE ACTIONS (instant respond + execute) ────────────────────────────

    private void handleCall(String cmd) {
        String target = cmd.replaceAll("\\b(call|phone|ring|dial|please)\\b", "").trim();
        if (target.isEmpty()) { respond("Who shall I call Sir?"); return; }
        respond("Calling " + target + " Sir.");
        handler.postDelayed(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_CALL);
                i.setData(Uri.parse("tel:" + Uri.encode(target)));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1500);
    }

    private void handleSMS() {
        respond("Opening messages Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1500);
    }

    private void handleWifi() {
        respond("Opening WiFi Sir.");
        launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void handleBluetooth() {
        respond("Opening Bluetooth Sir.");
        launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void handleVolume(String cmd) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        if (has(cmd, "mute", "silent")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_SILENT); } catch (Exception e) {}
            respond("Silenced Sir.");
        } else if (has(cmd, "unmute")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL); } catch (Exception e) {}
            respond("Sound on Sir.");
        } else if (has(cmd, "max", "full")) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            respond("Maximum volume Sir.");
        } else if (has(cmd, "up", "raise", "louder")) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC) + 2), 0);
            respond("Volume up Sir.");
        } else if (has(cmd, "down", "lower", "quieter")) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.max(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC) - 2), 0);
            respond("Volume down Sir.");
        }
    }

    private void handleFlashlight(String cmd) {
        boolean on = !has(cmd, "off", "disable");
        JarvisUtils.toggleFlashlight(context, on);
        respond(on ? "Torch on Sir." : "Torch off Sir.");
    }

    private void handleAlarm(String cmd) {
        int hour = JarvisUtils.parseHourFromCommand(cmd);
        int min = JarvisUtils.parseMinuteFromCommand(cmd);
        if (hour >= 0) {
            int h12 = hour % 12; if (h12 == 0) h12 = 12;
            respond(String.format("Alarm set for %d:%02d %s Sir.",
                h12, min, hour >= 12 ? "PM" : "AM"));
            handler.postDelayed(() -> {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                i.putExtra(AlarmClock.EXTRA_MINUTES, min);
                i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }, 1500);
        } else {
            respond("Opening alarms Sir.");
            launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        }
    }

    private void handleScreenshot() {
        respond("Screenshot taken Sir.");
        handler.postDelayed(() ->
            context.sendBroadcast(new Intent("com.jarvis.TAKE_SCREENSHOT")), 500);
    }

    private void handleReadScreen() {
        if (lastScreenContent != null && !lastScreenContent.isEmpty())
            respond("On screen: " + lastScreenContent.substring(0, Math.min(150, lastScreenContent.length())));
        else
            respond("Accessibility service needed to read screen Sir.");
    }

    private void handleOpen(String cmd) {
        String appName = cmd.replaceAll("\\b(open|launch|start|please|the|app)\\b", "").trim();
        if (appName.isEmpty()) { respond("Which app Sir?"); return; }
        respond("Opening " + appName + " Sir.");
        handler.postDelayed(() -> {
            if (!openByLabel(appName) && !openByPackage(appName)) {
                // Try play store search as last resort
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://search?q=" + Uri.encode(appName)));
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }
        }, 800);
    }

    private boolean openByLabel(String name) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            String n = name.toLowerCase();
            for (ApplicationInfo app : apps) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase();
                if (label.contains(n) || n.contains(label)) {
                    Intent i = pm.getLaunchIntentForPackage(app.packageName);
                    if (i != null) {
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        return true;
                    }
                }
            }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        return false;
    }

    private boolean openByPackage(String name) {
        String n = name.toLowerCase();
        String pkg = null;
        if (n.contains("whatsapp")) pkg = "com.whatsapp";
        else if (n.contains("facebook")) pkg = "com.facebook.katana";
        else if (n.contains("instagram")) pkg = "com.instagram.android";
        else if (n.contains("twitter") || n.contains("x ")) pkg = "com.twitter.android";
        else if (n.contains("youtube")) pkg = "com.google.android.youtube";
        else if (n.contains("gmail")) pkg = "com.google.android.gm";
        else if (n.contains("maps")) pkg = "com.google.android.apps.maps";
        else if (n.contains("chrome")) pkg = "com.android.chrome";
        else if (n.contains("spotify")) pkg = "com.spotify.music";
        else if (n.contains("telegram")) pkg = "org.telegram.messenger";
        else if (n.contains("tiktok")) pkg = "com.zhiliaoapp.musically";
        else if (n.contains("netflix")) pkg = "com.netflix.mediaclient";
        else if (n.contains("calculator")) pkg = "com.android.calculator2";
        else if (n.contains("camera")) pkg = "android.media.action.STILL_IMAGE_CAMERA";
        else if (n.contains("gallery") || n.contains("photos")) pkg = "com.google.android.apps.photos";
        else if (n.contains("play store")) pkg = "com.android.vending";
        else if (n.contains("messages") || n.contains("sms")) pkg = "com.google.android.apps.messaging";
        else if (n.contains("clock") || n.contains("alarm")) pkg = "com.android.deskclock";
        else if (n.contains("contacts")) pkg = "com.android.contacts";

        if (pkg == null) return false;
        try {
            Intent i = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) i = new Intent(pkg);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) { return false; }
    }

    private void handleNavigate(String cmd) {
        String dest = cmd.replaceAll("\\b(navigate|directions|take me to|get to|go to|please)\\b","").trim();
        if (dest.isEmpty()) { respond("Where to Sir?"); return; }
        respond("Navigating to " + dest + " Sir.");
        handler.postDelayed(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=" + Uri.encode(dest)));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://maps.google.com/?q=" + Uri.encode(dest)));
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                } catch (Exception ex) { Log.e(TAG, ex.getMessage()); }
            }
        }, 1000);
    }

    private void handleSearch(String cmd) {
        String q = cmd.replaceAll("\\b(search|google|look up|find|for|please)\\b","").trim();
        if (q.isEmpty()) { respond("What shall I search Sir?"); return; }
        respond("Searching for " + q + " Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1000);
    }

    private void handleMusic() {
        respond("Opening music Sir.");
        handler.postDelayed(() -> JarvisUtils.openMusicApp(context), 800);
    }

    private void handleSettings() {
        respond("Opening settings Sir.");
        launch(new Intent(Settings.ACTION_SETTINGS));
    }

    // ── AI (with instant ack) ─────────────────────────────────────────────────

    private void handleAI(String cmd) {
        // Immediately respond with cached "One moment Sir." while fetching
        speech.speak("One moment Sir.");
        broadcastUI("response", "Processing...");

        String query = cmd;
        if (lastScreenContent != null && !lastScreenContent.isEmpty())
            query += " [Screen: " + lastScreenContent.substring(0, Math.min(80, lastScreenContent.length())) + "]";

        final String finalQuery = query;

        // Fetch AI response while greeting plays
        ai.query(finalQuery, text -> {
            broadcastUI("response", text);
            // Wait for "One moment" to finish then speak answer
            handler.postDelayed(() -> speech.speak(text), 1000);
        });
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void launch(Intent intent) {
        handler.postDelayed(() -> {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(intent); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 1200);
    }

    private void respond(String text) {
        Log.d(TAG, "JARVIS: " + text);
        broadcastUI("response", text);
        speech.speak(text);
    }

    private void broadcastUI(String type, String content) {
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
