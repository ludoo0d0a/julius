package fr.geoking.julius.api.claude

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for the Claude API GA endpoints.
 * See https://platform.claude.com/docs/en/api/overview
 *
 * - Messages API: POST /v1/messages
 * - Token Counting API: POST /v1/messages/count_tokens
 * - Models API: GET /v1/models, GET /v1/models/{model_id}
 */
class ClaudeClient(
    private val client: HttpClient,
    private val baseUrl: String = ClaudeHttp.BASE_URL
) {
    private val json = ClaudeHttp.json

    // --- Messages API --- https://platform.claude.com/docs/en/api/messages/create

    /** Message role in the Messages API (no system role in messages — use top-level `system`). */
    object MessageRole {
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }

    /** Documented stop_reason values on Message responses. */
    object StopReason {
        const val END_TURN = "end_turn"
        const val MAX_TOKENS = "max_tokens"
        const val STOP_SEQUENCE = "stop_sequence"
        const val TOOL_USE = "tool_use"
        const val PAUSE_TURN = "pause_turn"
        const val REFUSAL = "refusal"
    }

    @Serializable
    data class MessageParam(
        val role: String,
        val content: String
    )

    @Serializable
    data class CreateMessageRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<MessageParam>,
        val system: String? = null,
        val temperature: Double? = null,
        val stream: Boolean? = null
    )

    @Serializable
    data class TextContentBlock(
        val type: String = "text",
        val text: String = ""
    )

    @Serializable
    data class MessageUsage(
        val input_tokens: Int = 0,
        val output_tokens: Int = 0,
        @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int? = null,
        @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int? = null
    )

    /** Message — https://platform.claude.com/docs/en/api/messages/create (Returns) */
    @Serializable
    data class Message(
        val id: String = "",
        val type: String = "",
        val role: String = "",
        val model: String = "",
        val content: List<TextContentBlock> = emptyList(),
        val stop_reason: String? = null,
        val stop_sequence: String? = null,
        val usage: MessageUsage? = null
    )

    suspend fun createMessage(
        apiKey: String,
        model: String,
        messages: List<MessageParam>,
        maxTokens: Int,
        system: String? = null,
        temperature: Double? = null
    ): Message {
        val body = CreateMessageRequest(
            model = model,
            max_tokens = maxTokens,
            messages = messages,
            system = system,
            temperature = temperature
        )
        val url = "$baseUrl/v1/messages"
        val bodyStr = json.encodeToString(CreateMessageRequest.serializer(), body)
        val response = client.post(url) {
            applyClaudeApiHeaders(apiKey)
            setBody(bodyStr)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            Message.serializer(),
            "Claude",
            requestBody = bodyStr
        )
    }

    /** Convenience: single user turn → assistant Message. */
    suspend fun sendUserMessage(
        apiKey: String,
        model: String,
        prompt: String,
        maxTokens: Int = 1024,
        system: String? = null,
        temperature: Double? = null
    ): Message = createMessage(
        apiKey = apiKey,
        model = model,
        messages = listOf(MessageParam(role = MessageRole.USER, content = prompt)),
        maxTokens = maxTokens,
        system = system,
        temperature = temperature
    )

    // --- Token Counting API --- https://platform.claude.com/docs/en/api/messages/count_tokens

    @Serializable
    data class CountTokensRequest(
        val model: String,
        val messages: List<MessageParam>,
        val system: String? = null
    )

    @Serializable
    data class CountTokensResponse(
        val input_tokens: Int = 0
    )

    suspend fun countTokens(
        apiKey: String,
        model: String,
        messages: List<MessageParam>,
        system: String? = null
    ): CountTokensResponse {
        val body = CountTokensRequest(model = model, messages = messages, system = system)
        val url = "$baseUrl/v1/messages/count_tokens"
        val bodyStr = json.encodeToString(CountTokensRequest.serializer(), body)
        val response = client.post(url) {
            applyClaudeApiHeaders(apiKey)
            setBody(bodyStr)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            CountTokensResponse.serializer(),
            "Claude",
            requestBody = bodyStr
        )
    }

    // --- Models API --- https://platform.claude.com/docs/en/api/models/list

    @Serializable
    data class ModelInfo(
        val id: String = "",
        val type: String = "",
        @SerialName("display_name") val displayName: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("max_input_tokens") val maxInputTokens: Int? = null,
        @SerialName("max_tokens") val maxTokens: Int? = null
    )

    @Serializable
    data class ListModelsResponse(
        val data: List<ModelInfo> = emptyList(),
        @SerialName("first_id") val firstId: String? = null,
        @SerialName("last_id") val lastId: String? = null,
        @SerialName("has_more") val hasMore: Boolean = false
    )

    suspend fun listModels(
        apiKey: String,
        limit: Int? = null,
        afterId: String? = null,
        beforeId: String? = null
    ): ListModelsResponse {
        val url = buildString {
            append("$baseUrl/v1/models")
            val params = buildList {
                if (limit != null) add("limit=$limit")
                if (afterId != null) add("after_id=$afterId")
                if (beforeId != null) add("before_id=$beforeId")
            }
            if (params.isNotEmpty()) append("?${params.joinToString("&")}")
        }
        val response = client.get(url) {
            applyClaudeApiHeaders(apiKey)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ListModelsResponse.serializer(),
            "Claude"
        )
    }

    suspend fun getModel(apiKey: String, modelId: String): ModelInfo {
        val url = "$baseUrl/v1/models/$modelId"
        val response = client.get(url) {
            applyClaudeApiHeaders(apiKey)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ModelInfo.serializer(),
            "Claude"
        )
    }
}

/** Extract visible text from a Message response (text blocks only). */
fun ClaudeClient.Message.extractText(): String =
    content.filter { it.type == "text" && it.text.isNotBlank() }
        .joinToString("\n") { it.text }
