# J.A.R.V.I.S ProGuard Rules
-keep class com.jarvis.assistant.** { *; }
-keep class com.jarvis.assistant.services.** { *; }
-keep class com.jarvis.assistant.receivers.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
