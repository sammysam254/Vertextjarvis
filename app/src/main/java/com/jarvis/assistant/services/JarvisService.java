package com.jarvis.assistant.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.jarvis.assistant.R;
import com.jarvis.assistant.ui.MainActivity;

public class JarvisService extends Service {

    private static final String TAG = "JarvisService";
    public static final String CHANNEL_ID = "jarvis_channel";
    public static final int NOTIF_ID = 1001;

    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        Log.d(TAG, "JarvisService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "JarvisService starting");
        try {
            // Start foreground immediately with notification
            startForeground(NOTIF_ID, buildNotification("Standing by, Sir..."));

            if (!running) {
                running = true;
                acquireWakeLock();
                // Start voice listener after short delay
                handler.postDelayed(this::startVoiceListener, 1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand: " + e.getMessage());
        }
        return START_STICKY;
    }

    private void startVoiceListener() {
        try {
            Intent vi = new Intent(this, VoiceListenerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(vi);
            else
                startService(vi);
            Log.d(TAG, "VoiceListenerService started");
        } catch (Exception e) {
            Log.e(TAG, "startVoiceListener: " + e.getMessage());
            // Retry after 3 seconds
            handler.postDelayed(this::startVoiceListener, 3000);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "J.A.R.V.I.S",
                NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("J.A.R.V.I.S is running");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "JarvisService::Lock");
                wakeLock.acquire();
            }
        } catch (Exception e) { Log.e(TAG, "WakeLock: " + e.getMessage()); }
    }

    public void updateNotification(String text) {
        try {
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Exception e) { Log.e(TAG, e.getMessage()); }
        // Restart self
        handler.postDelayed(() -> {
            try {
                Intent i = new Intent(this, JarvisService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(i);
                else
                    startService(i);
            } catch (Exception e) { Log.e(TAG, "Restart: " + e.getMessage()); }
        }, 1000);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        onDestroy();
    }
}
