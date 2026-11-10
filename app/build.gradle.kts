plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.razen.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.razen.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.7.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}





