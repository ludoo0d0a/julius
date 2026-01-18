package com.antigravity.voiceai.agents

import com.antigravity.voiceai.IaModel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class GenkitAgent(
    private val client: HttpClient,
    private val endpoint: String,
    private val apiKey: String = ""
) : ConversationalAgent {

    @Serializable
    private data class GenkitReq(val input: String)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String, model: IaModel): AgentResponse {
        if (endpoint.isBlank()) {
            return AgentResponse("Genkit endpoint is required. Please set it in settings.", null)
        }

        return try {
            val response = client.post(endpoint) {
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
                contentType(ContentType.Application.Json)
                setBody(GenkitReq(input))
            }

            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                return AgentResponse("Error connecting to Genkit: ${response.status.value} - $responseBody", null)
            }

            val text = try {
                val element = json.parseToJsonElement(responseBody)
                extractText(element) ?: responseBody
            } catch (e: Exception) {
                responseBody
            }

            AgentResponse(text, null)
        } catch (e: Exception) {
            e.printStackTrace()
            AgentResponse("Error connecting to Genkit: ${e.message}", null)
        }
    }

    private fun extractText(element: JsonElement): String? {
        return when (element) {
            is JsonPrimitive -> if (element.isString) element.content else null
            is JsonArray -> element.firstNotNullOfOrNull { extractText(it) }
            is JsonObject -> {
                val keys = listOf("text", "output", "result", "response", "answer", "message")
                for (key in keys) {
                    val value = element[key]
                    val extracted = value?.let { extractText(it) }
                    if (!extracted.isNullOrBlank()) {
                        return extracted
                    }
                }
                element.values.firstNotNullOfOrNull { extractText(it) }
            }
            else -> null
        }
    }
}
