package fr.geoking.julius.ui.jules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.ColorHelper

@Composable
internal fun JulesErrorCard(
    title: String,
    message: String,
    onDismiss: (() -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesErrorBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(message)) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.height(20.dp)
                    )
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontFamily = if (message.contains("URL:") || message.contains("Request Body:")) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
internal fun JulesSessionStatusBadge(session: JulesSessionEntity) {
    val status = julesSessionStatus(session, JulesStatusStyle.Badge)
    Surface(
        color = status.color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, status.color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = status.text.uppercase(),
            color = status.color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun JulesMiniProgressBar(currentStep: Int?) {
    if (currentStep == null) return
    LinearProgressIndicator(
        progress = { currentStep.toFloat() / 5f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = ColorHelper.JulesAccent,
        trackColor = Color.Transparent,
    )
}

internal fun calculateJulesProgressStep(session: JulesSessionEntity, items: List<JulesChatItem>): Int? {
    return when {
        session.prState == "merged" -> 5
        session.prState == "open" -> 4
        session.sessionState == "COMPLETED" -> 4
        session.sessionState == "PLANNING" -> 1
        else -> null
    }
}
