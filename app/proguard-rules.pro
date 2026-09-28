# El build de release no usa minificación (isMinifyEnabled = false),
# pero dejamos estas reglas por si algún día se activa.
-keep class com.bibliavoz.app.player.PlaybackService { *; }
-keepclassmembers class * extends android.speech.tts.UtteranceProgressListener { *; }
-dontwarn org.jetbrains.annotations.**
