package com.antigravity.voiceai.shared

interface ChatService {
    suspend fun sendMessage(message: String): String
}
