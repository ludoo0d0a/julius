import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.google.services)
}

configure<ApplicationExtension> {
    namespace = "fr.geoking.julius"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.geoking.julius"
        minSdk = 26
        targetSdk = 35
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val ciRunAttempt = System.getenv("GITHUB_RUN_ATTEMPT")?.toIntOrNull() ?: 1
        val localProps = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }
        } ?: Properties()
        // Keys: local.properties first, then env (CI must set env on the step that runs Gradle, e.g. JULES_KEY, GOOGLE_MAPS_KEY)
        fun prop(key: String, default: String = "") =
            localProps.getProperty(key) ?: System.getenv(key) ?: default
        // Sanitize for Java string literal: trim, strip newlines, escape backslash and double-quote
        fun sanitizeBuildConfigString(s: String): String =
            s.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\r\n]+"), " ")
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
        buildConfigField("int", "VERSION_CODE", "$computedVersionCode")
        buildConfigField("String", "VERSION_NAME", "\"$computedVersionName\"")
        val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        val elevenLabsKey = sanitizeBuildConfigString(prop("ELEVENLABS_KEY"))
        val geminiKey = sanitizeBuildConfigString(prop("GEMINI_KEY"))
        val deepgramKey = sanitizeBuildConfigString(prop("DEEPGRAM_KEY"))
        val openaiKey = sanitizeBuildConfigString(prop("OPENAI_KEY"))
        val perplexityKey = sanitizeBuildConfigString(prop("PERPLEXITY_KEY"))
        val firebaseAiKey = sanitizeBuildConfigString(prop("FIREBASE_AI_KEY"))
        val firebaseAiModel = sanitizeBuildConfigString(prop("FIREBASE_AI_MODEL", "gemini-1.5-flash-latest"))
        val opencodeZenKey = sanitizeBuildConfigString(prop("OPENCODE_ZEN_KEY"))
        val completionsMeKey = sanitizeBuildConfigString(prop("COMPLETIONS_ME_KEY"))
        val apifreellmKey = sanitizeBuildConfigString(prop("APIFREELLM_KEY"))
        val deepseekKey = sanitizeBuildConfigString(prop("DEEPSEEK_KEY"))
        val groqKey = sanitizeBuildConfigString(prop("GROQ_KEY"))
        val openrouterKey = sanitizeBuildConfigString(prop("OPENROUTER_KEY"))
        val julesKey = sanitizeBuildConfigString(prop("JULES_KEY"))
        val githubToken = sanitizeBuildConfigString(prop("GITHUB_TOKEN"))
        val googleWebClientId = sanitizeBuildConfigString(prop("GOOGLE_WEB_CLIENT_ID", "your_web_client_id_placeholder"))
        val tomtomKey = sanitizeBuildConfigString(prop("TOMTOM_KEY"))
        val mapsApiKey = prop("GOOGLE_MAPS_KEY")
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey

        buildConfigField("String", "ELEVENLABS_KEY", "\"$elevenLabsKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "JULES_KEY", "\"$julesKey\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
        buildConfigField("String", "DEEPGRAM_KEY", "\"$deepgramKey\"")
        buildConfigField("String", "OPENAI_KEY", "\"$openaiKey\"")
        buildConfigField("String", "PERPLEXITY_KEY", "\"$perplexityKey\"")
        buildConfigField("String", "FIREBASE_AI_KEY", "\"$firebaseAiKey\"")
        buildConfigField("String", "FIREBASE_AI_MODEL", "\"$firebaseAiModel\"")
        buildConfigField("String", "OPENCODE_ZEN_KEY", "\"$opencodeZenKey\"")
        buildConfigField("String", "COMPLETIONS_ME_KEY", "\"$completionsMeKey\"")
        buildConfigField("String", "APIFREELLM_KEY", "\"$apifreellmKey\"")
        buildConfigField("String", "DEEPSEEK_KEY", "\"$deepseekKey\"")
        buildConfigField("String", "GROQ_KEY", "\"$groqKey\"")
        buildConfigField("String", "OPENROUTER_KEY", "\"$openrouterKey\"")
        buildConfigField("String", "TOMTOM_KEY", "\"$tomtomKey\"")

        // Required for Google Play Services Maps (references legacy Apache HTTP classes removed from Android 9+)
        useLibrary("org.apache.http.legacy")

        buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "false")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "false")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "true")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // Android Auto: home lists every screen for DHU / car testing; release uses a shorter hub.
            buildConfigField("boolean", "AUTO_DASHBOARD_DEV_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "AUTO_DASHBOARD_DEV_MODE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        // Baseline removed; keep lint clean instead.
    }

}

// Fichier de désobscurcissement (mapping R8) associé à l’App Bundle.
// Après un bundle*Release, le mapping est copié dans build/deobfuscation/
// pour upload Play Console ou crash reporting.
afterEvaluate {
    val appExt = extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
    val versionName = appExt.defaultConfig.versionName ?: "unknown"
    val copyMappings = tasks.register("copyReleaseMappings") {
        doLast {
            val buildDir = layout.buildDirectory.get().asFile
            val mappingRoot = buildDir.resolve("outputs/mapping")
            val destDir = buildDir.resolve("deobfuscation")
            if (!mappingRoot.isDirectory) return@doLast
            mappingRoot.listFiles()?.filter { it.isDirectory }?.forEach { variantDir ->
                val mappingFile = File(variantDir, "mapping.txt")
                if (mappingFile.exists()) {
                    destDir.mkdirs()
                    val dest = File(destDir, "mapping-${variantDir.name}-$versionName.txt")
                    mappingFile.copyTo(dest, overwrite = true)
                    logger.lifecycle("Mapping copié: ${dest.absolutePath}")
                }
            }
        }
    }
    tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Release") }.configureEach {
        finalizedBy(copyMappings)
    }
}

// ---- 16KB page size compatibility guard (Android 15+) ----
// Android apps that ship native 64-bit libraries must ensure ELF PT_LOAD segments have p_align >= 16KB.
// This task inspects embedded .so inside resolved AAR/JARs and fails fast if any are incompatible.
val checkNative16kPageSize = tasks.register("checkNative16kPageSize") {
    group = "verification"
    description = "Fails if any dependency ships 64-bit .so not compatible with 16KB page size."

    doLast {
        fun u16le(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
        fun u32le(b: ByteArray, o: Int): Long =
            (b[o].toLong() and 0xff) or
                ((b[o + 1].toLong() and 0xff) shl 8) or
                ((b[o + 2].toLong() and 0xff) shl 16) or
                ((b[o + 3].toLong() and 0xff) shl 24)

        fun u64le(b: ByteArray, o: Int): Long {
            val lo = u32le(b, o)
            val hi = u32le(b, o + 4)
            return lo or (hi shl 32)
        }

        fun isPowerOfTwo(x: Long): Boolean = x > 0 && (x and (x - 1)) == 0L

        fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
            val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
            val buf = ByteArray(32 * 1024)
            var remaining = maxBytes
            while (remaining > 0) {
                val r = input.read(buf, 0, minOf(buf.size, remaining))
                if (r <= 0) break
                out.write(buf, 0, r)
                remaining -= r
            }
            return out.toByteArray()
        }

        data class ElfCheckResult(val ok: Boolean, val reason: String)

        fun checkElf16kCompatible(bytes: ByteArray): ElfCheckResult {
            if (bytes.size < 64) return ElfCheckResult(ok = false, reason = "too small to be ELF")
            if (!(bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte())) {
                return ElfCheckResult(ok = false, reason = "not an ELF file")
            }
            val elfClass = bytes[4].toInt() and 0xff // 1=32-bit, 2=64-bit
            val elfData = bytes[5].toInt() and 0xff // 1=little, 2=big
            if (elfData != 1) return ElfCheckResult(ok = false, reason = "big-endian ELF not supported by checker")
            if (elfClass != 2) return ElfCheckResult(ok = true, reason = "32-bit ELF (ignored for 16KB check)")

            val ePhoff = u64le(bytes, 32).toInt()
            val ePhentsize = u16le(bytes, 54)
            val ePhnum = u16le(bytes, 56)
            if (ePhentsize <= 0 || ePhnum <= 0) return ElfCheckResult(ok = false, reason = "missing program headers")

            val phTableEnd = ePhoff + (ePhentsize * ePhnum)
            if (phTableEnd > bytes.size) {
                return ElfCheckResult(ok = false, reason = "ELF header table not fully present (need $phTableEnd bytes, have ${bytes.size})")
            }

            val requiredAlign = 16 * 1024L
            val badAlignments = mutableSetOf<Long>()
            for (i in 0 until ePhnum) {
                val base = ePhoff + i * ePhentsize
                // Elf64_Phdr layout:
                // p_type(4), p_flags(4), p_offset(8), p_vaddr(8), p_paddr(8), p_filesz(8), p_memsz(8), p_align(8)
                val pType = u32le(bytes, base).toInt()
                if (pType != 1) continue // PT_LOAD
                val pAlign = u64le(bytes, base + 48)
                if (pAlign < requiredAlign || !isPowerOfTwo(pAlign)) badAlignments.add(pAlign)
            }

            return if (badAlignments.isEmpty()) {
                ElfCheckResult(ok = true, reason = "ok")
            } else {
                ElfCheckResult(ok = false, reason = "PT_LOAD p_align not 16KB-compatible: ${badAlignments.sorted().joinToString()}")
            }
        }

        val artifacts = configurations.getByName("runtimeClasspath").resolvedConfiguration.resolvedArtifacts
        val problems = mutableListOf<String>()

        for (art in artifacts) {
            val file = art.file
            if (!file.isFile) continue
            val name = "${art.moduleVersion.id.group}:${art.name}:${art.moduleVersion.id.version}"
            val ext = file.extension.lowercase()
            if (ext != "aar" && ext != "jar") continue

            ZipFile(file).use { zip ->
                val entries = buildList<ZipEntry> {
                    val en = zip.entries()
                    while (en.hasMoreElements()) {
                        val e = en.nextElement()
                        if (!e.isDirectory && e.name.endsWith(".so")) add(e)
                    }
                }
                if (entries.isEmpty()) return@use
                for (e in entries) {
                    // Read enough to include ELF header + program headers; cap to 2MB to avoid OOM on large libs.
                    zip.getInputStream(e).use { input ->
                        val head = readAtMost(input, 2 * 1024 * 1024)
                        val r = checkElf16kCompatible(head)
                        if (!r.ok) {
                            problems += "$name -> ${e.name}: ${r.reason}"
                        }
                    }
                }
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Found native 64-bit libraries incompatible with 16KB page size (Android 15+):")
                    problems.sorted().forEach { appendLine(" - $it") }
                    appendLine()
                    appendLine("Fix: update/replace the offending dependency with a 16KB-page-size-compatible build (p_align >= 16384).")
                }
            )
        } else {
            logger.lifecycle("Native 16KB page size check: OK (no incompatible 64-bit .so found in runtimeClasspath)")
        }
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
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.compose.ui.tooling)

    // Android Auto
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // Location (replaces deprecated LocationManager.requestSingleUpdate)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.maplibre.android)
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

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.ksp)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Vosk offline STT (car mic path)
    implementation(libs.vosk.android)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Coil for loading API logos in About
    implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
    implementation("io.coil-kt.coil3:coil-network-okhttp:${libs.versions.coil.get()}")

    // Shizuku (optional) - allows reading full device logcat when user grants permission.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
}
android {
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

// Run the guard as part of `./gradlew :androidApp:check` and CI `check` pipelines.
tasks.matching { it.name == "check" }.configureEach {
    dependsOn(checkNative16kPageSize)
}

// ---- Firebase sanity check ----
// Fail fast when `google-services.json` is still the repo placeholder, since it causes
// runtime Firebase Auth errors like "API key not valid".
val verifyGoogleServicesJson = tasks.register("verifyGoogleServicesJson") {
    group = "verification"
    description = "Fails if androidApp/google-services.json contains placeholder Firebase values."

    doLast {
        val gs = project.file("google-services.json")
        if (!gs.exists()) {
            throw GradleException(
                "Missing google-services.json in :androidApp.\n" +
                    "Fix: Firebase Console → Project settings → Your apps (Android) → Download google-services.json\n" +
                    "and place it at androidApp/google-services.json (or androidApp/src/<flavor>/google-services.json)."
            )
        }

        val text = gs.readText()
        fun hasPlaceholder(s: String): Boolean =
            s.contains("placeholder", ignoreCase = true) ||
                s.contains("julius-ai-placeholder", ignoreCase = true) ||
                s.contains("123456789012") ||
                s.contains("abcdef1234567890", ignoreCase = true)

        if (hasPlaceholder(text)) {
            throw GradleException(
                "androidApp/google-services.json still contains placeholder Firebase values.\n" +
                    "This will break Firebase Auth (\"API key not valid\").\n\n" +
                    "Fix:\n" +
                    " - Firebase Console → Project settings → Your apps (Android)\n" +
                    " - Ensure package name is \"fr.geoking.julius\"\n" +
                    " - Download a fresh google-services.json\n" +
                    " - Replace androidApp/google-services.json with the downloaded file\n"
            )
        }
    }
}

// Ensure we fail before packaging / running the app.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyGoogleServicesJson)
}

