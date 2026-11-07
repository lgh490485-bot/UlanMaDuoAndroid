plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ulanmaduo.desktop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ulanmaduo.desktop"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.7.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
