package fr.geoking.julius.ui.jules

import androidx.compose.ui.graphics.Color
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.ColorHelper

data class JulesSessionStatus(
    val text: String,
    val color: Color,
    val prIdSuffix: String = "",
    val mergeabilitySuffix: String = "",
)

fun julesSessionStatus(session: JulesSessionEntity, style: JulesStatusStyle): JulesSessionStatus {
    val hasOutput = !session.prUrl.isNullOrBlank()
    val (text, color) = when {
        session.prState == "merged" -> "Merged" to Color.Green
        session.prState == "closed" -> "Closed" to Color.Red
        session.sessionState == "COMPLETED" -> "Completed" to Color.Green
        session.sessionState == "FAILED" -> "Failed" to Color.Red
        session.sessionState == "AWAITING_PLAN_APPROVAL" -> when (style) {
            JulesStatusStyle.Header -> "Waiting for approval" to ColorHelper.JulesAccent
            JulesStatusStyle.Badge -> "Wait Approval" to ColorHelper.JulesAccent
        }
        session.sessionState == "PLANNING" -> when (style) {
            JulesStatusStyle.Header -> "Planning…" to ColorHelper.JulesAccent
            JulesStatusStyle.Badge -> "Planning" to ColorHelper.JulesAccent
        }
        session.sessionState == "PAUSED" -> "Paused" to Color.Yellow
        else -> when (style) {
            JulesStatusStyle.Header -> {
                val label = if (hasOutput) "Output available" else "In progress"
                label to if (hasOutput) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.6f)
            }
            JulesStatusStyle.Badge -> {
                val label = if (hasOutput) "Output" else "Active"
                label to if (hasOutput) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.6f)
            }
        }
    }
    val prIdSuffix = if (style == JulesStatusStyle.Header && !session.prId.isNullOrBlank()) {
        " #${session.prId}"
    } else {
        ""
    }
    val mergeabilitySuffix = if (
        style == JulesStatusStyle.Header &&
        session.prState == "open" &&
        session.prMergeable == false
    ) {
        " (Conflicts)"
    } else {
        ""
    }
    return JulesSessionStatus(text, color, prIdSuffix, mergeabilitySuffix)
}

enum class JulesStatusStyle {
    Header,
    Badge,
}
