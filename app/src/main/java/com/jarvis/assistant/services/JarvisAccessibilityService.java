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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JarvisAccessibilityService extends AccessibilityService {

    private static final String TAG = "JarvisAccessibility";
    private String currentApp = "";
    private BroadcastReceiver screenshotReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags =
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "Accessibility service connected. Screen reading active.");

        // Register screenshot receiver
        screenshotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                takeScreenshotWithService();
            }
        };
        IntentFilter filter = new IntentFilter("com.jarvis.TAKE_SCREENSHOT");
        registerReceiver(screenshotReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            int type = event.getEventType();

            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                CharSequence pkg = event.getPackageName();
                if (pkg != null) {
                    currentApp = pkg.toString();
                    JarvisCommandProcessor.lastAppName = getFriendlyAppName(currentApp);
                }
            }

            // Capture screen content on any window change
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                captureScreenText();
            }

        } catch (Exception e) {
            Log.e(TAG, "Event error: " + e.getMessage());
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
                JarvisCommandProcessor.lastScreenContent = text.length() > 500
                    ? text.substring(0, 500) + "..." : text;
            }
        } catch (Exception e) {
            Log.e(TAG, "captureScreenText: " + e.getMessage());
        }
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 10) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            if (text != null && text.length() > 0) sb.append(text).append(" ");
            else if (desc != null && desc.length() > 0) sb.append(desc).append(" ");
            for (int i = 0; i < node.getChildCount(); i++) {
                extractText(node.getChild(i), sb, depth + 1);
            }
        } catch (Exception ignored) {}
    }

    private void takeScreenshotWithService() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+ — use built-in takeScreenshot
                takeScreenshot(0, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        try {
                            Bitmap bmp = Bitmap.wrapHardwareBuffer(
                                result.getHardwareBuffer(), null);
                            saveScreenshot(bmp);
                            result.getHardwareBuffer().close();
                            broadcastResult("Screenshot saved to gallery, Sir.");
                        } catch (Exception e) {
                            Log.e(TAG, "Screenshot save: " + e.getMessage());
                        }
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        Log.e(TAG, "Screenshot failed: " + errorCode);
                        broadcastResult("I was unable to take a screenshot Sir. Error " + errorCode);
                    }
                });
            } else {
                // Older API — simulate key combo
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                broadcastResult("Screenshot taken, Sir.");
            }
        } catch (Exception e) {
            Log.e(TAG, "takeScreenshot: " + e.getMessage());
            // Try global action as fallback
            try {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                broadcastResult("Screenshot taken, Sir.");
            } catch (Exception ex) { Log.e(TAG, ex.getMessage()); }
        }
    }

    private void saveScreenshot(Bitmap bitmap) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File file = new File(dir, "JARVIS_" + ts + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "saveScreenshot: " + e.getMessage());
        }
    }

    private void broadcastResult(String msg) {
        Intent i = new Intent("com.jarvis.UI_UPDATE");
        i.putExtra("type", "response");
        i.putExtra("content", msg);
        sendBroadcast(i);
    }

    private String getFriendlyAppName(String pkg) {
        try {
            return getPackageManager()
                .getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    @Override
    public void onInterrupt() { Log.d(TAG, "Interrupted."); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (screenshotReceiver != null) unregisterReceiver(screenshotReceiver); }
        catch (Exception ignored) {}
    }
}
