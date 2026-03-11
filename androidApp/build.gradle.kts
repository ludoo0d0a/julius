import com.android.build.api.dsl.ApplicationExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

configure<ApplicationExtension> {
    namespace = "fr.geoking.julius"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.geoking.julius"
        minSdk = 26
        targetSdk = 36
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val ciRunAttempt = System.getenv("GITHUB_RUN_ATTEMPT")?.toIntOrNull() ?: 1
        val localProps = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }
        } ?: Properties()
        // Keys: local.properties first, then env (CI must set env on the step that runs Gradle, e.g. JULES_KEY, GOOGLE_MAPS_KEY)
        fun prop(key: String, default: String = "") =
            localProps.getProperty(key) ?: System.getenv(key) ?: default
        val localVersionCode = prop("VERSION_CODE").takeIf { it.isNotEmpty() }?.toIntOrNull()
        val computedVersionCode = when {
            ciRunNumber != null -> (ciRunNumber * 10) + ciRunAttempt
            localVersionCode != null -> localVersionCode
            else -> 2
        }
        val computedVersionName = if (ciRunNumber != null) {
            "1.0.$ciRunNumber"
        } else {
            "1.0"
        }
        versionCode = computedVersionCode
        versionName = computedVersionName
        val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        val elevenLabsKey = prop("ELEVENLABS_KEY")
        val geminiKey = prop("GEMINI_KEY")
        val deepgramKey = prop("DEEPGRAM_KEY")
        val openaiKey = prop("OPENAI_KEY")
        val perplexityKey = prop("PERPLEXITY_KEY")
        val firebaseAiKey = prop("FIREBASE_AI_KEY")
        val firebaseAiModel = prop("FIREBASE_AI_MODEL", "gemini-1.5-flash-latest")
        val opencodeZenKey = prop("OPENCODE_ZEN_KEY")
        val completionsMeKey = prop("COMPLETIONS_ME_KEY")
        val apifreellmKey = prop("APIFREELLM_KEY")
        val julesKey = prop("JULES_KEY")
        val googleWebClientId = prop("GOOGLE_WEB_CLIENT_ID", "your_web_client_id_placeholder")
        val mapsApiKey = prop("GOOGLE_MAPS_KEY")
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey

        buildConfigField("String", "ELEVENLABS_KEY", "\"$elevenLabsKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "JULES_KEY", "\"$julesKey\"")
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
        buildConfigField("String", "DEEPGRAM_KEY", "\"$deepgramKey\"")
        buildConfigField("String", "OPENAI_KEY", "\"$openaiKey\"")
        buildConfigField("String", "PERPLEXITY_KEY", "\"$perplexityKey\"")
        buildConfigField("String", "FIREBASE_AI_KEY", "\"$firebaseAiKey\"")
        buildConfigField("String", "FIREBASE_AI_MODEL", "\"$firebaseAiModel\"")
        buildConfigField("String", "OPENCODE_ZEN_KEY", "\"$opencodeZenKey\"")
        buildConfigField("String", "COMPLETIONS_ME_KEY", "\"$completionsMeKey\"")
        buildConfigField("String", "APIFREELLM_KEY", "\"$apifreellmKey\"")

        // Required for Google Play Services Maps (references legacy Apache HTTP classes removed from Android 9+)
        useLibrary("org.apache.http.legacy")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "CAR_USE_SURFACE", "false")
        }
        create("phone") {
            dimension = "distribution"
            buildConfigField("boolean", "CAR_USE_SURFACE", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":shared"))
    
    // Compose & Activity (lifecycle-runtime ensures LifecycleOwner is on classpath for ComponentActivity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Android Auto
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // Maps
    implementation(libs.maps.compose)
    // Bundle Apache HTTP legacy classes for Play Services Maps Dynamite (removed from Android 9+ bootclasspath)
    implementation(libs.httpclient.android)

    // Media3 for Dashboard Tile
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.guava)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // DI
    implementation(libs.koin.android)

    // Play In-App Update (warns when update available; flexible flow)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)

    // Google Auth / Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
