// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Firebase plugin - ADICIONAR ESTA LINHA
        classpath("com.google.gms:google-services:4.4.0")

        // Firebase Crashlytics plugin (opcional)
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Firebase plugin - ADICIONAR ESTA LINHA
    id("com.google.gms.google-services") version "4.4.0" apply false
}