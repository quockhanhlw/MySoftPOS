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

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# =============================================================================
# MySoftPOS ProGuard Rules (POS/ISO8583 Application)
# =============================================================================

# ===== CRASH REPORTING: Keep line numbers for stack traces =====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses

# ===== RETROFIT + OKHTTP =====
# Keep Retrofit service interfaces (methods are accessed via reflection)
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ===== GSON (used by Retrofit converter) =====
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all API DTOs (inner classes of ApiService used by Gson)
-keep class com.example.mysoftpos.data.remote.api.ApiService$* { *; }
-keep class com.example.mysoftpos.domain.model.** { *; }

# ===== ROOM DATABASE =====
# Keep Room entities and DAOs (accessed via generated code)
-keep class com.example.mysoftpos.data.local.entity.** { *; }
-keep class com.example.mysoftpos.data.local.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ===== ISO8583 PROTOCOL =====
# Keep ISO8583 model/spec classes (may use reflection for field mapping)
-keep class com.example.mysoftpos.iso8583.** { *; }


# ===== WORKMANAGER =====
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ===== VIEWMODELS =====
# ViewModels are created via reflection in ViewModelProvider.Factory
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ===== GENERAL ANDROID =====
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
