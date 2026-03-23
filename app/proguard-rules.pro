# Mantener clases de WebView y JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Mantener MediaSession
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }

# Mantener el servicio
-keep class com.mediaplayer.brave.MediaPlaybackService { *; }
-keep class com.mediaplayer.brave.MainActivity { *; }
