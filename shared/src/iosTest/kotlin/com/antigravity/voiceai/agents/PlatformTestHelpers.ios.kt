package com.antigravity.voiceai.agents

import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.HttpClientEngine

actual fun createTestHttpClientEngine(): HttpClientEngine {
    return Darwin.create()
}

actual object TestPropertyReader {
    // On iOS, we can't easily read from local.properties files
    // So we only support environment variables or returning defaults
    actual fun getProperty(propertyName: String): String? {
        // On iOS, we could potentially read from environment variables if available
        // For now, return null to indicate property not found
        return null
    }

    actual fun getProperty(propertyName: String, default: String): String {
        return getProperty(propertyName) ?: default
    }
}
