# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Phase 4: R8 keep rules for CameraX, Media3, Coil, ML Kit, DataStore
-keep class androidx.camera.** { *; }
-keep class androidx.camera.extensions.** { *; }
-keep class androidx.media3.** { *; }
-keep class coil.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class androidx.datastore.** { *; }
-keep class com.example.BuildConfig { *; }
-keep class com.example.CameraViewModel { *; }
-keep class com.example.data.** { *; }
-keep class androidx.profileinstaller.** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
