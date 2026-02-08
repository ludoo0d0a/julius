package fr.geoking.julius.shared

interface ChatService {
    suspend fun sendMessage(message: String, model: String): String
}
