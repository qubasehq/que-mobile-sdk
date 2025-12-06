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
}

dependencies {
    implementation(project(":que-core"))
    implementation(project(":que-platform-android"))
    implementation(project(":que-llm"))
    
    // React Native dependency (compileOnly as it's provided by the host app)
    compileOnly("com.facebook.react:react-android:0.73.0")
}
