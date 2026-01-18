package com.antigravity.voiceai.agents

import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.HttpClientEngine
import java.io.File
import java.util.Properties

actual fun createTestHttpClientEngine(): HttpClientEngine {
    return OkHttp.create()
}

actual object TestPropertyReader {
    actual fun getProperty(propertyName: String): String? {
        // Try system property first
        System.getProperty(propertyName)?.let { return it }

        // Try to read from local.properties - look in project root
        try {
            val possiblePaths = listOf(
                File("local.properties"),  // Current dir
                File("../local.properties"),  // Parent dir
                File("../../local.properties"),  // Grandparent dir
                File("../../../local.properties")  // Great-grandparent dir
            )

            for (localPropsFile in possiblePaths) {
                if (localPropsFile.exists() && localPropsFile.isFile) {
                    val props = Properties()
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

        return null
    }

    actual fun getProperty(propertyName: String, default: String): String {
        return getProperty(propertyName) ?: default
    }
}
