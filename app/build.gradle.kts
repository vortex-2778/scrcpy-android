plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.scrcpy.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scrcpy.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("ci") {
            // Filled from environment when present (CI)
            val ksPath = System.getenv("SCRCAPY_KEYSTORE")
            if (ksPath != null && File(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("SCRCAPY_KEYSTORE_PASS") ?: "android"
                keyAlias = System.getenv("SCRCAPY_KEY_ALIAS") ?: "scrcpyandroid"
                keyPassword = System.getenv("SCRCAPY_KEY_PASS") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ksPath = System.getenv("SCRCAPY_KEYSTORE")
            if (ksPath != null && File(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
