import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

group = "com.que.platform"
version = "1.0.0"


android {
    namespace = "com.que.platform.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        
        // Load Porcupine key from local.properties or Env
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        val porcupineKey = if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
            properties.getProperty("PORCUPINE_ACCESS_KEY", "")
        } else {
            System.getenv("PORCUPINE_ACCESS_KEY") ?: ""
        }
        
        buildConfigField("String", "PORCUPINE_ACCESS_KEY", "\"$porcupineKey\"")
    }
    
    buildFeatures {
        buildConfig = true
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

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":que-core"))
    implementation(project(":que-vision"))
    implementation(project(":que-actions"))
    implementation(project(":que-llm"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("ai.picovoice:porcupine-android:4.0.0")
    
    // Local LLM: llama.cpp JNI wrapper for on-device GGUF inference
    implementation("de.kherud:llama:4.2.0")
    // OkHttp for downloading models from HuggingFace
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // ObjectBox
    api("io.objectbox:objectbox-android:4.0.3")
    api("io.objectbox:objectbox-kotlin:4.0.3")
    api("io.objectbox:objectbox-java:4.0.3")
    kapt("io.objectbox:objectbox-processor:4.0.3")
}
