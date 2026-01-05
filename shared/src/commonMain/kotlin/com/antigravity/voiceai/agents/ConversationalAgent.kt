package com.antigravity.voiceai.agents

interface ConversationalAgent {
    suspend fun process(input: String): AgentResponse
}

data class AgentResponse(
    val text: String,
    val audio: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AgentResponse

        if (text != other.text) return false
        if (audio != null) {
            if (other.audio == null) return false
            if (!audio.contentEquals(other.audio)) return false
        } else if (other.audio != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (audio?.contentHashCode() ?: 0)
        return result
    }
}
