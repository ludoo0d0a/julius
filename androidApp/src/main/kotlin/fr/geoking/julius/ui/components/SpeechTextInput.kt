package fr.geoking.julius.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import fr.geoking.julius.ui.dictation.rememberDictation

enum class SpeechMicPlacement {
    /** Large mic (64 dp) below the transcript box. */
    External,
    /** Compact mic (40 dp) trailing inside the bordered text row. */
    Inline,
}

/**
 * Text field with microphone dictation (STT-only). Downstream logic belongs in [onDictationEnd].
 */
@Composable
fun SpeechTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onDictationEnd: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    micPlacement: SpeechMicPlacement = SpeechMicPlacement.External,
    showWaveform: Boolean = true,
    placeholder: String? = null,
    title: String? = null,
    subtitle: String? = null,
    onListeningChange: (Boolean) -> Unit = {},
    label: @Composable (() -> Unit)? = null,
    placeholderContent: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    micTint: Color = Color.Unspecified,
    preferOffline: Boolean = true,
) {
    var isListening by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var textAtSessionStart by remember { mutableStateOf("") }
    var lastTranscript by remember { mutableStateOf("") }
    var sessionUtterance by remember { mutableStateOf("") }

    val resolvedMicTint = if (micTint == Color.Unspecified) Color.White else micTint

    fun mergeWithPrefix(utterance: String): String =
        listOf(textAtSessionStart, utterance).filter { it.isNotBlank() }.joinToString(" ")

    val toggleDictation = rememberDictation(
        onText = { utterance ->
            sessionUtterance = utterance
            val merged = mergeWithPrefix(utterance)
            lastTranscript = merged
            onValueChange(merged)
        },
        onLevel = { audioLevel = it },
        onListening = { listening ->
            isListening = listening
            onListeningChange(listening)
            if (!listening && lastTranscript.isNotBlank()) {
                onDictationEnd(lastTranscript)
            }
        },
        preferOffline = preferOffline,
    )

    val displayText = when {
        isListening && sessionUtterance.isNotBlank() -> mergeWithPrefix(sessionUtterance)
        isListening -> textAtSessionStart
        else -> value
    }

    val onMicClick: () -> Unit = {
        if (isListening) {
            toggleDictation()
        } else {
            textAtSessionStart = value
            sessionUtterance = ""
            lastTranscript = value
            toggleDictation()
        }
    }

    val micIcon = @Composable {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Arrêter la dictée" else "Démarrer la dictée",
            tint = if (isListening) Color.Red else resolvedMicTint,
        )
    }

    Column(modifier = modifier) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }

        OutlinedTextField(
            value = displayText,
            onValueChange = { if (!isListening) onValueChange(it) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isListening,
            label = label,
            placeholder = placeholderContent ?: placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            shape = shape,
            colors = colors,
            trailingIcon = if (micPlacement == SpeechMicPlacement.Inline) {
                {
                    FilledIconButton(
                        onClick = onMicClick,
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) Color.Red.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isListening) Color.Red else resolvedMicTint,
                        ),
                    ) { micIcon() }
                }
            } else {
                null
            },
        )

        if (showWaveform && isListening) {
            Spacer(Modifier.height(8.dp))
            DictationWaveform(
                level = audioLevel,
                isActive = isListening,
                tint = if (isListening) Color.Red else resolvedMicTint,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (micPlacement == SpeechMicPlacement.External) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onMicClick,
                    enabled = enabled,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) Color.Red else resolvedMicTint.copy(alpha = 0.2f),
                        contentColor = if (isListening) Color.White else resolvedMicTint,
                    ),
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Arrêter la dictée" else "Démarrer la dictée",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DictationWaveform(
    level: Float,
    isActive: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) level.coerceIn(0.05f, 1f) else 0.08f,
        label = "dictation_wave_level",
    )
    val barCount = 12
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val barWidth = size.width / (barCount * 2f)
        val gap = barWidth
        for (i in 0 until barCount) {
            val centerWeight = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f) * 0.35f
            val barHeight = size.height * animatedLevel * centerWeight
            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = tint.copy(alpha = if (isActive) 0.85f else 0.25f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
