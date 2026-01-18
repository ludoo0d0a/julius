package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
import com.llamatik.library.LlamaBridge

/**
 * EmbeddedAgent - On-device/offline LLM inference using Llamatik
 * 
 * This agent runs models locally without requiring API keys or network connectivity.
 * Models must be in GGUF format and placed in the app's assets folder or accessible file path.
 * 
 * Recommended models for Android:
 * - Phi-2 (Q4_0): ~1.6GB, good quality for small devices
 * - Gemma 2B (Q4_0): ~1.4GB, optimized for mobile
 * - TinyLlama (Q4_0): ~650MB, fastest but lower quality
 * 
 * Model files should be placed in: androidApp/src/main/assets/models/
 * Example: androidApp/src/main/assets/models/phi-2.Q4_0.gguf
 */
class EmbeddedAgent(
    private val modelPath: String = "models/phi-2.Q4_0.gguf"
) : ConversationalAgent {

    private var isModelInitialized = false
    private val systemPrompt = "You are a helpful and concise voice assistant. Provide clear, brief responses suitable for voice interaction."

    init {
        initializeModelIfNeeded()
    }

    private fun initializeModelIfNeeded() {
        if (!isModelInitialized) {
            try {
                // Get the full path to the model file
                val fullModelPath = LlamaBridge.getModelPath(modelPath)
                
                // Initialize the generation model
                isModelInitialized = LlamaBridge.initGenerateModel(fullModelPath)
                
                if (!isModelInitialized) {
                    throw IllegalStateException("Failed to initialize embedded model at: $fullModelPath")
                }
            } catch (e: Exception) {
                throw NetworkException(null, "Failed to load embedded model: ${e.message}. Make sure the model file exists at '$modelPath' in assets.")
            }
        }
    }

    override suspend fun process(input: String): AgentResponse {
        try {
            // Ensure model is initialized
            if (!isModelInitialized) {
                initializeModelIfNeeded()
            }

            // Generate response using the embedded model
            // Using generateWithContext for better conversational quality
            val response = LlamaBridge.generateWithContext(
                systemPrompt = systemPrompt,
                contextBlock = "", // Could be used for conversation history in future
                userPrompt = input
            )

            // Return text response (audio will be null, system TTS will be used)
            return AgentResponse(
                text = response.trim(),
                audio = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw NetworkException(null, "Error with embedded model: ${e.message}")
        }
    }

    /**
     * Cleanup resources when done. Call this when switching away from EmbeddedAgent
     * or when the app is closing.
     */
    fun shutdown() {
        if (isModelInitialized) {
            try {
                LlamaBridge.shutdown()
                isModelInitialized = false
            } catch (e: Exception) {
                // Ignore shutdown errors
                e.printStackTrace()
            }
        }
    }
}