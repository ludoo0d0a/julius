package fr.geoking.julius.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.ui.ColorHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData

@Composable
fun DebugBar(
    jsonString: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var expandAllTrigger by remember { mutableStateOf(0) }
    var collapseAllTrigger by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Debug: Raw JSON Response",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (expanded) {
                IconButton(
                    onClick = { expandAllTrigger++ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "Expand All",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { collapseAllTrigger++ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.UnfoldLess,
                        contentDescription = "Collapse All",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("jules_debug_json", jsonString)))
                        Toast.makeText(context, "JSON copied", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy JSON",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                val jsonElement = remember(jsonString) {
                    try {
                        Json { prettyPrint = true }.parseToJsonElement(jsonString)
                    } catch (e: Exception) {
                        JsonPrimitive("Invalid JSON: ${e.message}")
                    }
                }

                LazyColumn {
                    item {
                        JsonNode(
                            name = "root",
                            element = jsonElement,
                            depth = 0,
                            expandAllTrigger = expandAllTrigger,
                            collapseAllTrigger = collapseAllTrigger
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JsonNode(
    name: String,
    element: JsonElement,
    depth: Int,
    expandAllTrigger: Int = 0,
    collapseAllTrigger: Int = 0
) {
    var expanded by remember { mutableStateOf(depth < 1) }

    LaunchedEffect(expandAllTrigger) {
        if (expandAllTrigger > 0) expanded = true
    }
    LaunchedEffect(collapseAllTrigger) {
        if (collapseAllTrigger > 0) expanded = false
    }

    Column(modifier = Modifier.padding(start = (depth * 12).dp)) {
        when (element) {
            is JsonObject -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$name: {",
                        color = ColorHelper.JulesAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!expanded) {
                        Text(
                            text = " ... }",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (expanded) {
                    element.forEach { (key, value) ->
                        JsonNode(
                            name = key,
                            element = value,
                            depth = depth + 1,
                            expandAllTrigger = expandAllTrigger,
                            collapseAllTrigger = collapseAllTrigger
                        )
                    }
                    Text(
                        text = "}",
                        color = ColorHelper.JulesAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            is JsonArray -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$name: [",
                        color = ColorHelper.JulesAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!expanded) {
                        Text(
                            text = " ... ]",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (expanded) {
                    element.forEachIndexed { index, value ->
                        JsonNode(
                            name = "[$index]",
                            element = value,
                            depth = depth + 1,
                            expandAllTrigger = expandAllTrigger,
                            collapseAllTrigger = collapseAllTrigger
                        )
                    }
                    Text(
                        text = "]",
                        color = ColorHelper.JulesAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            is JsonPrimitive -> {
                Row {
                    Text(
                        text = "$name: ",
                        color = ColorHelper.JulesAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = element.toString(),
                        color = if (element.isString) Color.Green else Color.Cyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
