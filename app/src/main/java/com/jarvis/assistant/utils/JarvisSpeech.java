package com.jarvis.assistant.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;
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

    // ── UPDATE THIS after Render deployment ───────────────────────────────────
    public static final String BACKEND = "https://jarvis-backend-xxxx.onrender.com";

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
            .readTimeout(25, TimeUnit.SECONDS)
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
        if (text == null || text.isEmpty()) {
            if (cb != null) cb.onDone();
            return;
        }
        Log.d(TAG, "speak() → " + text.substring(0, Math.min(50, text.length())));
        new Thread(() -> callBackendVoice(text, cb)).start();
    }

    private void callBackendVoice(String text, SpeechCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("voice", "Charon");

            Request req = new Request.Builder()
                .url(BACKEND + "/voice")
                .post(RequestBody.create(body.toString(),
                    MediaType.get("application/json")))
                .header("Content-Type", "application/json")
                .build();

            Response resp = http.newCall(req).execute();
            String respStr = resp.body().string();
            Log.d(TAG, "Backend /voice HTTP=" + resp.code());

            if (!resp.isSuccessful()) {
                Log.e(TAG, "Backend error: " + respStr);
                fallback(text, cb);
                return;
            }

            JSONObject json = new JSONObject(respStr);
            if (!json.has("audio")) {
                Log.e(TAG, "No audio in response: " + respStr);
                fallback(text, cb);
                return;
            }

            byte[] audioBytes = Base64.decode(json.getString("audio"), Base64.DEFAULT);
            String mime = json.optString("mimeType", "audio/wav");
            String ext = mime.contains("mp3") ? ".mp3" : ".wav";

            File f = new File(context.getCacheDir(), "tts_" + UUID.randomUUID() + ext);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(audioBytes);
            fos.close();

            Log.d(TAG, "Audio ready: " + audioBytes.length + " bytes");
            mainHandler.post(() -> playFile(f, cb));

        } catch (Exception e) {
            Log.e(TAG, "callBackendVoice: " + e.getMessage());
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
            Log.d(TAG, "Playing Gemini audio via backend ✓");

            mp.setOnCompletionListener(m -> {
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
            });
            mp.setOnErrorListener((m, w, x) -> {
                Log.e(TAG, "MediaPlayer error=" + w);
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "playFile: " + e.getMessage());
            if (cb != null) mainHandler.post(cb::onDone);
        }
    }

    private void fallback(String text, SpeechCallback cb) {
        mainHandler.post(() -> {
            try {
                if (fallbackTts != null && fallbackReady
                        && text != null && !text.isEmpty()) {
                    String id = "fb_" + System.currentTimeMillis();
                    if (cb != null) {
                        fallbackTts.setOnUtteranceProgressListener(
                            new UtteranceProgressListener() {
                                @Override public void onStart(String u) {}
                                @Override public void onDone(String u) {
                                    mainHandler.post(cb::onDone);
                                }
                                @Override public void onError(String u) {
                                    mainHandler.post(cb::onDone);
                                }
                            });
                    }
                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
                } else {
                    if (cb != null) cb.onDone();
                }
            } catch (Exception e) {
                Log.e(TAG, "fallback: " + e.getMessage());
                if (cb != null) cb.onDone();
            }
        });
    }

    public void stop() {
        try { if (fallbackTts != null) fallbackTts.stop(); }
        catch (Exception ignored) {}
    }

    public void shutdown() {
        try {
            if (fallbackTts != null) {
                fallbackTts.stop();
                fallbackTts.shutdown();
            }
        } catch (Exception ignored) {}
    }

    public void greetOnStart() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String t = h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
        speak("Good " + t + " Sir. J.A.R.V.I.S is fully operational and at your service.");
    }
}
