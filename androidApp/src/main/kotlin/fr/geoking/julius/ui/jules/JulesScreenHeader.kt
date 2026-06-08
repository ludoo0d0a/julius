package fr.geoking.julius.ui.jules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.JulesSessionEntity

@Composable
internal fun JulesScreenHeader(
    title: String,
    subtitle: String? = null,
    currentSession: JulesSessionEntity?,
    showSwitchProject: Boolean,
    onBack: () -> Unit,
    onSwitchProject: () -> Unit,
    onTogglePause: (JulesSessionEntity) -> Unit,
    onOpenInWeb: (String) -> Unit,
    onShowActivities: (JulesSessionEntity) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            currentSession?.let { session ->
                val status = julesSessionStatus(session, JulesStatusStyle.Header)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(status.color, RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "${status.text}${status.prIdSuffix}${status.mergeabilitySuffix}",
                        color = status.color,
                        fontSize = 12.sp,
                    )
                }
            } ?: subtitle?.let { sub ->
                Text(
                    text = sub,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showSwitchProject) {
            IconButton(onClick = onSwitchProject) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Switch project", tint = Color.White)
            }
        }

        currentSession?.let { session ->
            if (!session.isFinished) {
                IconButton(onClick = { onTogglePause(session) }) {
                    Icon(
                        if (session.sessionState == "PAUSED") Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (session.sessionState == "PAUSED") "Resume" else "Pause",
                        tint = Color.White,
                    )
                }
            }
            session.url?.takeIf { it.isNotBlank() }?.let { url ->
                IconButton(onClick = { onOpenInWeb(url) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in web", tint = Color.White)
                }
            }
            IconButton(onClick = { onShowActivities(session) }) {
                Icon(Icons.Default.History, contentDescription = "Activities", tint = Color.White)
            }
        }
    }
}
