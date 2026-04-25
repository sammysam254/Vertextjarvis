package com.jarvis.assistant.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.UUID;

/**
 * JarvisSpeech — Text-to-Speech with a formal, White House butler persona.
 * Speaks with a refined, authoritative tone.
 */
public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";
    private TextToSpeech tts;
    private boolean isReady = false;
    private final Context context;

    public interface SpeechCallback {
        void onDone();
    }

    public JarvisSpeech(Context context) {
        this.context = context;
        initTTS();
    }

    private void initTTS() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Set to British English for formal, distinguished tone
                int result = tts.setLanguage(Locale.UK);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }

                // Refined speech parameters
                tts.setSpeechRate(0.92f);   // Slightly slower — deliberate, measured
                tts.setPitch(0.85f);        // Slightly lower pitch — authoritative

                isReady = true;
                Log.d(TAG, "TTS initialized. J.A.R.V.I.S voice is ready.");
            } else {
                Log.e(TAG, "TTS initialization failed.");
            }
        });
    }

    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, SpeechCallback callback) {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready. Queuing: " + text);
            // Retry after short delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                speak(text, callback);
            }, 1000);
            return;
        }

        String utteranceId = UUID.randomUUID().toString();

        if (callback != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    callback.onDone();
                }

                @Override
                public void onError(String utteranceId) {
                    callback.onDone();
                }
            });
        }

        // Queue speech
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        Log.d(TAG, "Speaking: " + text);
    }

    public void speakAdd(String text) {
        if (!isReady || tts == null) return;
        String utteranceId = UUID.randomUUID().toString();
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // ─── FORMAL GREETINGS ────────────────────────────────────────────────────

    public void greetOnStart() {
        String hour = getTimeOfDayGreeting();
        speak("Good " + hour + ", Sir. J.A.R.V.I.S is now fully operational and at your service.");
    }

    private String getTimeOfDayGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return "morning";
        else if (hour < 17) return "afternoon";
        else if (hour < 21) return "evening";
        else return "evening";
    }
}
