# ---- Retrofit ----
# Retrofit 2.9+ liefert eigene consumer rules; hier nur noch app-spezifische Attribute halten.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---- Gson ----
# Wir nutzen ausschließlich TypeToken.getParameterized(...) statt anonymer
# Subklassen — daher braucht Gson keine speziellen Klasse-Keeps mehr.
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- App API models (Gson-deserialisiert, niemals umbenennen!) ----
-keep class de.dhde.hannover.departures.widget.api.** { *; }
-keepclassmembers class de.dhde.hannover.departures.widget.api.** { *; }

# ---- App data models ----
-keep class de.dhde.hannover.departures.widget.data.** { *; }

# ---- Glance ActionCallbacks ----
# Werden vom Widget-System reflektiv über ihren Klassennamen instanziiert.
# Ohne diesen Keep landen Refresh, GPS-Toggle, Tab-Wechsel etc. ins Leere.
-keep class * implements androidx.glance.appwidget.action.ActionCallback

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
