plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

group = "com.que.platform"
version = "1.0.0"


android {
    namespace = "com.que.platform.android"
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
    lint {
        disable += "ProtectedPermissions"
    }
}

dependencies {
    implementation(project(":que-core"))
    implementation(project(":que-vision"))
    implementation(project(":que-actions"))
    implementation(project(":que-llm"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
}
