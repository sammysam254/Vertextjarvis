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
        String cmd = command.toLowerCase().trim();
        // Remove wake word if present
        cmd = cmd.replaceAll("^(jarvis|travis|davis|jervis)\\s*", "").trim();
        if (cmd.isEmpty()) return;

        Log.d(TAG, "Processing: " + cmd);
        broadcastUI("command", cmd);

        if (has(cmd, "time", "what time")) { handleTime(); }
        else if (has(cmd, "date", "day", "today")) { handleDate(); }
        else if (has(cmd, "call", "ring", "dial", "phone")) { handleCall(cmd); }
        else if (has(cmd, "text", "sms", "send message")) { handleSMS(cmd); }
        else if (has(cmd, "wifi", "wi-fi")) { handleWifi(cmd); }
        else if (has(cmd, "bluetooth")) { handleBluetooth(cmd); }
        else if (has(cmd, "volume", "louder", "quieter", "mute", "unmute", "silent")) { handleVolume(cmd); }
        else if (has(cmd, "flashlight", "torch", "light")) { handleFlashlight(cmd); }
        else if (has(cmd, "alarm", "wake me", "remind me")) { handleAlarm(cmd); }
        else if (has(cmd, "screenshot", "capture screen")) { handleScreenshot(); }
        else if (has(cmd, "read screen", "what on screen", "what's on screen")) { handleReadScreen(); }
        else if (has(cmd, "open", "launch", "start")) { handleOpen(cmd); }
        else if (has(cmd, "navigate", "directions", "take me to", "go to")) { handleNavigate(cmd); }
        else if (has(cmd, "search", "google", "look up")) { handleSearch(cmd); }
        else if (has(cmd, "play music", "play song", "music", "spotify", "youtube music")) { handleMusic(); }
        else if (has(cmd, "battery", "charge")) { handleBattery(); }
        else if (has(cmd, "settings")) { handleSettings(); }
        else if (has(cmd, "stop", "quiet", "shut up", "enough")) { speech.stop(); }
        else if (has(cmd, "goodbye", "bye", "sleep", "dismissed")) { handleDismiss(); }
        else if (has(cmd, "who are you", "what are you")) { handleIntro(); }
        else { handleAI(cmd); }
    }

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

    private void handleCall(String cmd) {
        String target = cmd.replaceAll("(call|phone|ring|dial|please)", "").trim();
        if (target.isEmpty()) { respond("Who shall I call Sir?"); return; }
        respond("Calling " + target + " now Sir.");
        handler.postDelayed(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_CALL);
                i.setData(Uri.parse("tel:" + Uri.encode(target)));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) { respond("Unable to place that call Sir."); }
        }, 1000);
    }

    private void handleSMS(String cmd) {
        respond("Opening messages Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 800);
    }

    private void handleWifi(String cmd) {
        respond("Opening WiFi settings Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 800);
    }

    private void handleBluetooth(String cmd) {
        respond("Opening Bluetooth settings Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 800);
    }

    private void handleVolume(String cmd) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        if (has(cmd, "mute", "silent", "silence")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_SILENT); } catch (Exception e) {}
            respond("Device silenced Sir.");
        } else if (has(cmd, "unmute", "normal")) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL); } catch (Exception e) {}
            respond("Sound restored Sir.");
        } else if (has(cmd, "max", "full", "maximum")) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            respond("Volume at maximum Sir.");
        } else if (has(cmd, "up", "raise", "higher", "louder")) {
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur + 2), 0);
            respond("Volume raised Sir.");
        } else if (has(cmd, "down", "lower", "quieter")) {
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, cur - 2), 0);
            respond("Volume lowered Sir.");
        }
    }

    private void handleFlashlight(String cmd) {
        boolean on = !has(cmd, "off", "disable", "turn off");
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
            }, 1200);
        } else {
            respond("Opening alarms Sir.");
            handler.postDelayed(() -> {
                Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }, 800);
        }
    }

    private void handleScreenshot() {
        respond("Taking screenshot Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent("com.jarvis.TAKE_SCREENSHOT");
            context.sendBroadcast(i);
        }, 500);
    }

    private void handleReadScreen() {
        if (lastScreenContent != null && !lastScreenContent.isEmpty()) {
            respond("On your screen Sir: " + lastScreenContent.substring(0, Math.min(200, lastScreenContent.length())));
        } else {
            respond("I cannot read the screen without accessibility permission Sir.");
        }
    }

    private void handleOpen(String cmd) {
        // Extract app name
        String appName = cmd
            .replaceAll("\\b(open|launch|start|please|the|app)\\b", "")
            .trim();
        if (appName.isEmpty()) { respond("Which app Sir?"); return; }

        Log.d(TAG, "Opening app: " + appName);
        respond("Opening " + appName + " Sir.");

        handler.postDelayed(() -> {
            // Try to find and open the app
            if (!openAppByName(appName)) {
                // Try common app package names
                if (!tryKnownApp(appName)) {
                    respond("I could not find " + appName + " Sir. Please check if it is installed.");
                }
            }
        }, 800);
    }

    private boolean openAppByName(String name) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            String nameLower = name.toLowerCase();

            for (ApplicationInfo app : apps) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase();
                if (label.contains(nameLower) || nameLower.contains(label)) {
                    Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                    if (launch != null) {
                        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launch);
                        Log.d(TAG, "Opened: " + app.packageName);
                        return true;
                    }
                }
            }
        } catch (Exception e) { Log.e(TAG, "openAppByName: " + e.getMessage()); }
        return false;
    }

    private boolean tryKnownApp(String name) {
        String n = name.toLowerCase();
        String pkg = null;

        if (n.contains("whatsapp") || n.contains("whats")) pkg = "com.whatsapp";
        else if (n.contains("facebook") || n.contains("fb")) pkg = "com.facebook.katana";
        else if (n.contains("instagram") || n.contains("insta")) pkg = "com.instagram.android";
        else if (n.contains("twitter") || n.contains("x app")) pkg = "com.twitter.android";
        else if (n.contains("youtube")) pkg = "com.google.android.youtube";
        else if (n.contains("gmail") || n.contains("email")) pkg = "com.google.android.gm";
        else if (n.contains("maps") || n.contains("google maps")) pkg = "com.google.android.apps.maps";
        else if (n.contains("chrome") || n.contains("browser")) pkg = "com.android.chrome";
        else if (n.contains("camera")) pkg = "android.media.action.IMAGE_CAPTURE";
        else if (n.contains("gallery") || n.contains("photos")) pkg = "com.google.android.apps.photos";
        else if (n.contains("spotify")) pkg = "com.spotify.music";
        else if (n.contains("telegram")) pkg = "org.telegram.messenger";
        else if (n.contains("tiktok")) pkg = "com.zhiliaoapp.musically";
        else if (n.contains("netflix")) pkg = "com.netflix.mediaclient";
        else if (n.contains("calculator") || n.contains("calc")) pkg = "com.android.calculator2";
        else if (n.contains("clock") || n.contains("alarm")) pkg = "com.android.deskclock";
        else if (n.contains("contacts")) pkg = "com.android.contacts";
        else if (n.contains("calendar")) pkg = "com.android.calendar";
        else if (n.contains("settings")) pkg = Settings.ACTION_SETTINGS;
        else if (n.contains("play store") || n.contains("play")) pkg = "com.android.vending";
        else if (n.contains("messages") || n.contains("sms")) pkg = "com.android.mms";

        if (pkg == null) return false;

        try {
            Intent i;
            if (pkg.equals(Settings.ACTION_SETTINGS)) {
                i = new Intent(Settings.ACTION_SETTINGS);
            } else if (pkg.equals("android.media.action.IMAGE_CAPTURE")) {
                i = new Intent(pkg);
            } else {
                i = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) return false;
            }
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "tryKnownApp: " + e.getMessage());
            return false;
        }
    }

    private void handleNavigate(String cmd) {
        String dest = cmd.replaceAll(
            "\\b(navigate|navigation|directions|take me to|get to|go to|please)\\b", "").trim();
        if (dest.isEmpty()) { respond("Where to Sir?"); return; }
        respond("Navigating to " + dest + " Sir.");
        handler.postDelayed(() -> {
            try {
                Uri uri = Uri.parse("google.navigation:q=" + Uri.encode(dest));
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception e) {
                try {
                    Uri uri = Uri.parse("https://maps.google.com/?q=" + Uri.encode(dest));
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                } catch (Exception ex) { Log.e(TAG, ex.getMessage()); }
            }
        }, 800);
    }

    private void handleSearch(String cmd) {
        String q = cmd.replaceAll("\\b(search|google|look up|find|for|please)\\b", "").trim();
        if (q.isEmpty()) { respond("What shall I search Sir?"); return; }
        respond("Searching for " + q + " Sir.");
        handler.postDelayed(() -> {
            Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q));
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 800);
    }

    private void handleMusic() {
        respond("Opening music Sir.");
        handler.postDelayed(() -> JarvisUtils.openMusicApp(context), 800);
    }

    private void handleBattery() {
        int level = JarvisUtils.getBatteryLevel(context);
        String status = JarvisUtils.getBatteryStatus(context);
        respond("Battery is at " + level + " percent, " + status + " Sir.");
    }

    private void handleSettings() {
        respond("Opening settings Sir.");
        handler.postDelayed(() -> {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }, 800);
    }

    private void handleDismiss() {
        String[] msgs = {"Understood Sir.", "Very well Sir.", "Standing by Sir.", "Of course Sir."};
        respond(msgs[(int)(Math.random() * msgs.length)]);
    }

    private void handleIntro() {
        respond("I am Jarvis, your personal AI assistant Sir. Always at your service.");
    }

    private void handleAI(String cmd) {
        String context_info = "";
        if (lastScreenContent != null && !lastScreenContent.isEmpty()) {
            context_info = " [Screen: " + lastScreenContent.substring(0, Math.min(100, lastScreenContent.length())) + "]";
        }
        final String query = cmd + context_info;
        respond("One moment Sir.");
        ai.queryAndSpeak(query, speech, text -> broadcastUI("response", text));
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
