package com.jarvis.assistant.services;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Log;

import com.jarvis.assistant.utils.JarvisAI;
import com.jarvis.assistant.utils.JarvisSpeech;
import com.jarvis.assistant.utils.JarvisUtils;

import java.util.Calendar;

/**
 * JarvisCommandProcessor - Interprets and executes all voice commands.
 * Handles device control, communication, information queries, and AI responses.
 */
public class JarvisCommandProcessor {

    private static final String TAG = "CommandProcessor";

    private final Context context;
    private final JarvisSpeech speech;
    private final JarvisAI ai;
    private final Handler mainHandler;

    public JarvisCommandProcessor(Context context) {
        this.context = context;
        this.speech = new JarvisSpeech(context);
        this.ai = new JarvisAI(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Main entry point — routes commands to the appropriate handler.
     */
    public void processCommand(String command) {
        String cmd = command.toLowerCase().trim();
        Log.d(TAG, "Processing: " + cmd);

        // Broadcast to UI
        broadcastToUI("command", cmd);

        // Route to handler based on keywords
        if (matchesAny(cmd, "time", "what time")) {
            handleTimeQuery(cmd);
        } else if (matchesAny(cmd, "date", "today", "what day")) {
            handleDateQuery(cmd);
        } else if (matchesAny(cmd, "call", "phone", "ring", "dial")) {
            handleCallCommand(cmd);
        } else if (matchesAny(cmd, "text", "message", "sms", "send message")) {
            handleMessageCommand(cmd);
        } else if (matchesAny(cmd, "wifi", "wi-fi")) {
            handleWifiCommand(cmd);
        } else if (matchesAny(cmd, "bluetooth")) {
            handleBluetoothCommand(cmd);
        } else if (matchesAny(cmd, "volume", "louder", "quieter", "mute", "silent")) {
            handleVolumeCommand(cmd);
        } else if (matchesAny(cmd, "flashlight", "torch", "light")) {
            handleFlashlightCommand(cmd);
        } else if (matchesAny(cmd, "alarm", "wake me", "remind me")) {
            handleAlarmCommand(cmd);
        } else if (matchesAny(cmd, "open", "launch", "start app")) {
            handleOpenAppCommand(cmd);
        } else if (matchesAny(cmd, "weather")) {
            handleWeatherQuery(cmd);
        } else if (matchesAny(cmd, "search", "google", "look up")) {
            handleSearchCommand(cmd);
        } else if (matchesAny(cmd, "play music", "play song", "spotify", "youtube music")) {
            handleMusicCommand(cmd);
        } else if (matchesAny(cmd, "screenshot", "capture screen")) {
            handleScreenshot(cmd);
        } else if (matchesAny(cmd, "battery", "charge")) {
            handleBatteryQuery(cmd);
        } else if (matchesAny(cmd, "calculator", "calculate", "what is", "how much")) {
            handleCalculationOrInfo(cmd);
        } else if (matchesAny(cmd, "navigate", "directions", "take me to", "how do i get")) {
            handleNavigationCommand(cmd);
        } else if (matchesAny(cmd, "goodbye", "bye", "sleep", "that will be all", "dismissed")) {
            handleDismiss(cmd);
        } else if (matchesAny(cmd, "who are you", "what are you", "introduce yourself")) {
            handleSelfIntro();
        } else {
            // Unknown command — use AI to respond
            handleAIQuery(cmd);
        }
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleTimeQuery(String cmd) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        String amPm = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        String time = String.format("It is currently %d:%02d %s, Sir.", h, minute, amPm);
        respond(time);
    }

    private void handleDateQuery(String cmd) {
        Calendar cal = Calendar.getInstance();
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String[] months = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
        String day = days[cal.get(Calendar.DAY_OF_WEEK) - 1];
        String month = months[cal.get(Calendar.MONTH)];
        int date = cal.get(Calendar.DAY_OF_MONTH);
        int year = cal.get(Calendar.YEAR);
        String dateStr = String.format("Today is %s, %s %d, %d, Sir.", day, month, date, year);
        respond(dateStr);
    }

    private void handleCallCommand(String cmd) {
        // Extract name/number after "call"
        String target = cmd.replaceAll("(call|phone|ring|dial|please|jarvis)", "").trim();
        if (target.isEmpty()) {
            respond("Who would you like me to call, Sir?");
            return;
        }
        respond("Placing a call to " + target + " now, Sir.");
        mainHandler.postDelayed(() -> {
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                // If it looks like a number
                if (target.matches("[0-9+\\-\\s]+")) {
                    callIntent.setData(Uri.parse("tel:" + target.replaceAll("\\s", "")));
                } else {
                    // Search contacts
                    callIntent.setData(Uri.parse("tel:" + target));
                }
                callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
            } catch (Exception e) {
                respond("I'm afraid I'm unable to place that call, Sir. Please check the contact or number.");
            }
        }, 1500);
    }

    private void handleMessageCommand(String cmd) {
        // Basic SMS intent
        String content = cmd.replaceAll("(text|message|sms|send|to|jarvis|please)", "").trim();
        respond("I'll open messaging for you, Sir.");
        mainHandler.postDelayed(() -> {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:"));
            smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(smsIntent);
        }, 1000);
    }

    private void handleWifiCommand(String cmd) {
        if (matchesAny(cmd, "on", "enable", "turn on")) {
            respond("Enabling Wi-Fi, Sir.");
            openWifiSettings();
        } else if (matchesAny(cmd, "off", "disable", "turn off")) {
            respond("Disabling Wi-Fi, Sir.");
            openWifiSettings();
        } else {
            respond("Opening Wi-Fi settings for you, Sir.");
            openWifiSettings();
        }
    }

    private void openWifiSettings() {
        mainHandler.postDelayed(() -> {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }, 1000);
    }

    private void handleBluetoothCommand(String cmd) {
        if (matchesAny(cmd, "on", "enable", "turn on")) {
            respond("Enabling Bluetooth at once, Sir.");
            mainHandler.postDelayed(() -> {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }, 1000);
        } else if (matchesAny(cmd, "off", "disable", "turn off")) {
            respond("Disabling Bluetooth, Sir.");
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) adapter.disable();
            } catch (Exception e) {
                respond("I was unable to disable Bluetooth directly, Sir. Opening settings instead.");
            }
        } else {
            respond("Opening Bluetooth settings, Sir.");
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private void handleVolumeCommand(String cmd) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;

        if (matchesAny(cmd, "mute", "silent", "silence", "quiet")) {
            audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            respond("Device silenced, Sir. You won't be disturbed.");
        } else if (matchesAny(cmd, "max", "full", "maximum", "loud")) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            respond("Volume set to maximum, Sir.");
        } else if (matchesAny(cmd, "low", "lower", "quieter", "down")) {
            int curr = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, curr - 2), 0);
            respond("Volume lowered, Sir.");
        } else if (matchesAny(cmd, "up", "raise", "higher", "louder", "increase")) {
            int curr = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(max, curr + 2), 0);
            respond("Volume raised, Sir.");
        } else if (matchesAny(cmd, "unmute", "normal", "ring")) {
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            respond("Sound restored, Sir.");
        }
    }

    private void handleFlashlightCommand(String cmd) {
        if (matchesAny(cmd, "on", "enable", "turn on")) {
            JarvisUtils.toggleFlashlight(context, true);
            respond("Torch activated, Sir.");
        } else if (matchesAny(cmd, "off", "disable", "turn off")) {
            JarvisUtils.toggleFlashlight(context, false);
            respond("Torch deactivated, Sir.");
        } else {
            JarvisUtils.toggleFlashlight(context, true);
            respond("Torch activated, Sir.");
        }
    }

    private void handleAlarmCommand(String cmd) {
        // Parse time from command  e.g. "set alarm for 7 AM"
        int hour = JarvisUtils.parseHourFromCommand(cmd);
        int minute = JarvisUtils.parseMinuteFromCommand(cmd);

        if (hour >= 0) {
            respond(String.format("Setting alarm for %d:%02d, Sir. Very well.", hour, minute));
            mainHandler.postDelayed(() -> {
                Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
                alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, hour);
                alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "J.A.R.V.I.S Alarm");
                alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(alarmIntent);
            }, 1500);
        } else {
            respond("I'm opening the alarm clock for you, Sir.");
            mainHandler.postDelayed(() -> {
                Intent alarmIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(alarmIntent);
            }, 1000);
        }
    }

    private void handleOpenAppCommand(String cmd) {
        String appName = cmd.replaceAll("(open|launch|start|app|please|jarvis)", "").trim();
        if (appName.isEmpty()) {
            respond("Which application would you like me to open, Sir?");
            return;
        }
        respond("Opening " + appName + " for you, Sir.");
        mainHandler.postDelayed(() -> JarvisUtils.openAppByName(context, appName), 1000);
    }

    private void handleWeatherQuery(String cmd) {
        respond("Fetching the latest weather report for you, Sir. One moment.");
        // Open weather app or search
        mainHandler.postDelayed(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://weather.com"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }, 1500);
    }

    private void handleSearchCommand(String cmd) {
        String query = cmd.replaceAll("(search|google|look up|find|please|jarvis)", "").trim();
        if (query.isEmpty()) {
            respond("What would you like me to search for, Sir?");
            return;
        }
        respond("Searching for " + query + " now, Sir.");
        mainHandler.postDelayed(() -> {
            Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
            searchIntent.putExtra("query", query);
            searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(searchIntent);
        }, 1000);
    }

    private void handleMusicCommand(String cmd) {
        respond("Opening music for you, Sir.");
        mainHandler.postDelayed(() -> JarvisUtils.openMusicApp(context), 1000);
    }

    private void handleScreenshot(String cmd) {
        respond("Taking a screenshot, Sir.");
        mainHandler.postDelayed(() -> {
            Intent intent = new Intent("android.intent.action.SCREENSHOT");
            context.sendBroadcast(intent);
        }, 1000);
    }

    private void handleBatteryQuery(String cmd) {
        int level = JarvisUtils.getBatteryLevel(context);
        String status = JarvisUtils.getBatteryStatus(context);
        respond(String.format("Battery is at %d percent and currently %s, Sir.", level, status));
    }

    private void handleCalculationOrInfo(String cmd) {
        // Route to AI for calculations and general info
        handleAIQuery(cmd);
    }

    private void handleNavigationCommand(String cmd) {
        String destination = cmd.replaceAll("(navigate|navigation|directions|take me to|how do i get to|please|jarvis)", "").trim();
        if (destination.isEmpty()) {
            respond("Where would you like to navigate, Sir?");
            return;
        }
        respond("Opening navigation to " + destination + ", Sir.");
        mainHandler.postDelayed(() -> {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mapIntent);
        }, 1000);
    }

    private void handleDismiss(String cmd) {
        String[] dismissals = {
            "Understood, Sir. I shall remain on standby.",
            "Very well, Sir. I'll be here should you need me.",
            "Of course, Sir. Good day.",
            "As you wish, Sir. Standing by.",
            "Thank you, Sir. J.A.R.V.I.S signing off for now."
        };
        int idx = (int)(Math.random() * dismissals.length);
        respond(dismissals[idx]);
    }

    private void handleSelfIntro() {
        String intro = "I am J.A.R.V.I.S — your personal artificial intelligence assistant, Sir. " +
            "I am always running in the background, ready to serve at your command. " +
            "Simply say 'Jarvis' followed by your request, and I shall execute it promptly. " +
            "It is my honour and privilege to be at your service.";
        respond(intro);
    }

    private void handleAIQuery(String cmd) {
        respond("Allow me a moment to look into that, Sir.");
        ai.query(cmd, response -> {
            respond(response);
        });
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void respond(String text) {
        Log.d(TAG, "J.A.R.V.I.S: " + text);
        broadcastToUI("response", text);
        speech.speak(text);
    }

    private void broadcastToUI(String type, String content) {
        Intent intent = new Intent("com.jarvis.UI_UPDATE");
        intent.putExtra("type", type);
        intent.putExtra("content", content);
        context.sendBroadcast(intent);
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
