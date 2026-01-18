package com.antigravity.voiceai.agents

import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.HttpClientEngine
import platform.posix.getenv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

actual fun createTestHttpClientEngine(): HttpClientEngine {
    return Darwin.create()
}

actual object TestPropertyReader {
    // On iOS, we can't easily read from local.properties files
    // So we support environment variables (set before running tests)
    // To run tests with API key:
    // export gemini.key=your_api_key
    // ./gradlew :shared:iosX64Test
    @OptIn(ExperimentalForeignApi::class)
    actual fun getProperty(propertyName: String): String? {
        // Try to read from environment variable
        val envValue = getenv(propertyName)?.toKString()
        if (envValue != null && envValue.isNotEmpty()) {
            println("Found API key for $propertyName from environment variable")
            return envValue
        }
        return null
    }

    actual fun getProperty(propertyName: String, default: String): String {
        return getProperty(propertyName) ?: default
    }
}
