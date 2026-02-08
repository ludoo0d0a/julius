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
        namespace = "fr.geoking.julius.shared"
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
            implementation(libs.google.genai)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.google.genai)
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

// Debug task to run GeminiDebug.main() - useful for debugging GeminiAgent.listModels() and process()
tasks.register<JavaExec>("desktopRunDebug") {
    group = "application"
    description = "Runs GeminiDebug.main() for debugging GeminiAgent.listModels() and process()"
    
    val desktopTarget = kotlin.targets.getByName("desktop")
    val mainCompilation = desktopTarget.compilations.getByName("main")
    
    classpath = mainCompilation.runtimeDependencyFiles ?: files()
    classpath += mainCompilation.output.allOutputs
    
    // Set main class
    mainClass.set("fr.geoking.julius.debug.GeminiDebugKt")
    
    // Set working directory to project root so it can find local.properties
    workingDir = rootProject.projectDir
    
    // Forward system properties and environment variables
    val props = System.getProperties().entries.associate { it.key.toString() to it.value }
    systemProperties(props)
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
