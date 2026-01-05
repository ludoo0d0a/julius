import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.antigravity.voiceai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.antigravity.voiceai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        val elevenLabsKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("elevenlabs.key") ?: ""
            } else {
                ""
            }
        }
        val geminiKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("gemini.key") ?: ""
            } else {
                ""
            }
        }
        buildConfigField("String", "ELEVENLABS_KEY", "\"$elevenLabsKey\"")
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


}

dependencies {
    implementation(project(":shared"))
    
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Android Auto
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // DI
    implementation(libs.koin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}
