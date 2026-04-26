package com.jarvis.assistant.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JarvisSpeech {

    private static final String TAG = "JarvisSpeech";
    public static final String BACKEND = "https://vertextjarvis.onrender.com";

    private final Context context;
    private final Handler mainHandler;
    private final OkHttpClient http;

    public interface SpeechCallback { void onDone(); }

    public JarvisSpeech(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        registerReceiver();
        // Wake backend on init
        new Thread(this::pingBackend).start();
    }

    private void pingBackend() {
        try {
            Request req = new Request.Builder()
                .url(BACKEND + "/")
                .get().build();
            http.newCall(req).execute();
            Log.d(TAG, "Backend pinged - awake");
        } catch (Exception e) {
            Log.e(TAG, "Ping failed: " + e.getMessage());
        }
    }

    private void registerReceiver() {
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String text = intent.getStringExtra("text");
                    if (text != null) speak(text);
                }
            };
            context.registerReceiver(r, new IntentFilter("com.jarvis.SPEAK_TEXT"));
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    public void speak(String text) { speak(text, null); }

    public void speak(final String text, final SpeechCallback cb) {
        if (text == null || text.isEmpty()) {
            if (cb != null) cb.onDone();
            return;
        }
        // Clean text — no letter-by-letter reading
        String cleaned = text
            .replace("J.A.R.V.I.S", "Jarvis")
            .replace("J.A.R.V.I.S.", "Jarvis")
            .replace("A.I.", "AI")
            .replaceAll("([A-Z])\\.", "$1")
            .replaceAll("\\.{2,}", ".")
            .trim();

        Log.d(TAG, "speak: " + cleaned.substring(0, Math.min(60, cleaned.length())));
        new Thread(() -> callBackend(cleaned, cb)).start();
    }

    private void callBackend(String text, SpeechCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("voice", "Charon");

            Request req = new Request.Builder()
                .url(BACKEND + "/voice")
                .post(RequestBody.create(body.toString(),
                    MediaType.get("application/json; charset=utf-8")))
                .header("Content-Type", "application/json")
                .build();

            Response resp = http.newCall(req).execute();
            String respStr = resp.body().string();

            if (!resp.isSuccessful()) {
                Log.e(TAG, "Backend " + resp.code() + ": " + respStr);
                if (cb != null) mainHandler.post(cb::onDone);
                return;
            }

            JSONObject json = new JSONObject(respStr);
            String audioB64 = json.optString("audio", "");

            if (audioB64.isEmpty()) {
                Log.e(TAG, "No audio returned");
                if (cb != null) mainHandler.post(cb::onDone);
                return;
            }

            byte[] bytes = Base64.decode(audioB64, Base64.DEFAULT);
            if (bytes.length < 100) {
                Log.e(TAG, "Audio too small: " + bytes.length);
                if (cb != null) mainHandler.post(cb::onDone);
                return;
            }

            File f = new File(context.getCacheDir(),
                "tts_" + UUID.randomUUID() + ".wav");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bytes);
            fos.close();

            Log.d(TAG, "Audio ready: " + bytes.length + " bytes");
            mainHandler.post(() -> playFile(f, cb));

        } catch (Exception e) {
            Log.e(TAG, "callBackend: " + e.getMessage());
            // Silent fail — no Android TTS fallback
            if (cb != null) mainHandler.post(cb::onDone);
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
            Log.d(TAG, "Playing Gemini Charon ✓ size=" + f.length());

            mp.setOnCompletionListener(m -> {
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
            });
            mp.setOnErrorListener((m, w, x) -> {
                Log.e(TAG, "MediaPlayer error w=" + w);
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "playFile: " + e.getMessage());
            if (cb != null) mainHandler.post(cb::onDone);
        }
    }

    public void stop() {}

    public void shutdown() {}

    public void greetOnStart() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String t = h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
        speak("Good " + t + " Sir. Jarvis is online and at your service.");
    }
}
