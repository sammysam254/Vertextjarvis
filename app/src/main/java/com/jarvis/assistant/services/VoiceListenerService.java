package com.jarvis.assistant.services;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.jarvis.assistant.R;
import com.jarvis.assistant.utils.JarvisSpeech;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Continuously listens for the wake word "Jarvis" then captures full commands.
 * Runs as a foreground service to prevent being killed.
 */
public class VoiceListenerService extends Service implements RecognitionListener {

    private static final String TAG = "VoiceListenerService";
    private static final String WAKE_WORD = "jarvis";
    private static final long RESTART_DELAY_MS = 500;

    private SpeechRecognizer speechRecognizer;
    private JarvisCommandProcessor commandProcessor;
    private JarvisSpeech speech;
    private boolean isListeningForWakeWord = true;
    private boolean isActive = false;
    private android.os.Handler handler;

    // States
    public enum ListeningState {
        IDLE,
        WAITING_WAKE_WORD,
        AWAKE_LISTENING_COMMAND,
        PROCESSING
    }

    private ListeningState currentState = ListeningState.IDLE;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new android.os.Handler(android.os.Looper.getMainLooper());
        speech = new JarvisSpeech(this);
        commandProcessor = new JarvisCommandProcessor(this);
        Log.d(TAG, "VoiceListenerService created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1002, buildListenerNotification("Listening for wake word..."));
        if (!isActive) {
            isActive = true;
            startContinuousListening();
        }
        return START_STICKY;
    }

    private void startContinuousListening() {
        handler.post(() -> {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(this);
                beginListening();
                Log.d(TAG, "Continuous listening started. Awaiting \"Jarvis\", Sir.");
            } else {
                Log.e(TAG, "Speech recognition not available on this device.");
            }
        });
    }

    private void beginListening() {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // Set longer timeout for command capture
        if (!isListeningForWakeWord) {
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        }

        try {
            speechRecognizer.startListening(recognizerIntent);
            currentState = isListeningForWakeWord ?
                ListeningState.WAITING_WAKE_WORD : ListeningState.AWAKE_LISTENING_COMMAND;
        } catch (Exception e) {
            Log.e(TAG, "Error starting listener: " + e.getMessage());
            restartListenerDelayed();
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "Ready for speech.");
        broadcastState(currentState.name());
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "Speech detected.");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // Used for waveform visualization in UI
        Intent intent = new Intent("com.jarvis.AUDIO_LEVEL");
        intent.putExtra("rms", rmsdB);
        sendBroadcast(intent);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "End of speech.");
    }

    @Override
    public void onError(int error) {
        String errorMsg = getErrorText(error);
        Log.w(TAG, "Recognition error: " + errorMsg);
        // Restart listening immediately on most errors
        restartListenerDelayed();
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            restartListenerDelayed();
            return;
        }

        String spokenText = matches.get(0).toLowerCase().trim();
        Log.d(TAG, "Heard: " + spokenText);

        if (isListeningForWakeWord) {
            // Check if wake word was spoken
            if (spokenText.contains(WAKE_WORD)) {
                onWakeWordDetected(spokenText);
            } else {
                // Not our wake word, keep listening silently
                restartListenerDelayed();
            }
        } else {
            // We're in command mode - process whatever was said
            if (!spokenText.isEmpty()) {
                processVoiceCommand(spokenText);
            } else {
                // Nothing useful heard after wake word
                speech.speak("I'm still here, Sir. How may I assist you?");
                returnToWakeWordMode();
            }
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        // Optional: show partial results in UI
        ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (partial != null && !partial.isEmpty()) {
            Intent intent = new Intent("com.jarvis.PARTIAL_RESULTS");
            intent.putExtra("partial", partial.get(0));
            sendBroadcast(intent);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {}

    private void onWakeWordDetected(String fullPhrase) {
        Log.d(TAG, "Wake word detected! Full phrase: " + fullPhrase);

        // Check if the command was combined with wake word (e.g., "Jarvis turn off wifi")
        String afterWakeWord = fullPhrase.replace(WAKE_WORD, "").trim();

        if (afterWakeWord.length() > 3) {
            // Command was in the same sentence
            processVoiceCommand(afterWakeWord);
        } else {
            // Wake word only - greet and wait for command
            isListeningForWakeWord = false;
            speech.speak(getRandomGreeting(), () -> {
                updateNotification("Listening for your command...");
                beginListening();
            });
        }
    }

    private void processVoiceCommand(String command) {
        Log.d(TAG, "Processing command: " + command);
        currentState = ListeningState.PROCESSING;
        updateNotification("Processing: " + command);

        // Send to command processor
        commandProcessor.processCommand(command);

        // After processing, return to wake word listening
        handler.postDelayed(this::returnToWakeWordMode, 3000);
    }

    private void returnToWakeWordMode() {
        isListeningForWakeWord = true;
        updateNotification("Listening for wake word...");
        restartListenerDelayed();
    }

    private void restartListenerDelayed() {
        handler.postDelayed(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                } catch (Exception ignored) {}
            }
            beginListening();
        }, RESTART_DELAY_MS);
    }

    private String getRandomGreeting() {
        String[] greetings = {
            "Yes Sir, how may I assist you?",
            "At your service, Sir.",
            "Good to hear from you, Sir. What do you require?",
            "Of course, Sir. What shall I do?",
            "I'm listening, Sir.",
            "Your wish is my command, Sir.",
            "Ready and standing by, Sir."
        };
        int idx = (int)(Math.random() * greetings.length);
        return greetings[idx];
    }

    private void updateNotification(String text) {
        android.app.NotificationManager manager =
            (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1002, buildListenerNotification(text));
        }
    }

    private Notification buildListenerNotification(String text) {
        return new NotificationCompat.Builder(this, JarvisService.CHANNEL_ID)
            .setContentTitle("⬡  J.A.R.V.I.S — Voice Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void broadcastState(String state) {
        Intent intent = new Intent("com.jarvis.STATE_CHANGE");
        intent.putExtra("state", state);
        sendBroadcast(intent);
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No match (silence or unrecognized)";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER: return "Error from server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown error " + errorCode;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (speech != null) {
            speech.shutdown();
        }
        Log.d(TAG, "VoiceListenerService destroyed — restarting.");
    }
}
