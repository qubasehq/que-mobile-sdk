plugins {
    alias(libs.plugins.kotlinAndroid) // Using Android plugin to support Android-specific types if needed, or just kotlin-jvm
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.que.core"
version = "1.0.0"


android {
    namespace = "com.que.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Removed test dependencies as requested
}