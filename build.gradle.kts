buildscript {
    dependencies {
        // AGP 9 has Kotlin built in and defaults to an older Kotlin Gradle plugin. Pinning it here
        // keeps the Kotlin compiler and the Compose compiler plugin on the same version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
