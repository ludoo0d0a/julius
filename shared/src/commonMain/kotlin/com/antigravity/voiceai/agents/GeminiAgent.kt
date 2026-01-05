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
    private data class Res(val candidates: List<Candidate>?)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
         try {
            // Using Gemini 1.5 Flash (Free Tier eligible)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            
            val responseBody = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    Req(contents = listOf(
                        Content(parts = listOf(Part(text = input)))
                    ))
                )
            }.bodyAsText()

            val res = json.decodeFromString<Res>(responseBody)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "I didn't get a response."
            
            return AgentResponse(text, null) // Audio null = Use System TTS
        } catch (e: Exception) {
            e.printStackTrace()
            return AgentResponse("Error connecting to Gemini: ${e.message}", null)
        }
    }
}
