# Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses

# Gson rules
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe

# App specific: Keep API models
-keep class de.dhde.hannover.departures.widget.api.** { *; }
-keepclassmembers class de.dhde.hannover.departures.widget.api.** { *; }

# Glance / AppWidgets
-keep class androidx.glance.** { *; }
