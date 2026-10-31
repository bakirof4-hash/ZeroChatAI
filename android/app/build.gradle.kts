plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zerochat.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zerochat.ai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "BASE_URL", "\"https://zerochatai-1.onrender.com/\"")
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    // Resolve backend base URLs from Gradle properties (-Pzerochat.backend.url),
    // environment variables (ZEROCHAT_BACKEND_URL), or production HTTPS default.
    val prodBackendUrl = (project.findProperty("zerochat.backend.url") as? String)
        ?: System.getenv("ZEROCHAT_BACKEND_URL")
        ?: "https://zerochatai-1.onrender.com/"

    val emulatorBackendUrl = (project.findProperty("zerochat.emulator.url") as? String)
        ?: System.getenv("ZEROCHAT_EMULATOR_URL")
        ?: "http://10.0.2.2:8000/"

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BASE_URL", "\"$prodBackendUrl\"")
            buildConfigField("String", "PRODUCTION_URL", "\"$prodBackendUrl\"")
            buildConfigField("String", "EMULATOR_URL", "\"$emulatorBackendUrl\"")
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            // Debug build defaults to production HTTPS (or custom build-time override)
            // It NEVER hardcodes 10.0.2.2 for physical Android devices!
            buildConfigField("String", "BASE_URL", "\"$prodBackendUrl\"")
            buildConfigField("String", "PRODUCTION_URL", "\"$prodBackendUrl\"")
            buildConfigField("String", "EMULATOR_URL", "\"$emulatorBackendUrl\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation & ViewModel for Compose
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Secure Storage (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // QR Code Generation & Matrix (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

