// Top-level build file where you can add configuration options common to all sub-projects/modules.
// NOTE: AGP 9+ has built-in Kotlin — no `kotlin-android` plugin needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}