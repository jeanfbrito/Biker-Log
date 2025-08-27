# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve sensor-related classes
-keep class android.hardware.** { *; }
-keep class android.location.** { *; }

# OpenCSV
-keep class com.opencsv.** { *; }
-keepattributes *Annotation*

# OpenCSV dependencies - ignore missing logging libraries
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.logging.**

# Keep Apache Commons Logging classes if they exist
-keep class org.apache.commons.logging.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep our app's classes
-keep class com.motosensorlogger.** { *; }