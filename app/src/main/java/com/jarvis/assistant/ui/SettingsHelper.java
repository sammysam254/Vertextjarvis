package com.jarvis.assistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

public class SettingsHelper {

    public static void show(Context ctx, Runnable onSaved) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE);

            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            int pad = 48;
            layout.setPadding(pad, pad/2, pad, pad/2);

            // Gemini Key
            TextView tvGemini = new TextView(ctx);
            tvGemini.setText("Gemini API Key (Primary AI):");
            tvGemini.setTextColor(0xFFC0A855);
            layout.addView(tvGemini);

            EditText etGemini = new EditText(ctx);
            etGemini.setHint("AIza...");
            etGemini.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etGemini.setText(prefs.getString("gemini_key", ""));
            layout.addView(etGemini);

            // Google TTS Key
            TextView tvTTS = new TextView(ctx);
            tvTTS.setText("\nGoogle Cloud TTS Key (Premium Voice):");
            tvTTS.setTextColor(0xFFC0A855);
            layout.addView(tvTTS);

            EditText etTTS = new EditText(ctx);
            etTTS.setHint("AIza...");
            etTTS.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etTTS.setText(prefs.getString("google_tts_key", ""));
            layout.addView(etTTS);

            // Claude Key
            TextView tvClaude = new TextView(ctx);
            tvClaude.setText("\nClaude API Key (Fallback AI):");
            tvClaude.setTextColor(0xFF8899AA);
            layout.addView(tvClaude);

            EditText etClaude = new EditText(ctx);
            etClaude.setHint("sk-ant-...");
            etClaude.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etClaude.setText(prefs.getString("api_key", ""));
            layout.addView(etClaude);

            TextView tvHelp = new TextView(ctx);
            tvHelp.setText("\nGet Gemini key: aistudio.google.com\n" +
                "Get Google TTS key: console.cloud.google.com\n" +
                "(Both are free to start)\n\n" +
                "Long-press ACTIVATE to return here.");
            tvHelp.setTextColor(0xFF556678);
            tvHelp.setTextSize(11);
            layout.addView(tvHelp);

            new AlertDialog.Builder(ctx)
                .setTitle("⬡  J.A.R.V.I.S Configuration")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("gemini_key", etGemini.getText().toString().trim());
                    editor.putString("google_tts_key", etTTS.getText().toString().trim());
                    editor.putString("api_key", etClaude.getText().toString().trim());
                    editor.apply();
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton("Cancel", null)
                .show();

        } catch (Exception e) {
            Log.e("SettingsHelper", "Error: " + e.getMessage());
        }
    }
}
