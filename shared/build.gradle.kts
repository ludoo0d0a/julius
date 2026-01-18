import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

kotlin {
    androidLibrary {
        namespace = "com.antigravity.voiceai.shared"
        compileSdk = 36
        minSdk = 26
        
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    
    // JVM target for running tests on desktop (MacBook Intel x64)
    jvm("desktop") {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            // Show all test results (not just failures)
            testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = true
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
    
    // Simple iOS target configuration for KMP
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "Shared"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.library)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Configure publishing to avoid archives configuration deprecation
// Note: The Kotlin Multiplatform plugin still uses 'archives' internally.
// This warning will be resolved when the plugin is updated for full Gradle 9 compatibility.
publishing {
    publications {
        withType<MavenPublication>().configureEach {
            // Use default publications created by Kotlin Multiplatform
        }
    }
}

// Note: The --tests flag doesn't work reliably with Kotlin Multiplatform JVM targets
// because test names include the target suffix [desktop] (e.g., "GeminiRealApiTests[desktop]").
//
// To run GeminiRealApiTests, simply run all desktop tests - they all execute:
//   ./gradlew :shared:desktopTest
//
// Or use custom tasks below to run specific tests:

// Custom task to run Gemini tests (all methods in GeminiRealApiTests)
// Note: Filtering by specific method doesn't work with Kotlin Multiplatform
// This task runs all GeminiRealApiTests methods
tasks.register("desktopTestGemini", Test::class) {
    group = "verification"
    description = "Runs all GeminiRealApiTests on desktop target"
    
    val desktopTarget = kotlin.targets.getByName("desktop")
    val testCompilation = desktopTarget.compilations.getByName("test")
    
    testClassesDirs = testCompilation.output.classesDirs
    classpath = testCompilation.runtimeDependencyFiles ?: files()
    
    useJUnitPlatform()
    
    // No filter - runs all GeminiRealApiTests (including testListModels)
    // To see only testListModels output, filter the console output:
    // ./gradlew :shared:desktopTestGemini 2>&1 | grep -i "testListModels\|ListModels Response"
}
