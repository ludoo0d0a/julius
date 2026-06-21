package fr.geoking.julius.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.JulesSessionEntity

/** Visual mapping for a status: human label + the raw enum + color + running pulse. */
data class StatusVisual(
    val label: String,
    val enum: String,
    val color: Color,
    val pulse: Boolean = false,
)

/** Coarse status bucket used by the global Features filter chips. */
enum class FeatureBucket(val label: String) {
    ALL("Toutes"),
    RUNNING("En cours"),
    QUEUED("En file"),
    MERGED("Terminé"),
    FAILED("Échec"),
}

fun featureStatusVisual(status: String): StatusVisual = when (status.uppercase()) {
    "IN_PROGRESS" -> StatusVisual("En cours", "IN_PROGRESS", V3.Accent, pulse = true)
    "PLANNING" -> StatusVisual("En cours", "PLANNING", V3.Accent, pulse = true)
    "QUEUED" -> StatusVisual("En file", "QUEUED", V3.Warn)
    "PENDING" -> StatusVisual("En attente", "PENDING", V3.Muted)
    "COMPLETED" -> StatusVisual("Terminé", "COMPLETED", V3.Success)
    "FAILED" -> StatusVisual("Échec", "FAILED", V3.Danger)
    "CANCELLED" -> StatusVisual("Annulé", "CANCELLED", V3.Muted)
    else -> StatusVisual(status, status, V3.Muted)
}

fun sessionStatusVisual(session: JulesSessionEntity): StatusVisual = when {
    session.prState == "merged" -> StatusVisual("Mergé", "merged", V3.Success)
    session.prState == "closed" -> StatusVisual("Fermée", "closed", V3.Muted)
    session.prMergeable == false -> StatusVisual("Conflit", "mergeable=false", V3.Danger)
    session.prMergeable == true && session.prState == "open" -> StatusVisual("Prêt à merger", "mergeable=true", V3.Success)
    session.prState == "open" -> StatusVisual("PR ouverte", "open", V3.Accent)
    session.sessionState == "FAILED" -> StatusVisual("Échec", "FAILED", V3.Danger)
    session.sessionState == "COMPLETED" -> StatusVisual("Terminé", "COMPLETED", V3.Success)
    else -> StatusVisual("En cours", session.sessionState ?: "ACTIVE", V3.Accent, pulse = true)
}

/** Which filter bucket a feature status falls into (open/CI handled at call site for TOMERGE). */
fun bucketOf(status: String): FeatureBucket = when (status.uppercase()) {
    "IN_PROGRESS", "PLANNING" -> FeatureBucket.RUNNING
    "QUEUED", "PENDING" -> FeatureBucket.QUEUED
    "COMPLETED" -> FeatureBucket.MERGED
    "FAILED" -> FeatureBucket.FAILED
    else -> FeatureBucket.QUEUED
}

/** Status as a leading icon (used in feature rows instead of a trailing chip). */
fun featureStatusIcon(status: String): androidx.compose.ui.graphics.vector.ImageVector = when (status.uppercase()) {
    "IN_PROGRESS", "PLANNING" -> Icons.Filled.Autorenew
    "QUEUED" -> Icons.Filled.Schedule
    "PENDING" -> Icons.Filled.HourglassEmpty
    "COMPLETED" -> Icons.Filled.CheckCircle
    "FAILED" -> Icons.Filled.ErrorOutline
    "CANCELLED" -> Icons.Filled.Block
    else -> Icons.Filled.Schedule
}

@Composable
fun StatusPill(v: StatusVisual, showEnum: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(v.color.copy(alpha = 0.14f))
            .border(1.dp, v.color.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(99.dp)).background(v.color))
        Text(v.label, color = v.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        if (showEnum) {
            Text(
                v.enum,
                color = v.color.copy(alpha = 0.7f),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, meta: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text.uppercase(),
            color = V3.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        if (meta != null) {
            Text(meta, color = V3.Faint, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun V3Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(V3.Surface)
            .border(1.dp, V3.Border, RoundedCornerShape(18.dp)),
        content = content,
    )
}

/** A tappable list row: leading icon dot, title, subtitle, trailing slot. */
@Composable
fun V3Row(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = V3.Muted,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(V3.SurfaceHi),
                contentAlignment = Alignment.Center,
            ) { Icon(leadingIcon, null, tint = leadingTint, modifier = Modifier.size(18.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = V3.Fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = V3.Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun KpiCell(value: String, label: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(V3.Surface)
            .border(1.dp, V3.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Text(
            value,
            color = if (accent) V3.Accent else V3.Fg,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(label, color = V3.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * Compact Material top-app-bar title: a single title + optional supporting line
 * (aligned with the prototype — no oversized hero title, no uppercase eyebrow).
 * The `eyebrow` param is kept for call-site compatibility but intentionally unused.
 */
@Composable
fun V3LargeTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 6.dp)) {
        Text(title, color = V3.Fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
        if (subtitle != null) {
            Text(subtitle, color = V3.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
