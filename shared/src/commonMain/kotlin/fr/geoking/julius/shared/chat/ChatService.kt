package fr.geoking.julius.shared.chat

interface ChatService {
    suspend fun sendMessage(message: String, model: String): String
}
