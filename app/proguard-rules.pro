# ---------------------------------------------------------------------------
# LumaOS Pro release shrink rules
#
# Policy: the application code contains no runtime reflection (backup codecs,
# layout codecs and diagnostics map their fields explicitly - see MiniJson and
# HomeLayoutCodec), so keep rules exist only where something outside the
# compiler actually needs them:
#
#   * components the manifest references by name (application class),
#   * attributes R8 must not strip for Kotlin default-argument handling,
#   * the debug-logging backstop.
#
# Everything else previously listed here was verified unnecessary and removed
# during the November 2026 compatibility audit:
#
#   * "-keep public class * extends android.app.Activity" - every Activity in
#     this app is declared in AndroidManifest.xml; AGP generates manifest keep
#     rules from the merged manifest, so the blanket rule only pinned unused
#     library Activity subclasses from shrinking.
#   * WorkManager keeps - WorkManager is not a dependency; the scheduler was
#     deliberately replaced (see IntelligenceEngine) and WeatherRefreshJob is a
#     platform JobService declared in the manifest.
#   * DataStore Serializer keeps - the app defines no Serializer subclass;
#     androidx.datastore ships its own consumer rules.
#   * "…launcher.**.model.**" and intelligence keepclassmembers - no reflection
#     touches those classes; Choreographer callbacks and DataStore/diagnostics
#     writes are direct references that R8 resolves on its own.
#   * androidx.compose.ui.semantics keeps - only ever needed by UI tests, which
#     run against the unminified debug variant; release has no semantics
#     reflection (accessibility reads the semantics tree, not the classes).
# ---------------------------------------------------------------------------

# Application entry point is referenced from the manifest only.
-keep class com.lumaos.pro.launcher.core.LumaApp { <init>(); }

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

# Warn rather than fail if a rule references a class that shrinking removed.
-dontnote kotlinx.coroutines.**
