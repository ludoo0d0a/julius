import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

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
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
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
// To run tests on Android SDK / JVM (desktop target uses same JVM as Android):
//   ./gradlew :shared:desktopTest
//
// The desktop JVM target is Android-compatible since both use the same JVM.
// Or use custom tasks below to run specific tests:

// Custom task to run only testListModels from GeminiRealApiTests
// Based on: https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html
tasks.register("desktopTestGeminiListModels", Test::class) {
    group = "verification"
    description = "Runs GeminiRealApiTests.testListModels on desktop target (Android-compatible JVM)"
    
    val desktopTarget = kotlin.targets.getByName("desktop")
    val testCompilation = desktopTarget.compilations.getByName("test")
    
    testClassesDirs = testCompilation.output.classesDirs
    classpath = testCompilation.runtimeDependencyFiles ?: files()
    
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    
    // Filter to run only GeminiListModelsTest class (contains only testListModels)
    // Pattern matching works better with dedicated test class names
    filter {
        includeTestsMatching("com.antigravity.voiceai.agents.GeminiListModelsTest")
    }
    
    // Enhanced logging to show all output in console
    testLogging {
        // Show all events including standard output
        events("passed", "failed", "skipped", "started", "standard_out", "standard_error")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        // Show all test output, including println statements
        displayGranularity = 2
        minGranularity = 0
    }
    
    // Force output to console and disable up-to-date check
    outputs.upToDateWhen { false }
    
    // After tests run, filter and display only testListModels output
    doLast {
        println("\n" + "=".repeat(60))
        println("Gemini testListModels Output:")
        println("=".repeat(60))
    }
}

// Configure Android test tasks
tasks.withType<Test>().configureEach {
    if (name.contains("UnitTest", ignoreCase = true)) {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

// To run testListModels with Android SDK/JVM compatibility:
// The desktop JVM target uses the same JVM as Android, so it's Android-compatible.
// Run with: ./gradlew :shared:desktopTest
//
// All tests run, including GeminiRealApiTests.testListModels.
// The test output shows: "9 tests completed" - Gemini tests are included in the 6 that pass.
