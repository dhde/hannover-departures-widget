# ---- Retrofit ----
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit Coroutines Support
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Gson ----
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
# Preserve fields annotated with @SerializedName (critical for Gson reflection!)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Preserve generic type info for TypeToken (used in StopsRepository for List<StationSearchResult>)
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.reflect.TypeToken { *; }

# ---- App API models (Gson-deserialisiert, niemals umbenennen!) ----
-keep class de.dhde.hannover.departures.widget.api.** { *; }
-keepclassmembers class de.dhde.hannover.departures.widget.api.** { *; }

# ---- App data models ----
-keep class de.dhde.hannover.departures.widget.data.** { *; }

# ---- Glance / AppWidgets ----
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# ---- WorkManager ----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- DataStore / Kotlin Serialization ----
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Kotlin Coroutines ----
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- Google Play Services (Location) ----
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
