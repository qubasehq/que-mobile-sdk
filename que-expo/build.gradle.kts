plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.que.expo"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
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
    implementation(project(":que-core"))
    implementation(project(":que-platform-android"))
    implementation(project(":que-actions"))
    implementation(project(":que-llm"))
    implementation(project(":que-vision"))
    
    // React Native dependency (compileOnly as it's provided by the host app)
    compileOnly("com.facebook.react:react-android:+")
    
    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
