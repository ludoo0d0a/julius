package fr.geoking.julius.agents

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FirebaseAIAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash-latest",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) : ConversationalAgent {

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class Content(val parts: List<Part>, val role: String = "user")

    @Serializable
    private data class Req(val contents: List<Content>)

    @Serializable
    private data class Candidate(val content: Content)

    @Serializable
    private data class ErrorDetail(val message: String, val status: String)

    @Serializable
    private data class ErrorRes(val error: ErrorDetail)

    @Serializable
    private data class Res(val candidates: List<Candidate>?)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            return AgentResponse("Firebase AI key is required. Please set it in settings.", null)
        }

        return try {
            val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    Req(
                        contents = listOf(
                            Content(parts = listOf(Part(text = input)))
                        )
                    )
                )
            }

            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                return try {
                    val errorRes = json.decodeFromString<ErrorRes>(responseBody)
                    AgentResponse("Error connecting to Firebase AI: ${errorRes.error.message}", null)
                } catch (e: Exception) {
                    AgentResponse("Error connecting to Firebase AI: ${response.status.value} - $responseBody", null)
                }
            }

            val res = json.decodeFromString<Res>(responseBody)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I didn't get a response."

            AgentResponse(text, null)
        } catch (e: Exception) {
            e.printStackTrace()
            AgentResponse("Error connecting to Firebase AI: ${e.message}", null)
        }
    }
}
