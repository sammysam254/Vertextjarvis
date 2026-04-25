package com.jarvis.assistant.utils;

import android.content.Context;
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

public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";
    private static final String GEMINI_KEY = "AIzaSyA4yzazTjmnOdOz2RITHqrCxBzKZDlR7B8";
    private static final String GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts";
    private static final String GEMINI_TTS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        GEMINI_TTS_MODEL + ":generateContent?key=" + GEMINI_KEY;

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
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        initFallbackTTS();
    }

    private void initFallbackTTS() {
        fallbackTts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                setBestFallbackVoice();
                fallbackTts.setSpeechRate(0.85f);
                fallbackTts.setPitch(0.75f);
                fallbackReady = true;
                Log.d(TAG, "Fallback TTS ready");
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
                if (eng && quality) {
                    if (best == null) best = v;
                    if (n.contains("en-gb")) { best = v; break; }
                }
            }
            if (best != null) fallbackTts.setVoice(best);
            else fallbackTts.setLanguage(Locale.UK);
        } catch (Exception e) {
            try { fallbackTts.setLanguage(Locale.UK); } catch (Exception ignored) {}
        }
    }

    public void speak(String text) { speak(text, null); }

    public void speak(String text, SpeechCallback callback) {
        if (text == null || text.isEmpty()) {
            if (callback != null) callback.onDone();
            return;
        }
        Log.d(TAG, "Speaking via Gemini TTS: " + text.substring(0, Math.min(40, text.length())));
        speakWithGemini(text, callback);
    }

    private void speakWithGemini(String text, SpeechCallback callback) {
        try {
            JSONObject prebuiltVoice = new JSONObject();
            prebuiltVoice.put("voiceName", "Charon");

            JSONObject voiceConfig = new JSONObject();
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoice);

            JSONObject speechConfig = new JSONObject();
            speechConfig.put("voiceConfig", voiceConfig);

            JSONObject genConfig = new JSONObject();
            genConfig.put("responseModalities", new JSONArray().put("AUDIO"));
            genConfig.put("speechConfig", speechConfig);

            JSONObject part = new JSONObject();
            part.put("text", text);

            JSONObject content = new JSONObject();
            content.put("parts", new JSONArray().put(part));
            content.put("role", "user");

            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            body.put("generationConfig", genConfig);

            RequestBody reqBody = RequestBody.create(body.toString(),
                MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(GEMINI_TTS_URL)
                .addHeader("Content-Type", "application/json")
                .post(reqBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    Log.e(TAG, "Gemini TTS fail: " + e.getMessage());
                    speakWithFallback(text, callback);
                }

                @Override
                public void onResponse(Call call, Response response) throws java.io.IOException {
                    String respBody = "";
                    try {
                        respBody = response.body().string();
                        Log.d(TAG, "Gemini TTS HTTP " + response.code());

                        JSONObject json = new JSONObject(respBody);

                        if (json.has("error")) {
                            String err = json.getJSONObject("error").getString("message");
                            Log.e(TAG, "Gemini TTS error: " + err);
                            speakWithFallback(text, callback);
                            return;
                        }

                        JSONObject candidate = json
                            .getJSONArray("candidates").getJSONObject(0);
                        JSONObject contentObj = candidate.getJSONObject("content");
                        JSONObject audioPart = contentObj.getJSONArray("parts").getJSONObject(0);

                        if (!audioPart.has("inlineData")) {
                            Log.e(TAG, "No inlineData. Response: " +
                                respBody.substring(0, Math.min(400, respBody.length())));
                            speakWithFallback(text, callback);
                            return;
                        }

                        JSONObject inlineData = audioPart.getJSONObject("inlineData");
                        String audioBase64 = inlineData.getString("data");
                        String mimeType = inlineData.optString("mimeType", "audio/wav");
                        Log.d(TAG, "Got audio! mime=" + mimeType + " b64len=" + audioBase64.length());

                        byte[] audioBytes = Base64.decode(audioBase64, Base64.DEFAULT);
                        String ext = mimeType.contains("mp3") ? ".mp3" : ".wav";
                        File tmp = new File(context.getCacheDir(), "j_" + UUID.randomUUID() + ext);
                        new FileOutputStream(tmp).write(audioBytes);

                        mainHandler.post(() -> playAudio(tmp, callback));

                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        speakWithFallback(text, callback);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Build error: " + e.getMessage());
            speakWithFallback(text, callback);
        }
    }

    private void playAudio(File file, SpeechCallback callback) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.start();
            Log.d(TAG, "Playing Gemini audio!");

            player.setOnCompletionListener(mp -> {
                mp.release();
                file.delete();
                if (callback != null) mainHandler.post(callback::onDone);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what);
                mp.release();
                file.delete();
                speakWithFallback("", callback);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "playAudio error: " + e.getMessage());
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
                            @Override public void onDone(String u) { mainHandler.post(callback::onDone); }
                            @Override public void onError(String u) { mainHandler.post(callback::onDone); }
                        });
                    }
                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
                } else {
                    if (callback != null) callback.onDone();
                }
            } catch (Exception e) {
                Log.e(TAG, "Fallback error: " + e.getMessage());
                if (callback != null) callback.onDone();
            }
        });
    }

    public void stop() {
        try { if (fallbackTts != null) fallbackTts.stop(); } catch (Exception ignored) {}
    }

    public void shutdown() {
        try {
            if (fallbackTts != null) { fallbackTts.stop(); fallbackTts.shutdown(); }
        } catch (Exception ignored) {}
    }

    public void greetOnStart() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String t = h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
        speak("Good " + t + " Sir. J.A.R.V.I.S is fully operational and at your service.");
    }
}
