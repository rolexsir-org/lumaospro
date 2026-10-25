# ---------------------------------------------------------------------------
# LumaOS Pro release shrink rules
# ---------------------------------------------------------------------------

# Application entry point is referenced from the manifest only.
-keep class com.lumaos.pro.launcher.core.LumaApp { <init>(); }

# Activities are started by name from the manifest / intents.
-keep public class * extends android.app.Activity

# DataStore Preferences needs its serializer reachable.
-keepclassmembers class * extends androidx.datastore.core.Serializer { *; }

# WorkManager instantiates workers reflectively.
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker { *; }

# Any model type written to DataStore or diagnostics must keep its members.
-keepclassmembers class com.lumaos.pro.launcher.**.model.** { *; }

# Choreographer callbacks are installed from Kotlin; keep the lambda targets.
-keepclassmembers class com.lumaos.pro.launcher.intelligence.** {
    *** *(...);
}

# Remove our debug logging from release builds. LumaLog already compiles out
# via BuildConfig.DEBUG at the call site; this is the backstop.
-assumenosideeffects class com.lumaos.pro.launcher.core.util.LumaLog {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep Kotlin metadata so reflection-based tooling (and R8's own Kotlin
# handling of default arguments) stays correct.
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }

# Warn而不是 fail if a rule references a class that shrinking removed.
-dontnote kotlinx.coroutines.**
