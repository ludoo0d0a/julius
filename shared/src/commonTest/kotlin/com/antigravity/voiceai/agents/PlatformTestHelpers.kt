package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine

/**
 * Platform-specific HTTP client engine factory
 */
expect fun createTestHttpClientEngine(): HttpClientEngine

/**
 * Platform-specific property reader for test configuration
 */
expect object TestPropertyReader {
    fun getProperty(propertyName: String): String?
    fun getProperty(propertyName: String, default: String): String
}
