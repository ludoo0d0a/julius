package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.DeviceAction

interface ConversationalAgent {
    suspend fun process(input: String): AgentResponse
}

data class AgentResponse(
    val text: String,
    val audio: ByteArray? = null,
    val action: DeviceAction? = null
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
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (audio?.contentHashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        return result
    }
}
