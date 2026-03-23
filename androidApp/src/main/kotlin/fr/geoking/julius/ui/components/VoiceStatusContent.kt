package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.AgentType
import fr.geoking.julius.TextAnimation
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.VoiceEvent

private val AgentLabelFontSize = 11.sp
private val StatusBadgeFontSize = 12.sp

/** Reusable status pill (Silence, Listening, PassiveListening, Processing, Speaking). */
@Composable
fun StatusChip(
    status: VoiceEvent,
    modifier: Modifier = Modifier
) {
    val text = if (status == VoiceEvent.PassiveListening) "Wake Word" else status.name
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = StatusBadgeFontSize,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

private val DisplayTextFontSize = 26.sp
private val DisplayTextLineHeight = 36.sp
private val ErrorTitleFontSize = 15.sp
private val ErrorDetailFontSize = 13.sp

/**
 * Center content: agent name, voice status, main text (transcript or last message), and optional error.
 * @param onAgentClick Optional callback when the agent name is clicked (cycles to next agent on phone).
 */
@Composable
fun VoiceStatusContent(
    agentName: String,
    status: VoiceEvent,
    displayText: String,
    lastError: DetailedError?,
    textAnimation: TextAnimation = TextAnimation.Fade,
    onAgentClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Agent label — compact, uppercase, letter-spaced; clickable to cycle to next agent
        Text(
            text = agentName.uppercase(),
            color = Color(0xFF6366F1).copy(alpha = 0.85f),
            fontSize = AgentLabelFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.then(
                if (onAgentClick != null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onAgentClick() }
                else Modifier
            )
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (status == VoiceEvent.Processing) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = displayText.ifBlank { " " },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Main display text — larger, better line height, centered
            val text = when {
                status == VoiceEvent.PassiveListening -> "Dis 'Hey Julius'"
                status == VoiceEvent.Silence && displayText.isBlank() -> "Dis 'Hey Julius'"
                else -> displayText.ifEmpty { " " }
            }

            AnimatedLetterText(
                text = text,
                animation = textAnimation,
                color = if (status == VoiceEvent.PassiveListening || (status == VoiceEvent.Silence && displayText.isBlank())) Color.White.copy(alpha = 0.5f) else Color.White,
                style = TextStyle(
                    fontSize = DisplayTextFontSize,
                    lineHeight = DisplayTextLineHeight,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.15.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        lastError?.let { error ->
            Spacer(modifier = Modifier.height(20.dp))
            val errorTitle = when (error.httpCode) {
                401 -> "Authentication Error"
                403 -> "Permission Denied"
                429 -> "Rate Limit Exceeded"
                in 500..599 -> "Server Error"
                else -> "Connection Error"
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF87171).copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = errorTitle,
                    color = Color(0xFFFCA5A5),
                    fontSize = ErrorTitleFontSize,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                val httpStatus = error.httpCode?.let { "HTTP $it" } ?: ""
                Text(
                    text = "$httpStatus ${error.message}".trim(),
                    color = Color(0xFFF87171).copy(alpha = 0.85f),
                    fontSize = ErrorDetailFontSize,
                    fontWeight = FontWeight.Normal
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
        displayText = "Hi, how can I help you",
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
        displayText = "Hi, how can I help you",
        lastError = DetailedError(httpCode = 401, message = "Invalid API key", timestamp = 0L)
    )
}
