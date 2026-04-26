package com.jarvis.assistant.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";
    private static final String GEMINI_KEY = "AIzaSyA4yzazTjmnOdOz2RITHqrCxBzKZDlR7B8";
    private static final String TTS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-2.5-flash-preview-tts:generateContent?key=" + GEMINI_KEY;

    private final Context context;
    private final Handler mainHandler;
    private final OkHttpClient http;
    private TextToSpeech fallbackTts;
    private boolean fallbackReady = false;

    public interface SpeechCallback { void onDone(); }

    public JarvisSpeech(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
        initFallback();
    }

    private void initFallback() {
        fallbackTts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    fallbackTts.setLanguage(Locale.UK);
                    fallbackTts.setSpeechRate(0.85f);
                    fallbackTts.setPitch(0.75f);
                    fallbackReady = true;
                    Log.d(TAG, "Fallback TTS ready");
                } catch (Exception e) { Log.e(TAG, e.getMessage()); }
            }
        });
    }

    public void speak(String text) { speak(text, null); }

    public void speak(final String text, final SpeechCallback cb) {
        if (text == null || text.isEmpty()) { if (cb != null) cb.onDone(); return; }
        Log.d(TAG, "Speaking: " + text.substring(0, Math.min(60, text.length())));
        new Thread(() -> callGeminiTTS(text, cb)).start();
    }

    private void callGeminiTTS(String text, SpeechCallback cb) {
        try {
            JSONObject part = new JSONObject();
            part.put("text", text);

            JSONObject content = new JSONObject();
            content.put("role", "user");
            content.put("parts", new JSONArray().put(part));

            JSONObject prebuilt = new JSONObject();
            prebuilt.put("voiceName", "Charon");

            JSONObject voiceCfg = new JSONObject();
            voiceCfg.put("prebuiltVoiceConfig", prebuilt);

            JSONObject speechCfg = new JSONObject();
            speechCfg.put("voiceConfig", voiceCfg);

            JSONObject genCfg = new JSONObject();
            genCfg.put("responseModalities", new JSONArray().put("AUDIO"));
            genCfg.put("speechConfig", speechCfg);

            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            body.put("generationConfig", genCfg);

            RequestBody reqBody = RequestBody.create(
                body.toString(), MediaType.get("application/json"));
            Request req = new Request.Builder()
                .url(TTS_URL)
                .post(reqBody)
                .header("Content-Type", "application/json")
                .build();

            Response response = http.newCall(req).execute();
            String respStr = response.body().string();
            Log.d(TAG, "TTS HTTP=" + response.code() +
                " preview=" + respStr.substring(0, Math.min(200, respStr.length())));

            JSONObject json = new JSONObject(respStr);
            if (json.has("error")) {
                Log.e(TAG, "TTS API err: " + json.getJSONObject("error").getString("message"));
                fallback(text, cb);
                return;
            }

            JSONObject inlineData = json
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData");

            String b64 = inlineData.getString("data");
            String mime = inlineData.optString("mimeType", "audio/wav");
            String ext = mime.contains("mp3") ? ".mp3" : ".wav";

            byte[] audio = Base64.decode(b64, Base64.DEFAULT);
            File f = new File(context.getCacheDir(), "tts_" + UUID.randomUUID() + ext);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(audio);
            fos.close();

            Log.d(TAG, "Audio saved: " + f.length() + " bytes, mime=" + mime);
            mainHandler.post(() -> playFile(f, cb));

        } catch (Exception e) {
            Log.e(TAG, "callGeminiTTS error: " + e.getMessage());
            fallback(text, cb);
        }
    }

    private void playFile(File f, SpeechCallback cb) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build());
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            mp.setVolume(1.0f, 1.0f);
            mp.start();
            Log.d(TAG, "Gemini audio playing ✓ size=" + f.length());
            mp.setOnCompletionListener(m -> {
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
            });
            mp.setOnErrorListener((m, w, x) -> {
                Log.e(TAG, "MediaPlayer error=" + w + "/" + x);
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "playFile error: " + e.getMessage());
            if (cb != null) mainHandler.post(cb::onDone);
        }
    }

    private void fallback(String text, SpeechCallback cb) {
        mainHandler.post(() -> {
            try {
                if (fallbackTts != null && fallbackReady && text != null && !text.isEmpty()) {
                    String id = "fb_" + System.currentTimeMillis();
                    if (cb != null) {
                        fallbackTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override public void onStart(String u) {}
                            @Override public void onDone(String u) { mainHandler.post(cb::onDone); }
                            @Override public void onError(String u) { mainHandler.post(cb::onDone); }
                        });
                    }
                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
                } else { if (cb != null) cb.onDone(); }
            } catch (Exception e) {
                Log.e(TAG, "fallback: " + e.getMessage());
                if (cb != null) cb.onDone();
            }
        });
    }

    public void stop() { try { if (fallbackTts != null) fallbackTts.stop(); } catch (Exception ignored) {} }
    public void shutdown() {
        try { if (fallbackTts != null) { fallbackTts.stop(); fallbackTts.shutdown(); } }
        catch (Exception ignored) {}
    }
    public void greetOnStart() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String t = h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
        speak("Good " + t + " Sir. J.A.R.V.I.S is fully operational and at your service.");
    }
}
