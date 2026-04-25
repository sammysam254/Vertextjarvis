# ⬡ J.A.R.V.I.S — Android Personal AI Assistant

> *"Just A Rather Very Intelligent System"*
> Always on. Always listening. Always at your service, Sir.

---

## 📋 Overview

J.A.R.V.I.S is a fully autonomous Android AI assistant that:
- **Runs 24/7 in the background** as a foreground service (survives app closes, restarts after reboot)
- **Listens continuously** for the wake word **"Jarvis"** then captures your command
- **Responds with formal, White House butler-style voice** — *"Yes Sir", "At your service, Sir", "Right away, Sir"*
- **Executes device actions** hands-free via voice
- **Integrates with Claude AI** for intelligent, conversational responses
- **Monitors screen activity** via Accessibility Service for contextual awareness

---

## 🚀 How to Build & Install

### Prerequisites
| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 11 or 17 |
| Android SDK | API 26–34 |
| Gradle | 8.4 (auto-downloaded) |
| Physical Android device | API 26+ (Android 8.0+) |

> ⚠️ **Speech recognition requires a real device**. Android emulators do NOT support microphone/SpeechRecognizer properly.

---

### Step 1: Open the Project

1. Launch **Android Studio**
2. Choose **"Open an existing project"**
3. Navigate to and select the `JarvisApp/` folder
4. Wait for Gradle sync to complete (first time downloads ~200MB of dependencies)

---

### Step 2: Configure the API Key (Optional — for AI intelligence)

To enable intelligent AI responses (beyond built-in commands):

1. Get your API key from **https://console.anthropic.com**
2. You can pre-set it by editing `JarvisAI.java`, or set it at runtime:
   - Open the app → tap **⚙ Settings icon** (top right)
   - Enter your Anthropic API key
   - Tap **Save**

Without an API key, J.A.R.V.I.S still handles all device commands but will prompt you to add one for general questions.

---

### Step 3: Build the APK

#### Option A — Android Studio (Recommended)
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

#### Option B — Command Line
```bash
# On macOS/Linux:
./gradlew assembleDebug

# On Windows:
gradlew.bat assembleDebug
```

#### Option C — Release APK (for distribution)
```bash
./gradlew assembleRelease
```
*(Requires signing config — set up in `build.gradle` under `signingConfigs`)*

---

### Step 4: Install on Device

**Enable Developer Options on your Android phone:**
1. Settings → About Phone → tap "Build Number" **7 times**
2. Settings → Developer Options → enable **USB Debugging**

**Install via ADB:**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or simply drag the APK file to your connected phone and open it.

---

## 🎙️ Voice Commands Reference

### Activation
Simply say: **"Jarvis"** (anywhere, anytime — app must be running)

J.A.R.V.I.S will respond: *"Yes Sir, how may I assist you?"*

You can also combine: **"Jarvis, turn off WiFi"** in one sentence.

---

### 📱 Device Control

| Command | Example | Response |
|---------|---------|----------|
| **Time** | "What time is it?" | "It is 3:45 PM, Sir." |
| **Date** | "What's today's date?" | "Today is Saturday, April 25, 2026, Sir." |
| **WiFi** | "Turn on WiFi" | Opens WiFi settings |
| **Bluetooth** | "Enable Bluetooth" | Enables/opens BT settings |
| **Volume** | "Mute / Louder / Quieter" | Adjusts system volume |
| **Torch** | "Turn on flashlight" | Activates camera torch |
| **Battery** | "What's my battery level?" | "Battery is at 78%, charging, Sir." |
| **Alarm** | "Set alarm for 7 AM" | Creates system alarm |
| **Screenshot** | "Take a screenshot" | Captures screen |

### 📞 Communication

| Command | Example |
|---------|---------|
| **Call** | "Call John" / "Call 0712345678" |
| **Message** | "Send a message to Sarah" |

### 🔍 Information & Search

| Command | Example |
|---------|---------|
| **Search** | "Search for Python tutorials" |
| **Weather** | "What's the weather like?" |
| **Navigate** | "Take me to Nairobi CBD" |
| **AI Query** | "What is quantum computing?" |
| **Calculate** | "What is 15% of 8500?" |

### 🎵 Media & Apps

| Command | Example |
|---------|---------|
| **Music** | "Play music" |
| **Open App** | "Open WhatsApp" / "Launch YouTube" |

### 💬 Interaction

| Command | Response |
|---------|---------|
| "Who are you?" | Full self introduction |
| "Goodbye" / "Dismissed" | *"Standing by, Sir. Good day."* |
| Any other question | Routes to Claude AI |

---

## 🏗️ Architecture

```
JarvisApp/
├── AndroidManifest.xml           ← All permissions + service declarations
│
├── services/
│   ├── JarvisService.java        ← Master foreground service (immortal)
│   ├── VoiceListenerService.java ← Continuous microphone / wake word detection
│   ├── JarvisCommandProcessor.java ← Routes and executes all commands
│   ├── JarvisAccessibilityService.java ← Screen & app monitoring
│   └── JarvisNotificationListener.java ← Reads incoming notifications
│
├── receivers/
│   ├── BootReceiver.java         ← Auto-starts after device reboot
│   └── AlarmReceiver.java        ← Handles alarm triggers
│
├── ui/
│   ├── SplashActivity.java       ← Branded intro screen
│   ├── MainActivity.java         ← Main dashboard + conversation feed
│   └── ConversationAdapter.java  ← Chat-style message list
│
└── utils/
    ├── JarvisSpeech.java         ← TTS with formal British voice
    ├── JarvisAI.java             ← Claude API integration + memory
    └── JarvisUtils.java          ← Device helpers (flashlight, battery, apps)
```

---

## 🔐 Permissions Setup (First Launch)

On first launch, grant all permissions when prompted. Then manually enable:

### 1. Accessibility Service *(for screen monitoring)*
```
Settings → Accessibility → J.A.R.V.I.S Screen Monitor → Enable
```

### 2. Notification Listener *(to read notifications)*
```
Settings → Apps → Special app access → Notification access → J.A.R.V.I.S → Allow
```

### 3. Display Over Other Apps *(for overlay features)*
```
Settings → Apps → J.A.R.V.I.S → Display over other apps → Allow
```

### 4. Battery Optimization — IMPORTANT!
To prevent Android from killing J.A.R.V.I.S:
```
Settings → Battery → Battery Optimization → All Apps → J.A.R.V.I.S → Don't optimize
```
On Samsung: Settings → Device Care → Battery → Background usage limits → Never sleeping apps → Add J.A.R.V.I.S

---

## 🎯 Formal Voice Samples

J.A.R.V.I.S speaks with the distinguished formality of White House serving staff:

| Situation | Speech |
|-----------|--------|
| Wake word detected | *"Yes Sir, how may I assist you?"* |
| Task complete | *"Consider it done, Sir."* |
| Setting alarm | *"Setting alarm for 7:00 AM, Sir. Very well."* |
| Dismissal | *"Very well, Sir. I shall remain on standby."* |
| Error | *"I'm afraid I encountered a difficulty, Sir. My apologies."* |
| Greeting on start | *"Good morning, Sir. J.A.R.V.I.S is now fully operational."* |
| Torch on | *"Torch activated, Sir."* |
| Bluetooth | *"Enabling Bluetooth at once, Sir."* |

The TTS uses **British English locale** at a slightly slower, lower-pitched tone for an authoritative, measured delivery.

---

## 🔧 Customization

### Change Wake Word
In `VoiceListenerService.java`, line ~20:
```java
private static final String WAKE_WORD = "jarvis";  // ← change this
```

### Change AI Personality
In `JarvisAI.java`, edit `SYSTEM_PROMPT` to adjust:
- Formality level
- Name (e.g., "Alfred", "Friday", "Edith")
- Language/accent preference
- Response length

### Change Voice
In `JarvisSpeech.java`:
```java
tts.setLanguage(Locale.UK);      // British English
tts.setSpeechRate(0.92f);        // Speed (1.0 = normal)
tts.setPitch(0.85f);             // Lower = deeper voice
```
For US accent: change `Locale.UK` to `Locale.US`

### Add Custom Commands
In `JarvisCommandProcessor.java`, in the `processCommand()` method:
```java
} else if (matchesAny(cmd, "your keyword", "alternative phrase")) {
    handleYourCustomCommand(cmd);
}
```
Then add the handler method below.

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|---------|
| Voice not recognized | Check RECORD_AUDIO permission; test on real device |
| Service keeps stopping | Disable battery optimization for the app |
| TTS not working | Check device has TTS engine: Settings → Accessibility → Text-to-speech |
| API key not working | Verify key at console.anthropic.com; check internet connection |
| App doesn't start on boot | Re-enable boot permission; check device-specific autostart settings |
| "Recognition not available" | Install Google app / Google Assistant on device |

---

## 📦 Dependencies

```gradle
// UI & Architecture
androidx.appcompat, constraintlayout, recyclerview, cardview
com.google.android.material (Material 3)
androidx.lifecycle (service lifecycle)
androidx.work (background scheduling)

// Networking (for AI API)
com.squareup.okhttp3:okhttp:4.12.0
com.google.code.gson:gson:2.10.1

// Animations
com.airbnb.android:lottie:6.4.0
```

---

## ⚡ Known Limitations

1. **Continuous listening** drains ~5-10% more battery. This is inherent to always-on voice assistants.
2. **Google SpeechRecognizer** requires an internet connection for recognition (offline mode requires additional library like Vosk).
3. On **Android 13+**, some settings toggles (WiFi on/off programmatically) require additional system-level permissions not available to third-party apps — the app opens the settings panel instead.
4. **Device control depth** varies by manufacturer (Samsung, Xiaomi, etc. have custom restrictions).

---

## 🛡️ Privacy

- Voice is processed by **Google's on-device/cloud SpeechRecognizer** — same as Google Assistant
- AI queries go to **Anthropic's API** over HTTPS — only your spoken questions, never audio
- No data is stored on any server by J.A.R.V.I.S itself
- API key is stored locally in Android `SharedPreferences` (private to the app)

---

*"I am at your service, Sir."*
*— J.A.R.V.I.S*
