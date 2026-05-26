-keep class com.iptv.player.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**

# Volcano Engine RTC SDK
-keep class com.ss.bytertc.** { *; }
-keep class com.volcengine.** { *; }
-dontwarn com.ss.bytertc.**
