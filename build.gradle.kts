// Top-level build file. Plugins are declared here without being applied so the
// versions live in exactly one place (gradle/libs.versions.toml) and the module
// build files only have to name them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
