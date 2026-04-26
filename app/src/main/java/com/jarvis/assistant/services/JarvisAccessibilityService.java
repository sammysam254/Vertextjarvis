package com.jarvis.assistant.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JarvisAccessibilityService extends AccessibilityService {

    private static final String TAG = "JarvisAccessibility";
    private BroadcastReceiver screenshotReceiver;
    private Handler handler;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler(Looper.getMainLooper());

        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 300;
            setServiceInfo(info);
            Log.d(TAG, "Accessibility service connected ✓");
        } catch (Exception e) {
            Log.e(TAG, "onServiceConnected: " + e.getMessage());
        }

        // Register screenshot receiver safely
        try {
            screenshotReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    handler.post(() -> takeScreenshotNow());
                }
            };
            IntentFilter filter = new IntentFilter("com.jarvis.TAKE_SCREENSHOT");
            registerReceiver(screenshotReceiver, filter);
            Log.d(TAG, "Screenshot receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Screenshot receiver: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            int type = event.getEventType();
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                CharSequence pkg = event.getPackageName();
                if (pkg != null) {
                    String appPkg = pkg.toString();
                    try {
                        String label = getPackageManager()
                            .getApplicationLabel(getPackageManager()
                            .getApplicationInfo(appPkg, 0)).toString();
                        JarvisCommandProcessor.lastAppName = label;
                    } catch (Exception e) {
                        JarvisCommandProcessor.lastAppName = appPkg;
                    }
                }
            }

            // Capture screen text
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                captureScreenText();
            }
        } catch (Exception e) {
            Log.e(TAG, "onAccessibilityEvent: " + e.getMessage());
        }
    }

    private void captureScreenText() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            StringBuilder sb = new StringBuilder();
            extractText(root, sb, 0);
            String text = sb.toString().trim();
            if (!text.isEmpty()) {
                JarvisCommandProcessor.lastScreenContent =
                    text.length() > 600 ? text.substring(0, 600) + "..." : text;
            }
        } catch (Exception e) {
            Log.e(TAG, "captureScreenText: " + e.getMessage());
        }
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 8) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            if (text != null && text.length() > 0)
                sb.append(text.toString().trim()).append(" ");
            else if (desc != null && desc.length() > 0)
                sb.append(desc.toString().trim()).append(" ");
            for (int i = 0; i < Math.min(node.getChildCount(), 20); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) extractText(child, sb, depth + 1);
            }
        } catch (Exception ignored) {}
    }

    private void takeScreenshotNow() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshot(0, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        try {
                            Bitmap bmp = Bitmap.wrapHardwareBuffer(
                                result.getHardwareBuffer(), null);
                            if (bmp != null) saveScreenshot(bmp);
                            result.getHardwareBuffer().close();
                            broadcast("Screenshot saved to gallery, Sir.");
                        } catch (Exception e) {
                            Log.e(TAG, "Screenshot save: " + e.getMessage());
                            broadcast("Screenshot failed Sir. " + e.getMessage());
                        }
                    }
                    @Override
                    public void onFailure(int code) {
                        Log.e(TAG, "Screenshot failed code=" + code);
                        // Try global action
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                        broadcast("Screenshot taken, Sir.");
                    }
                });
            } else {
                boolean done = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                broadcast(done ? "Screenshot taken, Sir." :
                    "Screenshot not supported on this device Sir.");
            }
        } catch (Exception e) {
            Log.e(TAG, "takeScreenshot: " + e.getMessage());
            try { performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); } catch (Exception ignored) {}
            broadcast("Screenshot attempted, Sir.");
        }
    }

    private void saveScreenshot(Bitmap bmp) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "JARVIS_" + ts + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "saveScreenshot: " + e.getMessage());
        }
    }

    private void broadcast(String msg) {
        try {
            Intent i = new Intent("com.jarvis.UI_UPDATE");
            i.putExtra("type", "response");
            i.putExtra("content", msg);
            sendBroadcast(i);
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (screenshotReceiver != null)
                unregisterReceiver(screenshotReceiver);
        } catch (Exception ignored) {}
    }
}
