// LumaOS Pro root build file. Plugins are declared here without being applied so that
// the single app module opts in explicitly (keeps the build graph shallow).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
