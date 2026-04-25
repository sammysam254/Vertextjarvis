package com.jarvis.assistant.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.jarvis.assistant.utils.JarvisSpeech;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getStringExtra("message");
        if (message == null) message = "Your alarm is going off, Sir.";
        JarvisSpeech speech = new JarvisSpeech(context);
        speech.speak("Attention, Sir. " + message);
    }
}
