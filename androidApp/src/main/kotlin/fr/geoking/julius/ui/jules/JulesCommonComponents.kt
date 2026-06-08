package fr.geoking.julius.ui.jules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesErrorBg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                    }
                }
            }
            Text(message, color = Color.White.copy(alpha = 0.8f))
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
