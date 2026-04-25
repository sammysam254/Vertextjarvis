package com.jarvis.assistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
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
            layout.setPadding(48, 24, 48, 24);

            // Gemini Key — used for BOTH AI and voice
            TextView tvGemini = new TextView(ctx);
            tvGemini.setText("Gemini API Key (AI + Voice):");
            tvGemini.setTextColor(0xFFC0A855);
            tvGemini.setTextSize(14);
            layout.addView(tvGemini);

            EditText etGemini = new EditText(ctx);
            etGemini.setHint("AIzaSy...");
            etGemini.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etGemini.setText(prefs.getString("gemini_key", ""));
            layout.addView(etGemini);

            TextView tvHelp = new TextView(ctx);
            tvHelp.setText("\n✓ Powers J.A.R.V.I.S intelligence (Gemini 1.5 Flash)\n" +
                "✓ Powers premium voice (Gemini TTS - Charon voice)\n" +
                "✓ 100% FREE at aistudio.google.com\n\n" +
                "Without key: uses device TTS voice\n\n" +
                "Long-press ACTIVATE to return here.");
            tvHelp.setTextColor(0xFF8899AA);
            tvHelp.setTextSize(12);
            layout.addView(tvHelp);

            new AlertDialog.Builder(ctx)
                .setTitle("⬡  J.A.R.V.I.S Configuration")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit()
                        .putString("gemini_key", etGemini.getText().toString().trim())
                        .apply();
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton("Cancel", null)
                .show();

        } catch (Exception e) {
            Log.e("SettingsHelper", "Error: " + e.getMessage());
        }
    }
}
