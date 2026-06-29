# Add project specific ProGuard rules here.
# You can control the set of applied rules using the
# proguardFiles setting in build.gradle.

# ============== Kotlin ==============
-dontwarn kotlin.**

# ============== Kotlinx Coroutines ==============
-dontwarn kotlinx.coroutines.**

# ============== Kotlinx Serialization ==============
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.shimulfp.hub2stream.**$$serializer { *; }
-keepclassmembers class com.shimulfp.hub2stream.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.shimulfp.hub2stream.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============== Jackson ==============
-dontwarn com.fasterxml.jackson.databind.**
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, EnclosingMethod

# ============== Media3 / ExoPlayer ==============
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============== FFmpeg JNI ==============
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclassmembers class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-keepclassmembers class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }

# ============== OkHttp ==============
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============== Jsoup ==============
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.annotations.**

# ============== Coil ==============
-keep class coil.** { *; }

# ============== Compose ==============
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============== Data Models (keep for serialization) ==============
-keep class com.shimulfp.hub2stream.models.** { *; }
-keep class com.shimulfp.hub2stream.extractor.models.** { *; }
-keep class com.shimulfp.hub2stream.extractor.** { *; }
-keepclassmembers class com.shimulfp.hub2stream.extractor.** {
    <fields>;
}

# ============== Keep fragment / activity ==============
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.activity.ComponentActivity
-keep public class * extends androidx.compose.ui.graphics.vector.ImageVector
