package com.jarvis.assistant.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
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
 * JarvisSpeech — Gemini TTS primary (free), Android TTS fallback.
 * Uses Gemini 2.5 Flash with "Charon" voice — deep, formal, authoritative.
 */
public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";

    // Gemini TTS endpoint
    private static final String GEMINI_TTS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=";

    // Best deep formal voice options from Gemini:
    // "Charon" - deep authoritative male
    // "Fenrir" - deep male
    // "Orus"   - formal male
    private static final String VOICE_NAME = "Charon";

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
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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
                boolean eng = n.contains("en-gb") || n.contains("en_gb")
                           || n.contains("en-us") || n.contains("en_us");
                boolean quality = v.getQuality() >= Voice.QUALITY_NORMAL;
                boolean offline = !v.isNetworkConnectionRequired();
                if (eng && quality && offline) {
                    if (best == null) best = v;
                    if (n.contains("male") || n.contains("en-gb")) { best = v; break; }
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
        if (text == null || text.isEmpty()) {
            if (callback != null) callback.onDone();
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
        String geminiKey = prefs.getString("gemini_key", "");

        if (!geminiKey.isEmpty()) {
            speakWithGemini(text, geminiKey, callback);
        } else {
            speakWithFallback(text, callback);
        }
    }

    private void speakWithGemini(String text, String apiKey, SpeechCallback callback) {
        try {
            // Build Gemini TTS request
            JSONObject speechConfig = new JSONObject();
            JSONObject voiceConfig = new JSONObject();
            JSONObject prebuiltVoice = new JSONObject();
            prebuiltVoice.put("voiceName", VOICE_NAME);
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoice);
            speechConfig.put("voiceConfig", voiceConfig);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseModalities", new JSONArray().put("AUDIO"));
            generationConfig.put("speechConfig", speechConfig);

            JSONObject part = new JSONObject();
            part.put("text", text);
            JSONObject content = new JSONObject();
            content.put("parts", new JSONArray().put(part));
            content.put("role", "user");

            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            body.put("generationConfig", generationConfig);

            RequestBody requestBody = RequestBody.create(
                body.toString(),
                MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(GEMINI_TTS_URL + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    Log.e(TAG, "Gemini TTS failed: " + e.getMessage());
                    speakWithFallback(text, callback);
                }

                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException {
                    try {
                        String respBody = response.body().string();
                        Log.d(TAG, "Gemini TTS response code: " + response.code());

                        JSONObject json = new JSONObject(respBody);

                        if (json.has("error")) {
                            Log.e(TAG, "Gemini TTS error: " + json.getJSONObject("error").getString("message"));
                            speakWithFallback(text, callback);
                            return;
                        }

                        // Extract audio data from response
                        JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                        JSONObject contentObj = candidate.getJSONObject("content");
                        JSONObject audioPart = contentObj.getJSONArray("parts").getJSONObject(0);

                        if (!audioPart.has("inlineData")) {
                            Log.e(TAG, "No inlineData in response");
                            speakWithFallback(text, callback);
                            return;
                        }

                        JSONObject inlineData = audioPart.getJSONObject("inlineData");
                        String audioBase64 = inlineData.getString("data");
                        String mimeType = inlineData.getString("mimeType"); // audio/wav or audio/mp3

                        byte[] audioBytes = Base64.decode(audioBase64, Base64.DEFAULT);

                        // Determine extension
                        String ext = mimeType.contains("wav") ? ".wav" : ".mp3";
                        File tempFile = new File(context.getCacheDir(),
                            "jarvis_" + UUID.randomUUID() + ext);
                        FileOutputStream fos = new FileOutputStream(tempFile);
                        fos.write(audioBytes);
                        fos.close();

                        mainHandler.post(() -> playAudioFile(tempFile, callback));

                    } catch (Exception e) {
                        Log.e(TAG, "Gemini TTS parse error: " + e.getMessage());
                        speakWithFallback(text, callback);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Gemini TTS request error: " + e.getMessage());
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
            Log.d(TAG, "Playing Gemini TTS audio: " + file.getName());

            player.setOnCompletionListener(mp -> {
                mp.release();
                file.delete();
                if (callback != null) mainHandler.post(callback::onDone);
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                mp.release();
                file.delete();
                speakWithFallback("", callback);
                return true;
            });

        } catch (Exception e) {
            Log.e(TAG, "Playback error: " + e.getMessage());
            if (callback != null) mainHandler.post(callback::onDone);
        }
    }

    private void speakWithFallback(String text, SpeechCallback callback) {
        mainHandler.post(() -> {
            try {
                if (fallbackTts != null && fallbackReady && text != null && !text.isEmpty()) {
                    String id = "j_" + System.currentTimeMillis();
                    if (callback != null) {
                        fallbackTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
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
                } else {
                    if (callback != null) callback.onDone();
                }
            } catch (Exception e) {
                Log.e(TAG, "Fallback TTS error: " + e.getMessage());
                if (callback != null) callback.onDone();
            }
        });
    }

    public void stop() {
        try { if (fallbackTts != null) fallbackTts.stop(); }
        catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    public void shutdown() {
        try {
            if (fallbackTts != null) { fallbackTts.stop(); fallbackTts.shutdown(); }
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    public void greetOnStart() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String time = hour < 12 ? "morning" : hour < 17 ? "afternoon" : "evening";
        speak("Good " + time + " Sir. J.A.R.V.I.S is fully operational and at your service.");
    }
}
