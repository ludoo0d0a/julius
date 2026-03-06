import com.android.build.api.dsl.ApplicationExtension
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
        }
        val localVersionCode = localProps?.getProperty("version.code")?.toIntOrNull()
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
        val deepgramKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("deepgram.key") ?: ""
            } else {
                ""
            }
        }
        val openaiKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("openai.key") ?: ""
            } else {
                ""
            }
        }
        val perplexityKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("perplexity.key") ?: ""
            } else {
                ""
            }
        }
        val firebaseAiKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("firebaseai.key") ?: ""
            } else {
                ""
            }
        }
        val firebaseAiModel = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("firebaseai.model") ?: "gemini-1.5-flash-latest"
            } else {
                "gemini-1.5-flash-latest"
            }
        }
        val opencodeZenKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("opencodezen.key") ?: ""
            } else ""
        }
        val completionsMeKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("completionsme.key") ?: ""
            } else ""
        }
        val apifreellmKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("apifreellm.key") ?: ""
            } else ""
        }

        val mapsApiKey = project.rootProject.file("local.properties").let { file ->
            if (file.exists()) {
                val properties = Properties()
                properties.load(file.inputStream())
                properties.getProperty("google.maps.key") ?: ""
            } else {
                ""
            }
        }
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey

        buildConfigField("String", "ELEVENLABS_KEY", "\"$elevenLabsKey\"")
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
        buildConfigField("String", "DEEPGRAM_KEY", "\"$deepgramKey\"")
        buildConfigField("String", "OPENAI_KEY", "\"$openaiKey\"")
        buildConfigField("String", "PERPLEXITY_KEY", "\"$perplexityKey\"")
        buildConfigField("String", "FIREBASE_AI_KEY", "\"$firebaseAiKey\"")
        buildConfigField("String", "FIREBASE_AI_MODEL", "\"$firebaseAiModel\"")
        buildConfigField("String", "OPENCODE_ZEN_KEY", "\"$opencodeZenKey\"")
        buildConfigField("String", "COMPLETIONS_ME_KEY", "\"$completionsMeKey\"")
        buildConfigField("String", "APIFREELLM_KEY", "\"$apifreellmKey\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "CAR_USE_SURFACE", "false")
        }
        create("full") {
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
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Android Auto
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // Maps
    implementation(libs.maps.compose)

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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
