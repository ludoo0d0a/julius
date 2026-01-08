package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GeminiAgent(
    private val client: HttpClient,
    private val apiKey: String
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
         try {
            // Using Gemini 1.5 Flash (Free Tier eligible) - try v1beta with latest
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$apiKey"
            
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    Req(contents = listOf(
                        Content(parts = listOf(Part(text = input)))
                    ))
                )
            }

            val responseBody = response.bodyAsText()

            // Check for error response first
            if (response.status.value != 200) {
                try {
                    val errorRes = json.decodeFromString<ErrorRes>(responseBody)
                    return AgentResponse("Error connecting to Gemini: ${errorRes.error.message}", null)
                } catch (e: Exception) {
                    return AgentResponse("Error connecting to Gemini: ${response.status.value} - $responseBody", null)
                }
            }

            val res = json.decodeFromString<Res>(responseBody)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "I didn't get a response."
            
            return AgentResponse(text, null) // Audio null = Use System TTS
        } catch (e: Exception) {
            e.printStackTrace()
            return AgentResponse("Error connecting to Gemini: ${e.message}", null)
        }
    }
}
