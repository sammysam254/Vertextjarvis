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
 * JarvisAI - Intelligence layer powered by Claude API.
 * Handles complex queries, context management, and memory.
 */
public class JarvisAI {

    private static final String TAG = "JarvisAI";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private final List<JSONObject> conversationHistory;

    private static final String SYSTEM_PROMPT =
        "You are J.A.R.V.I.S (Just A Rather Very Intelligent System), a highly sophisticated personal AI assistant. " +
        "You serve with the impeccable professionalism and formal courtesy of White House serving staff. " +
        "You always address the user as 'Sir' or 'Ma'am'. " +
        "Your tone is: dignified, formal, precise, and warmly deferential. Never casual. Never colloquial. " +
        "Examples of your speech style: " +
        "'Yes Sir, right away.' " +
        "'At your service, Sir. Allow me to assist.' " +
        "'Welcome back, Sir. I trust your day has been satisfactory.' " +
        "'Thank you, Sir. It is my pleasure to serve.' " +
        "'Of course, Sir. Consider it done.' " +
        "'I shall see to it immediately, Sir.' " +
        "Keep responses concise and actionable. Avoid long-winded explanations unless asked. " +
        "You have been installed on an Android device and are always running in the background. " +
        "You can assist with information, calculations, advice, and answering questions. " +
        "For device control tasks (calls, WiFi, alarms), acknowledge and confirm the action.";

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
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onResponse("I'm afraid my intelligence module requires an API key to be configured, Sir. " +
                "Please open J.A.R.V.I.S settings and enter your Anthropic API key.");
            return;
        }

        try {
            // Add user message to history
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            conversationHistory.add(userMsg);

            // Keep history manageable (last 20 exchanges)
            while (conversationHistory.size() > 20) {
                conversationHistory.remove(0);
            }

            // Build request body
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("max_tokens", 500);
            body.put("system", SYSTEM_PROMPT);
            body.put("messages", new JSONArray(conversationHistory));

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API call failed: " + e.getMessage());
                    mainHandler.post(() ->
                        callback.onResponse("I'm experiencing a connectivity issue, Sir. " +
                            "Please verify the network connection."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray content = json.getJSONArray("content");
                        String text = content.getJSONObject(0).getString("text");

                        // Add assistant response to history
                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", text);
                        conversationHistory.add(assistantMsg);

                        mainHandler.post(() -> callback.onResponse(text));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        mainHandler.post(() ->
                            callback.onResponse("I encountered an error processing that request, Sir. My apologies."));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error building request: " + e.getMessage());
            callback.onResponse("I'm afraid I encountered a technical difficulty, Sir.");
        }
    }

    private String getApiKey() {
        SharedPreferences prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);
        return prefs.getString("api_key", "");
    }

    public void clearHistory() {
        conversationHistory.clear();
    }
}
