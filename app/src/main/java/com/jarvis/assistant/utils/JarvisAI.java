package com.jarvis.assistant.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JarvisAI {

    private static final String TAG = "JarvisAI";
    private static final String GEMINI_KEY = "AIzaSyA4yzazTjmnOdOz2RITHqrCxBzKZDlR7B8";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        GEMINI_MODEL + ":generateContent?key=" + GEMINI_KEY;

    private static final String SYSTEM_PROMPT =
        "You are J.A.R.V.I.S — a sophisticated personal AI assistant. " +
        "Speak with the formal dignity of White House serving staff. " +
        "Always address the user as 'Sir'. " +
        "Be formal, precise, concise and deferential. Never casual or wordy. " +
        "Keep responses under 3 sentences unless more detail is truly needed. " +
        "Typical responses: 'Yes Sir, right away.', 'At your service Sir.', " +
        "'Consider it done Sir.', 'Of course Sir, allow me to assist.' " +
        "You run on Android and can help with any question or task.";

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private final List<JSONObject> history = new ArrayList<>();

    public interface AICallback {
        void onResponse(String response);
    }

    public JarvisAI(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void query(String userMessage, AICallback callback) {
        try {
            // System instruction
            JSONObject sysInstruction = new JSONObject();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", SYSTEM_PROMPT);
            sysInstruction.put("parts", new JSONArray().put(sysPart));

            // Build contents with history
            JSONArray contents = new JSONArray();
            for (JSONObject msg : history) contents.put(msg);

            // Add new user message
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            userContent.put("parts", new JSONArray().put(new JSONObject().put("text", userMessage)));
            contents.put(userContent);

            // Generation config
            JSONObject genConfig = new JSONObject();
            genConfig.put("maxOutputTokens", 300);
            genConfig.put("temperature", 0.7);

            JSONObject body = new JSONObject();
            body.put("system_instruction", sysInstruction);
            body.put("contents", contents);
            body.put("generationConfig", genConfig);

            RequestBody reqBody = RequestBody.create(body.toString(),
                MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(GEMINI_URL)
                .addHeader("Content-Type", "application/json")
                .post(reqBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini fail: " + e.getMessage());
                    mainHandler.post(() -> callback.onResponse(
                        "I'm experiencing a connectivity issue Sir. Please check your network."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String respBody = response.body().string();
                        Log.d(TAG, "Gemini AI HTTP " + response.code());

                        JSONObject json = new JSONObject(respBody);

                        if (json.has("error")) {
                            String err = json.getJSONObject("error").getString("message");
                            Log.e(TAG, "Gemini AI error: " + err);
                            mainHandler.post(() -> callback.onResponse(
                                "I encountered a difficulty Sir: " + err));
                            return;
                        }

                        String text = json
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0)
                            .getString("text");

                        // Save to history
                        JSONObject uMsg = new JSONObject();
                        uMsg.put("role", "user");
                        uMsg.put("parts", new JSONArray().put(new JSONObject().put("text", userMessage)));
                        history.add(uMsg);

                        JSONObject aMsg = new JSONObject();
                        aMsg.put("role", "model");
                        aMsg.put("parts", new JSONArray().put(new JSONObject().put("text", text)));
                        history.add(aMsg);

                        // Keep history to last 20 messages
                        while (history.size() > 20) history.remove(0);

                        mainHandler.post(() -> callback.onResponse(text));

                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        mainHandler.post(() -> callback.onResponse(
                            "I encountered an error Sir. My apologies."));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Query error: " + e.getMessage());
            callback.onResponse("Technical difficulty Sir. My apologies.");
        }
    }

    public void clearHistory() { history.clear(); }
}
