package com.jarvis.assistant.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.jarvis.assistant.R;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceListenerService extends Service implements RecognitionListener {

    private static final String TAG = "VoiceListener";
    private static final String WAKE_WORD = "jarvis";

    private SpeechRecognizer recognizer;
    private JarvisCommandProcessor commandProcessor;
    private Handler handler;
    private boolean isListening = false;
    private boolean awake = false;
    private boolean active = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        commandProcessor = new JarvisCommandProcessor(this);
        Log.d(TAG, "VoiceListenerService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1002, buildNotif("Listening for Jarvis..."));
        if (!active) {
            active = true;
            new Thread(() -> { try { new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder().url(com.jarvis.assistant.utils.JarvisSpeech.BACKEND + "/").get().build()).execute(); } catch (Exception e) {} }).start();
            handler.postDelayed(this::startListening, 500);
        }
        return START_STICKY;
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available");
            handler.postDelayed(this::startListening, 5000);
            return;
        }
        try {
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);

            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);

            recognizer.startListening(i);
            isListening = true;
            Log.d(TAG, "Listening... awake=" + awake);
        } catch (Exception e) {
            Log.e(TAG, "startListening: " + e.getMessage());
            restartDelayed(2000);
        }
    }

    @Override
    public void onResults(Bundle results) {
        isListening = false;
        ArrayList<String> matches = results.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            restartDelayed(300);
            return;
        }

        String heard = matches.get(0).toLowerCase().trim();
        Log.d(TAG, "Heard: " + heard);

        if (!awake) {
            // Check for wake word
            if (heard.contains(WAKE_WORD) || heard.contains("travis")
                    || heard.contains("jarvis") || heard.contains("davis")
                    || heard.contains("jervis")) {

                // Extract command after wake word if present
                String afterWake = heard
                    .replaceFirst(".*(jarvis|travis|davis|jervis)\\s*", "").trim();

                if (afterWake.length() > 2) {
                    // Wake word + command in one sentence
                    Log.d(TAG, "Wake+command: " + afterWake);
                    awake = true;
                    executeCommand(afterWake);
                } else {
                    // Wake word only — greet and listen for command
                    awake = true;
                    greetAndListen();
                }
            } else {
                restartDelayed(100);
            }
        } else {
            // Already awake — process command
            if (heard.length() > 1) {
                executeCommand(heard);
            } else {
                awake = false;
                restartDelayed(100);
            }
        }
    }

    private void greetAndListen() {
        // Speak greeting then listen for command
        String[] greetings = {
            "Yes Sir.",
            "At your service Sir.",
            "How may I assist you Sir.",
            "Yes Sir, go ahead.",
            "I'm listening Sir."
        };
        String greeting = greetings[(int)(Math.random() * greetings.length)];

        // Broadcast to trigger speech
        Intent intent = new Intent("com.jarvis.SPEAK_TEXT");
        intent.putExtra("text", greeting);
        sendBroadcast(intent);

        updateNotif("Awaiting command Sir...");

        // Listen for command after brief pause
        handler.postDelayed(() -> startListening(), 2000);
    }

    private void executeCommand(String command) {
        Log.d(TAG, "Executing: " + command);
        awake = false;
        updateNotif("Processing: " + command);

        // Broadcast command to UI
        Intent uiIntent = new Intent("com.jarvis.UI_UPDATE");
        uiIntent.putExtra("type", "command");
        uiIntent.putExtra("content", command);
        sendBroadcast(uiIntent);

        // Execute command
        commandProcessor.processCommand(command);

        // Resume listening after command
        handler.postDelayed(() -> {
            updateNotif("Listening for Jarvis...");
            startListening();
        }, 4000);
    }

    @Override
    public void onPartialResults(Bundle partial) {
        ArrayList<String> results = partial.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (results != null && !results.isEmpty()) {
            String p = results.get(0).toLowerCase();
            // Quick wake word detection on partial results
            if (!awake && (p.contains(WAKE_WORD) || p.contains("travis"))) {
                Log.d(TAG, "Wake word in partial: " + p);
            }
        }
    }

    @Override
    public void onError(int error) {
        isListening = false;
        // Most errors just need a restart
        int delay = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) ? 1500 : 300;
        if (error != SpeechRecognizer.ERROR_NO_MATCH &&
            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            Log.w(TAG, "Recognition error: " + error);
        }
        restartDelayed(delay);
    }

    private void restartDelayed(int ms) {
        handler.postDelayed(this::startListening, ms);
    }

    private void updateNotif(String text) {
        android.app.NotificationManager nm =
            (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1002, buildNotif(text));
    }

    private Notification buildNotif(String text) {
        return new NotificationCompat.Builder(this, JarvisService.CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    @Override public void onReadyForSpeech(Bundle p) { Log.d(TAG, "Ready"); }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rms) {}
    @Override public void onBufferReceived(byte[] b) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int t, Bundle p) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        active = false;
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        // Restart self
        handler.postDelayed(() -> {
            Intent i = new Intent(this, VoiceListenerService.class);
            startService(i);
        }, 1000);
    }
}
