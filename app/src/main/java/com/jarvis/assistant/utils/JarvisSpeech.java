package com.jarvis.assistant.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * JarvisSpeech - Google Cloud TTS primary, Android TTS fallback.
 * Uses en-GB-Neural2-D — deep, formal British male voice.
 */
public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";
    private static final String GOOGLE_TTS_URL =
        "https://texttospeech.googleapis.com/v1/text:synthesize?key=";

    // Best formal deep male British voice
    private static final String VOICE_NAME = "en-GB-Neural2-D";
    private static final String LANGUAGE_CODE = "en-GB";

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private TextToSpeech fallbackTts;
    private boolean fallbackReady = false;

    public interface SpeechCallback {
        void onDone();
    }

    public JarvisSpeech(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        initFallbackTTS();
    }

    private void initFallbackTTS() {
        fallbackTts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                setBestFallbackVoice();
                fallbackTts.setSpeechRate(0.88f);
                fallbackTts.setPitch(0.78f);
                fallbackReady = true;
                Log.d(TAG, "Fallback TTS ready.");
            }
        });
    }

    private void setBestFallbackVoice() {
        try {
            Set<Voice> voices = fallbackTts.getVoices();
            if (voices == null) { fallbackTts.setLanguage(Locale.UK); return; }
            Voice best = null;
            for (Voice v : voices) {
                String n = v.getName().toLowerCase();
                boolean eng = n.contains("en-gb") || n.contains("en_gb");
                boolean quality = v.getQuality() >= Voice.QUALITY_NORMAL;
                if (eng && quality) {
                    if (best == null) best = v;
                    if (n.contains("male") || n.contains("en-gb-x")) { best = v; break; }
                }
            }
            if (best != null) fallbackTts.setVoice(best);
            else fallbackTts.setLanguage(Locale.UK);
        } catch (Exception e) {
            try { fallbackTts.setLanguage(Locale.UK); } catch (Exception ignored) {}
        }
    }

    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, SpeechCallback callback) {
        if (text == null || text.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
        String googleKey = prefs.getString("google_tts_key", "");

        if (!googleKey.isEmpty()) {
            speakWithGoogleTTS(text, googleKey, callback);
        } else {
            speakWithFallback(text, callback);
        }
    }

    private void speakWithGoogleTTS(String text, String apiKey, SpeechCallback callback) {
        try {
            JSONObject voiceParams = new JSONObject();
            voiceParams.put("languageCode", LANGUAGE_CODE);
            voiceParams.put("name", VOICE_NAME);
            voiceParams.put("ssmlGender", "MALE");

            JSONObject audioConfig = new JSONObject();
            audioConfig.put("audioEncoding", "MP3");
            audioConfig.put("speakingRate", 0.90);  // Slightly slower — more authoritative
            audioConfig.put("pitch", -3.0);          // Lower pitch — deeper, formal voice
            audioConfig.put("volumeGainDb", 2.0);    // Slightly louder

            JSONObject input = new JSONObject();
            // Use SSML for more natural pauses
            input.put("ssml", "<speak>" + text + "</speak>");

            JSONObject body = new JSONObject();
            body.put("input", input);
            body.put("voice", voiceParams);
            body.put("audioConfig", audioConfig);

            RequestBody requestBody = RequestBody.create(
                body.toString(),
                MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(GOOGLE_TTS_URL + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, okhttp3.IOException e) {
                    Log.e(TAG, "Google TTS failed: " + e.getMessage());
                    speakWithFallback(text, callback);
                }

                @Override
                public void onResponse(Call call, Response response) throws okhttp3.IOException {
                    try {
                        String respBody = response.body().string();
                        JSONObject json = new JSONObject(respBody);

                        if (!json.has("audioContent")) {
                            Log.e(TAG, "No audioContent: " + respBody);
                            speakWithFallback(text, callback);
                            return;
                        }

                        String audioContent = json.getString("audioContent");
                        byte[] audioBytes = Base64.decode(audioContent, Base64.DEFAULT);

                        // Save to temp file and play
                        File tempFile = new File(context.getCacheDir(),
                            "jarvis_speech_" + UUID.randomUUID() + ".mp3");
                        FileOutputStream fos = new FileOutputStream(tempFile);
                        fos.write(audioBytes);
                        fos.close();

                        mainHandler.post(() -> playAudioFile(tempFile, callback));

                    } catch (Exception e) {
                        Log.e(TAG, "Google TTS parse error: " + e.getMessage());
                        speakWithFallback(text, callback);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Google TTS request error: " + e.getMessage());
            speakWithFallback(text, callback);
        }
    }

    private void playAudioFile(File file, SpeechCallback callback) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.start();

            player.setOnCompletionListener(mp -> {
                mp.release();
                file.delete();
                if (callback != null) callback.onDone();
            });

            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                file.delete();
                speakWithFallback("", callback);
                return true;
            });

        } catch (Exception e) {
            Log.e(TAG, "Audio playback error: " + e.getMessage());
            if (callback != null) callback.onDone();
        }
    }

    private void speakWithFallback(String text, SpeechCallback callback) {
        mainHandler.post(() -> {
            try {
                if (fallbackTts != null && fallbackReady && !text.isEmpty()) {
                    String id = "j_" + System.currentTimeMillis();
                    if (callback != null) {
                        fallbackTts.setOnUtteranceProgressListener(
                            new android.speech.tts.UtteranceProgressListener() {
                                @Override public void onStart(String u) {}
                                @Override public void onDone(String u) {
                                    mainHandler.post(callback::onDone);
                                }
                                @Override public void onError(String u) {
                                    mainHandler.post(callback::onDone);
                                }
                            });
                    }
                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
                } else if (callback != null) {
                    callback.onDone();
                }
            } catch (Exception e) {
                Log.e(TAG, "Fallback TTS error: " + e.getMessage());
                if (callback != null) callback.onDone();
            }
        });
    }

    public void stop() {
        try {
            if (fallbackTts != null) fallbackTts.stop();
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    public void shutdown() {
        try {
            if (fallbackTts != null) { fallbackTts.stop(); fallbackTts.shutdown(); }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    public void greetOnStart() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "morning" : hour < 17 ? "afternoon" : "evening";
        speak("Good " + greeting + " Sir. J.A.R.V.I.S is now fully operational and at your service.");
    }
}
