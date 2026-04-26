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
    public static final String BACKEND = "https://vertextjarvis.onrender.com";

    private final Context context;
    private final Handler mainHandler;
    private final OkHttpClient http;
    private TextToSpeech fallbackTts;
    private boolean fallbackReady = false;
    private boolean isSpeaking = false;

    public interface SpeechCallback { void onDone(); }

    public JarvisSpeech(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        initFallback();
        registerSpeakReceiver();
    }

    private void registerSpeakReceiver() {
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String text = intent.getStringExtra("text");
                    if (text != null) speak(text);
                }
            };
            context.registerReceiver(receiver,
                new IntentFilter("com.jarvis.SPEAK_TEXT"));
        } catch (Exception e) { Log.e(TAG, e.getMessage()); }
    }

    private void initFallback() {
        mainHandler.post(() -> {
            fallbackTts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        fallbackTts.setLanguage(Locale.UK);
                        fallbackTts.setSpeechRate(0.88f);
                        fallbackTts.setPitch(0.75f);
                        fallbackReady = true;
                        Log.d(TAG, "Fallback TTS ready");
                    } catch (Exception e) { Log.e(TAG, e.getMessage()); }
                }
            });
        });
    }

    public void speak(String text) { speak(text, null); }

    public void speak(final String text, final SpeechCallback cb) {
        if (text == null || text.isEmpty()) {
            if (cb != null) cb.onDone();
            return;
        }
        // Clean text — remove special chars that cause letter-by-letter spelling
        String cleaned = text
            .replace("J.A.R.V.I.S", "Jarvis")
            .replace("J.A.R.V.I.S.", "Jarvis")
            .replace("A.I.", "AI")
            .replace("...", " ")
            .replaceAll("([A-Z])\\.", "$1")  // Remove dots after single capitals
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
                Log.e(TAG, "Backend error " + resp.code());
                fallback(text, cb);
                return;
            }

            JSONObject json = new JSONObject(respStr);
            String audioB64 = json.optString("audio", "");

            if (audioB64.isEmpty()) {
                Log.e(TAG, "No audio in response");
                fallback(text, cb);
                return;
            }

            byte[] audioBytes = Base64.decode(audioB64, Base64.DEFAULT);
            Log.d(TAG, "Audio: " + audioBytes.length + " bytes");

            String mime = json.optString("mimeType", "audio/wav");
            String ext = mime.contains("mp3") ? ".mp3" : ".wav";
            File f = new File(context.getCacheDir(), "tts_" + UUID.randomUUID() + ext);
            new FileOutputStream(f).write(audioBytes);

            mainHandler.post(() -> playFile(f, cb));

        } catch (Exception e) {
            Log.e(TAG, "callBackend: " + e.getMessage());
            fallback(text, cb);
        }
    }

    private void playFile(File f, SpeechCallback cb) {
        try {
            isSpeaking = true;
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
            Log.d(TAG, "Playing Gemini Charon voice ✓");

            mp.setOnCompletionListener(m -> {
                isSpeaking = false;
                m.release(); f.delete();
                if (cb != null) mainHandler.post(cb::onDone);
            });
            mp.setOnErrorListener((m, w, x) -> {
                isSpeaking = false;
                Log.e(TAG, "MediaPlayer error w=" + w);
                m.release(); f.delete();
                fallback(text_ref, cb);
                return true;
            });
        } catch (Exception e) {
            isSpeaking = false;
            Log.e(TAG, "playFile: " + e.getMessage());
            if (cb != null) mainHandler.post(cb::onDone);
        }
    }

    // temp ref for error handler
    private String text_ref = "";

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
                if (cb != null) cb.onDone();
            }
        });
    }

    public boolean isSpeaking() { return isSpeaking; }

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
        speak("Good " + t + " Sir. Jarvis is fully operational and at your service.");
    }
}
