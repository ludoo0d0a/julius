package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

open class RealApiTestBase {
    protected fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    // Helper to read API keys from local.properties or system properties
    protected fun getApiKey(propertyName: String, default: String = ""): String {
        // Try system property first
        System.getProperty(propertyName)?.let { return it }

        // Try to read from local.properties - look in project root
        try {
            // Try multiple possible paths
            val possiblePaths = listOf(
                java.io.File("local.properties"),  // Current dir
                java.io.File("../local.properties"),  // Parent dir
                java.io.File("../../local.properties"),  // Grandparent dir
                java.io.File("../../../local.properties")  // Great-grandparent dir
            )

            for (localPropsFile in possiblePaths) {
                if (localPropsFile.exists() && localPropsFile.isFile) {
                    val props = java.util.Properties()
                    localPropsFile.inputStream().use { props.load(it) }
                    val value = props.getProperty(propertyName)?.trim()
                    if (!value.isNullOrEmpty()) {
                        println("Found API key for $propertyName from ${localPropsFile.absolutePath}")
                        return value
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if file doesn't exist or can't be read
            println("Error reading local.properties: ${e.message}")
        }

        return default
    }

    protected fun defaultPrompt(): String = "donne la météo de demain"
}
