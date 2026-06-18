package fr.geoking.julius.api.claude

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Shared HTTP helpers for the [Claude API](https://platform.claude.com/docs/en/api/overview).
 */
object ClaudeHttp {
    const val BASE_URL = "https://api.anthropic.com"
    const val API_VERSION = "2023-06-01"
    const val MANAGED_AGENTS_BETA = "managed-agents-2026-04-01"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun requireApiKey(apiKey: String, provider: String): String {
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "Anthropic API key is required. Get one at platform.claude.com Settings.",
                provider = provider
            )
        }
        return apiKey
    }

    fun <T> decodeOrThrow(
        body: String,
        status: Int,
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        provider: String,
        requestBody: String? = null
    ): T {
        if (status !in 200..299) throw networkError(status, body, url, provider, requestBody)
        return json.decodeFromString(serializer, body)
    }

    fun networkError(
        httpCode: Int,
        body: String,
        url: String,
        provider: String,
        requestBody: String? = null
    ) = NetworkException(
        httpCode = httpCode,
        message = "$provider: $body",
        url = url,
        provider = provider,
        requestBody = requestBody
    )
}

internal fun HttpRequestBuilder.applyClaudeApiHeaders(apiKey: String) {
    header("x-api-key", ClaudeHttp.requireApiKey(apiKey, "Claude"))
    header("anthropic-version", ClaudeHttp.API_VERSION)
    contentType(ContentType.Application.Json)
}

internal fun HttpRequestBuilder.applyManagedAgentsHeaders(apiKey: String) {
    applyClaudeApiHeaders(apiKey)
    header("anthropic-beta", ClaudeHttp.MANAGED_AGENTS_BETA)
}
