package com.jarvis.assistant.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.jarvis.assistant.R;
import com.jarvis.assistant.ui.MainActivity;

/**
 * JarvisService - The immortal background service.
 * Runs forever, survives device reboots, and orchestrates all assistant features.
 */
public class JarvisService extends Service {

    private static final String TAG = "JarvisService";
    public static final String CHANNEL_ID = "jarvis_persistent_channel";
    public static final String CHANNEL_NAME = "J.A.R.V.I.S - Always On Duty";
    public static final int NOTIFICATION_ID = 1001;

    private VoiceListenerService voiceListener;
    private PowerManager.WakeLock wakeLock;
    private JarvisCommandProcessor commandProcessor;
    private boolean isRunning = false;

    // Broadcast actions for inter-component communication
    public static final String ACTION_COMMAND_RECEIVED = "com.jarvis.ACTION_COMMAND";
    public static final String ACTION_SPEAK = "com.jarvis.ACTION_SPEAK";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_TEXT = "text";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "JarvisService onCreate - Coming online, Sir.");
        createNotificationChannel();
        acquireWakeLock();
        commandProcessor = new JarvisCommandProcessor(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "JarvisService onStartCommand");

        // Start as foreground immediately to prevent killing
        startForeground(NOTIFICATION_ID, buildPersistentNotification());

        if (!isRunning) {
            isRunning = true;
            startVoiceListener();
        }

        // Handle commands passed via intent
        if (intent != null && intent.hasExtra(EXTRA_COMMAND)) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            if (command != null) {
                commandProcessor.processCommand(command);
            }
        }

        // STICKY - Android will restart if killed
        return START_STICKY;
    }

    private void startVoiceListener() {
        Intent voiceIntent = new Intent(this, VoiceListenerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(voiceIntent);
        } else {
            startService(voiceIntent);
        }
        Log.d(TAG, "Voice listener started. Awaiting your commands, Sir.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("J.A.R.V.I.S is standing by, ready to serve.");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildPersistentNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⬡  J.A.R.V.I.S")
            .setContentText("At your service, Sir. Say \"Jarvis\" to activate.")
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "JarvisService::WakeLock"
            );
            wakeLock.acquire();
        }
    }

    /**
     * Update notification with current status
     */
    public void updateNotificationStatus(String status) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⬡  J.A.R.V.I.S")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_jarvis_notif)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build();
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "JarvisService onDestroy - Restarting immediately...");
        isRunning = false;

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Restart self — J.A.R.V.I.S never truly goes offline
        Intent restartIntent = new Intent(this, JarvisService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed - Staying online, Sir.");
        // Re-schedule restart
        Intent restartIntent = new Intent(this, JarvisService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }
}
