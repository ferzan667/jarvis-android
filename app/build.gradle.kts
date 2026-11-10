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
        versionCode = 6
        versionName = "0.3.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.github.msnilsen:openwakeword-android:0.1.2")
}


