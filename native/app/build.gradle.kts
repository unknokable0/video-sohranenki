plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val telegramApiId = System.getenv("TELEGRAM_API_ID") ?: "0"
val telegramApiHash = (System.getenv("TELEGRAM_API_HASH") ?: "")
    .replace("\\", "\\\\")
    .replace(""", "\\"")

android {
    namespace = "com.unknokable.videosohranenki"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.unknokable.videosohranenki"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("io.github.tdlib-android:ktx:0.1.1")
}
