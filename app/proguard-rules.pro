# El build de release no usa minificación (isMinifyEnabled = false),
# pero dejamos estas reglas por si algún día se activa.
-keep class com.bibliavoz.app.player.PlaybackService { *; }
-dontwarn org.jetbrains.annotations.**
