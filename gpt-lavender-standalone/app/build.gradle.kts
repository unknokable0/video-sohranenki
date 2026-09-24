plugins {
    id("com.android.application")
}
android {
    namespace = "app.gptlavender.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.gptlavender.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
