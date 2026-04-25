package com.jarvis.assistant.services;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Monitors all incoming notifications.
 * Jarvis can read them aloud or act on them.
 */
public class JarvisNotificationListener extends NotificationListenerService {

    private static final String TAG = "JarvisNotifListener";

    // High-priority packages to monitor and potentially announce
    private static final String[] IMPORTANT_APPS = {
        "com.whatsapp", "com.facebook.messenger",
        "com.google.android.gm", "com.samsung.android.messaging"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        android.app.Notification notif = sbn.getNotification();

        // Broadcast to main service for potential announcement
        Intent intent = new Intent("com.jarvis.NOTIFICATION_RECEIVED");
        intent.putExtra("package", pkg);
        if (notif.extras != null) {
            CharSequence title = notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
            CharSequence text = notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
            intent.putExtra("title", title != null ? title.toString() : "");
            intent.putExtra("text", text != null ? text.toString() : "");
        }
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: track cleared notifications
    }

    private boolean isImportantApp(String pkg) {
        for (String app : IMPORTANT_APPS) {
            if (app.equals(pkg)) return true;
        }
        return false;
    }
}
