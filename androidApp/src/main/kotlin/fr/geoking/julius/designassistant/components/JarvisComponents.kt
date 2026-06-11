package fr.geoking.julius.designassistant.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.designassistant.DesignAssistantJarvisColors
import fr.geoking.julius.designassistant.FeatureStatus

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DesignAssistantJarvisColors.MaterialTheme,
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.Monospace),
            titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        ),
        content = content
    )
}

@Composable
fun JarvisHUDHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = DesignAssistantJarvisColors.CyanNeon,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
                drawCircle(
                    color = DesignAssistantJarvisColors.CyanNeon,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width - 20.dp.toPx(), size.height)
                )
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DesignAssistantJarvisColors.CyanNeon
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    color = DesignAssistantJarvisColors.CyanNeon,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = DesignAssistantJarvisColors.TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun JarvisCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowColor: Color = DesignAssistantJarvisColors.CyanNeon,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Box(
        modifier = cardModifier
            .padding(4.dp)
            .drawBehind {
                val path = Path().apply {
                    moveTo(0f, 10.dp.toPx())
                    lineTo(10.dp.toPx(), 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height - 10.dp.toPx())
                    lineTo(size.width - 10.dp.toPx(), size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    color = glowColor.copy(alpha = 0.1f)
                )
                drawPath(
                    path = path,
                    color = glowColor,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .padding(12.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun JarvisStatusIndicator(status: FeatureStatus) {
    val color = when (status) {
        FeatureStatus.DONE -> DesignAssistantJarvisColors.StatusSuccess
        FeatureStatus.IN_PROGRESS -> DesignAssistantJarvisColors.StatusActive
        FeatureStatus.TODO -> DesignAssistantJarvisColors.StatusWarning
        FeatureStatus.READY -> DesignAssistantJarvisColors.CyanNeon
        FeatureStatus.IDEA -> DesignAssistantJarvisColors.TextSecondary
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .drawBehind {
                    drawCircle(color = color.copy(alpha = 0.4f), radius = 6.dp.toPx())
                    drawCircle(color = color, radius = 3.dp.toPx())
                }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status.labelFr.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun JarvisButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DesignAssistantJarvisColors.CyanNeon
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        color = Color.Transparent,
        shape = GenericShape { size, _ ->
            moveTo(0f, size.height * 0.3f)
            lineTo(size.width * 0.1f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.7f)
            lineTo(size.width * 0.9f, size.height)
            lineTo(0f, size.height)
            close()
        },
        border = BorderStroke(1.dp, color)
    ) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun JarvisChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String = "COMMAND..."
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .drawBehind {
                drawLine(
                    color = DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.5f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    placeholder,
                    color = DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = DesignAssistantJarvisColors.CyanNeon),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DesignAssistantJarvisColors.CyanNeon,
                unfocusedBorderColor = DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.3f),
                cursorColor = DesignAssistantJarvisColors.CyanNeon
            ),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {}, // Mic placeholder
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, DesignAssistantJarvisColors.OrangeNeon.copy(alpha = 0.5f), CircleShape)
                .background(DesignAssistantJarvisColors.OrangeNeon.copy(alpha = 0.05f))
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Voice Command",
                tint = DesignAssistantJarvisColors.OrangeNeon
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, DesignAssistantJarvisColors.CyanNeon, CircleShape)
                .background(DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.1f))
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Execute",
                tint = DesignAssistantJarvisColors.CyanNeon
            )
        }
    }
}
