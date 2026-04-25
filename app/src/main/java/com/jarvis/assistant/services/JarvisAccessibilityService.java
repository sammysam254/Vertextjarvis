package com.jarvis.assistant.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * JarvisAccessibilityService - Monitors screen activity intelligently.
 * Tracks which app is active and provides contextual assistance.
 */
public class JarvisAccessibilityService extends AccessibilityService {

    private static final String TAG = "JarvisAccessibility";
    private String currentApp = "";
    private long lastEventTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();
        long now = System.currentTimeMillis();

        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // App switch or screen change
                CharSequence pkg = event.getPackageName();
                CharSequence className = event.getClassName();
                if (pkg != null) {
                    String newApp = pkg.toString();
                    if (!newApp.equals(currentApp)) {
                        onAppChanged(currentApp, newApp);
                        currentApp = newApp;
                    }
                }
                break;

            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                // User typing — can assist contextually
                if (now - lastEventTime > 2000) { // throttle
                    lastEventTime = now;
                    broadcastActivity("typing", currentApp);
                }
                break;

            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                CharSequence text = event.getText().isEmpty() ? null : event.getText().get(0);
                if (text != null) {
                    broadcastActivity("notification", text.toString());
                }
                break;
        }
    }

    private void onAppChanged(String from, String to) {
        Log.d(TAG, "App switched: " + from + " → " + to);
        broadcastActivity("app_switch", to);

        // Provide friendly context notifications for certain apps
        // (Could be used to offer assistance)
    }

    private void broadcastActivity(String type, String data) {
        Intent intent = new Intent("com.jarvis.SCREEN_ACTIVITY");
        intent.putExtra("type", type);
        intent.putExtra("data", data);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "Accessibility service connected. Screen monitoring active.");
    }
}
