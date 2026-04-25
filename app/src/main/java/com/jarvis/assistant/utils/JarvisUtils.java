package com.jarvis.assistant.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility helpers for device control.
 */
public class JarvisUtils {

    private static final String TAG = "JarvisUtils";
    private static CameraManager cameraManager;
    private static boolean flashlightOn = false;

    // ─── FLASHLIGHT ───────────────────────────────────────────────────────────

    public static void toggleFlashlight(Context context, boolean on) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                String[] cameraIds = cm.getCameraIdList();
                if (cameraIds.length > 0) {
                    cm.setTorchMode(cameraIds[0], on);
                    flashlightOn = on;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Flashlight error: " + e.getMessage());
        }
    }

    // ─── BATTERY ──────────────────────────────────────────────────────────────

    public static int getBatteryLevel(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return -1;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level == -1 || scale == -1) return -1;
        return (int)((level / (float) scale) * 100);
    }

    public static String getBatteryStatus(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return "unknown";
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) return "charging";
        if (status == BatteryManager.BATTERY_STATUS_FULL) return "fully charged";
        return "not charging";
    }

    // ─── APP LAUNCHING ────────────────────────────────────────────────────────

    public static void openAppByName(Context context, String appName) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase();
            if (label.contains(appName.toLowerCase())) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    return;
                }
            }
        }

        // App not found — open search
        Log.w(TAG, "App not found: " + appName);
    }

    public static void openMusicApp(Context context) {
        // Try Spotify, then YouTube Music, then default
        String[] musicApps = {
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
            "com.samsung.android.music"
        };

        PackageManager pm = context.getPackageManager();
        for (String pkg : musicApps) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }

        // Generic media intent
        Intent mediaIntent = new Intent(Intent.ACTION_VIEW);
        mediaIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(mediaIntent);
    }

    // ─── TIME PARSING ─────────────────────────────────────────────────────────

    public static int parseHourFromCommand(String cmd) {
        // Match patterns like "7 AM", "7:30", "19:00", "seven"
        Pattern p12 = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)", Pattern.CASE_INSENSITIVE);
        Matcher m = p12.matcher(cmd);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            String ampm = m.group(3);
            if ("pm".equalsIgnoreCase(ampm) && h < 12) h += 12;
            if ("am".equalsIgnoreCase(ampm) && h == 12) h = 0;
            return h;
        }

        Pattern p24 = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher m2 = p24.matcher(cmd);
        if (m2.find()) {
            return Integer.parseInt(m2.group(1));
        }

        return -1;
    }

    public static int parseMinuteFromCommand(String cmd) {
        Pattern p = Pattern.compile("\\d{1,2}:(\\d{2})");
        Matcher m = p.matcher(cmd);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }
}
