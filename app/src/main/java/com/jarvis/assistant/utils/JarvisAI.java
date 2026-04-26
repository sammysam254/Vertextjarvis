package com.jarvis.assistant.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JarvisAI {

    private static final String TAG = "JarvisAI";

    public interface AICallback { void onResponse(String response); }

    private final Context context;
    private final Handler mainHandler;
    private final OkHttpClient http;
    private final String deviceId;

    public JarvisAI(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        // Unique device ID using Android ID
        this.deviceId = android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID);
    }

    public void query(String message, AICallback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("deviceId", deviceId);

                Request req = new Request.Builder()
                    .url(JarvisSpeech.BACKEND + "/ai")
                    .post(RequestBody.create(body.toString(),
                        MediaType.get("application/json")))
                    .header("Content-Type", "application/json")
                    .build();

                Response resp = http.newCall(req).execute();
                String respStr = resp.body().string();
                Log.d(TAG, "Backend /ai HTTP=" + resp.code());

                JSONObject json = new JSONObject(respStr);
                String text = json.optString("text",
                    "I encountered a difficulty Sir. My apologies.");

                mainHandler.post(() -> callback.onResponse(text));

            } catch (Exception e) {
                Log.e(TAG, "query error: " + e.getMessage());
                mainHandler.post(() -> callback.onResponse(
                    "I'm having trouble connecting to my intelligence module Sir. " +
                    "Please check your internet connection."));
            }
        }).start();
    }

    // Combined AI + TTS in one backend call — more efficient
    public void queryAndSpeak(String message, JarvisSpeech speech,
            AICallback textCallback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", message);
                body.put("deviceId", deviceId);
                body.put("voice", "Charon");

                Request req = new Request.Builder()
                    .url(JarvisSpeech.BACKEND + "/speak")
                    .post(RequestBody.create(body.toString(),
                        MediaType.get("application/json")))
                    .header("Content-Type", "application/json")
                    .build();

                Response resp = http.newCall(req).execute();
                String respStr = resp.body().string();
                Log.d(TAG, "Backend /speak HTTP=" + resp.code());

                JSONObject json = new JSONObject(respStr);
                String text = json.optString("text", "I encountered a difficulty Sir.");
                String audio = json.optString("audio", "");
                String mime = json.optString("mimeType", "audio/wav");

                // Deliver text to UI
                mainHandler.post(() -> textCallback.onResponse(text));

                // Play audio if present
                if (!audio.isEmpty() && speech != null) {
                    byte[] audioBytes = android.util.Base64.decode(audio, android.util.Base64.DEFAULT);
                    String ext = mime.contains("mp3") ? ".mp3" : ".wav";
                    java.io.File f = new java.io.File(
                        context.getCacheDir(), "speak_" + java.util.UUID.randomUUID() + ext);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                    fos.write(audioBytes);
                    fos.close();

                    Handler h = new Handler(Looper.getMainLooper());
                    h.post(() -> {
                        try {
                            android.media.MediaPlayer mp = new android.media.MediaPlayer();
                            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                                .build());
                            mp.setDataSource(f.getAbsolutePath());
                            mp.prepare();
                            mp.setVolume(1.0f, 1.0f);
                            mp.start();
                            Log.d(TAG, "Playing combined speak audio ✓");
                            mp.setOnCompletionListener(m -> { m.release(); f.delete(); });
                            mp.setOnErrorListener((m, w, x) -> {
                                m.release(); f.delete(); return true;
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "playFile: " + e.getMessage());
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "queryAndSpeak error: " + e.getMessage());
                mainHandler.post(() -> textCallback.onResponse(
                    "Connection error Sir. Please check your internet."));
            }
        }).start();
    }

    public void clearHistory() {
        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                    .url(JarvisSpeech.BACKEND + "/session/" + deviceId)
                    .delete()
                    .build();
                http.newCall(req).execute();
                Log.d(TAG, "Session cleared");
            } catch (Exception e) { Log.e(TAG, e.getMessage()); }
        }).start();
    }
}
