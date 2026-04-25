package com.jarvis.assistant.utils;

import android.content.Context;
import android.content.SharedPreferences;
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

/**
 * JarvisAI - Powered by Google Gemini (primary) with Claude fallback.
 */
public class JarvisAI {

    private static final String TAG = "JarvisAI";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Gemini endpoint
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    // Claude fallback endpoint
    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";

    private static final String SYSTEM_PROMPT =
        "You are J.A.R.V.I.S, a highly sophisticated personal AI assistant. " +
        "You serve with the impeccable professionalism of White House serving staff. " +
        "Always address the user as 'Sir'. " +
        "Be formal, precise, warm and deferential. Never casual. " +
        "Keep responses concise — under 3 sentences unless more detail is needed. " +
        "Examples: 'Yes Sir, right away.', 'At your service Sir.', " +
        "'Of course Sir, allow me.', 'Consider it done Sir.' " +
        "You are running on an Android device and assist with any task asked.";

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private final List<JSONObject> conversationHistory;

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
        this.conversationHistory = new ArrayList<>();
    }

    public void query(String userMessage, AICallback callback) {
        SharedPreferences prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
        String geminiKey = prefs.getString("gemini_key", "");
        String claudeKey = prefs.getString("api_key", "");

        if (!geminiKey.isEmpty()) {
            queryGemini(userMessage, geminiKey, callback);
        } else if (!claudeKey.isEmpty()) {
            queryClaude(userMessage, claudeKey, callback);
        } else {
            callback.onResponse("I'm afraid my intelligence module requires an API key " +
                "to be configured Sir. Please long-press the ACTIVATE button " +
                "and enter your Gemini or Claude API key.");
        }
    }

    // ─── GEMINI ───────────────────────────────────────────────────────────────

    private void queryGemini(String userMessage, String apiKey, AICallback callback) {
        try {
            // Build Gemini request
            JSONObject systemInstruction = new JSONObject();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", SYSTEM_PROMPT);
            systemInstruction.put("parts", new JSONArray().put(sysPart));

            // Build contents array with history
            JSONArray contents = new JSONArray();

            // Add conversation history
            for (JSONObject msg : conversationHistory) {
                contents.put(msg);
            }

            // Add current user message
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            JSONArray userParts = new JSONArray();
            userParts.put(new JSONObject().put("text", userMessage));
            userContent.put("parts", userParts);
            contents.put(userContent);

            JSONObject body = new JSONObject();
            body.put("system_instruction", systemInstruction);
            body.put("contents", contents);

            // Generation config
            JSONObject genConfig = new JSONObject();
            genConfig.put("maxOutputTokens", 300);
            genConfig.put("temperature", 0.7);
            body.put("generationConfig", genConfig);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                .url(GEMINI_URL + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini failed: " + e.getMessage());
                    mainHandler.post(() -> callback.onResponse(
                        "I'm experiencing a connectivity issue Sir. " +
                        "Please verify your network connection."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        Log.d(TAG, "Gemini response: " + body);
                        JSONObject json = new JSONObject(body);

                        if (json.has("error")) {
                            String errMsg = json.getJSONObject("error").getString("message");
                            Log.e(TAG, "Gemini error: " + errMsg);
                            mainHandler.post(() -> callback.onResponse(
                                "I encountered a difficulty Sir. " + errMsg));
                            return;
                        }

                        String text = json
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                        // Save to history
                        JSONObject userMsg = new JSONObject();
                        userMsg.put("role", "user");
                        userMsg.put("parts", new JSONArray().put(new JSONObject().put("text", userMessage)));
                        conversationHistory.add(userMsg);

                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "model");
                        assistantMsg.put("parts", new JSONArray().put(new JSONObject().put("text", text)));
                        conversationHistory.add(assistantMsg);

                        // Keep history manageable
                        while (conversationHistory.size() > 20) {
                            conversationHistory.remove(0);
                        }

                        mainHandler.post(() -> callback.onResponse(text));

                    } catch (Exception e) {
                        Log.e(TAG, "Gemini parse error: " + e.getMessage());
                        mainHandler.post(() -> callback.onResponse(
                            "I encountered an error processing that response Sir. My apologies."));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Gemini request error: " + e.getMessage());
            callback.onResponse("I'm afraid I encountered a technical difficulty Sir.");
        }
    }

    // ─── CLAUDE FALLBACK ──────────────────────────────────────────────────────

    private void queryClaude(String userMessage, String apiKey, AICallback callback) {
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            conversationHistory.add(userMsg);

            while (conversationHistory.size() > 20) conversationHistory.remove(0);

            JSONObject body = new JSONObject();
            body.put("model", "claude-haiku-4-5-20251001");
            body.put("max_tokens", 300);
            body.put("system", SYSTEM_PROMPT);
            body.put("messages", new JSONArray(conversationHistory));

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                .url(CLAUDE_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onResponse(
                        "Connectivity issue Sir. Please check your network."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String respBody = response.body().string();
                        JSONObject json = new JSONObject(respBody);
                        String text = json.getJSONArray("content")
                            .getJSONObject(0).getString("text");

                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", text);
                        conversationHistory.add(assistantMsg);

                        mainHandler.post(() -> callback.onResponse(text));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onResponse(
                            "I encountered an error Sir. My apologies."));
                    }
                }
            });

        } catch (Exception e) {
            callback.onResponse("Technical difficulty Sir. My apologies.");
        }
    }

    public void clearHistory() {
        conversationHistory.clear();
    }
}
