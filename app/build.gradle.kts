plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.utsk.gs6builder"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.utsk.gs6builder"
        minSdk = 29          // MediaStore.Downloads needs no storage permission from here up
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}

chaquopy {
    defaultConfig {
        version = "3.11"           
        buildPython("python3")    
        extractPackages("archspec")   // archspec reads ../json/cpu/*.json via __file__, so it must live on disk
        pip {
            options("--find-links", rootProject.file("wheels").absolutePath)
            install("-r", "requirements-android.txt")
        }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}