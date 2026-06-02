# Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# TCC native code
-keep class com.tcc.app.** { *; }

# Keep ProotInstaller data classes (used with reflection by Kotlin)
-keep class com.tcc.app.ProotInstaller$InstallResult { *; }

# Don't warn about missing annotations
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Keep Kotlin metadata for serialization
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
