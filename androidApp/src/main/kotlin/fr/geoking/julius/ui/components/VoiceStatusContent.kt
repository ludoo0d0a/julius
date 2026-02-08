package fr.geoking.julius.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.AgentType
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.VoiceEvent

/**
 * Center content: agent name, voice status, main text (transcript or last message), and optional error.
 */
@Composable
fun VoiceStatusContent(
    agentName: String,
    status: VoiceEvent,
    displayText: String,
    lastError: DetailedError?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = agentName,
            color = Color(0xFF6366F1).copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = status.name,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = displayText,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        lastError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            val errorTitle = when (error.httpCode) {
                401 -> "Authentication Error"
                403 -> "Permission Denied"
                429 -> "Rate Limit Exceeded"
                in 500..599 -> "Server Error"
                else -> "Connection Error"
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorTitle,
                    color = Color(0xFFF87171),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val httpStatus = error.httpCode?.let { "HTTP $it" } ?: ""
                Text(
                    text = "$httpStatus ${error.message}".trim(),
                    color = Color(0xFFF87171).copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceStatusContentSilencePreview() {
    VoiceStatusContent(
        agentName = AgentType.Gemini.name,
        status = VoiceEvent.Silence,
        displayText = "Hi, how can I help?",
        lastError = null
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceStatusContentListeningPreview() {
    VoiceStatusContent(
        agentName = AgentType.Gemini.name,
        status = VoiceEvent.Listening,
        displayText = "What's the weather in Paris?",
        lastError = null
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceStatusContentWithErrorPreview() {
    VoiceStatusContent(
        agentName = AgentType.OpenAI.name,
        status = VoiceEvent.Silence,
        displayText = "Hi, how can I help?",
        lastError = DetailedError(httpCode = 401, message = "Invalid API key", timestamp = 0L)
    )
}
