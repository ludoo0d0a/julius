package fr.geoking.julius.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import fr.geoking.julius.ui.ColorHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** Theme for [JsonDebugPanel] / [JsonTreeNode] — reusable across V3, harness, and overlay debug bars. */
data class JsonDebugColors(
    val keyColor: Color,
    val stringColor: Color,
    val numberColor: Color,
    val mutedColor: Color,
    val faintColor: Color,
    val highlightColor: Color,
) {
    companion object {
        val DarkOverlay = JsonDebugColors(
            keyColor = ColorHelper.JulesAccent,
            stringColor = Color(0xFF81C784),
            numberColor = Color.Cyan,
            mutedColor = Color.White.copy(alpha = 0.55f),
            faintColor = Color.White.copy(alpha = 0.35f),
            highlightColor = Color.Yellow,
        )
    }
}

/**
 * Reusable collapsible JSON tree viewer (request/response payloads, API debug, etc.).
 */
@Composable
fun JsonDebugPanel(
    jsonString: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 220.dp,
    colors: JsonDebugColors = JsonDebugColors.DarkOverlay,
    showToolbar: Boolean = true,
    initialExpandDepth: Int = 1,
    label: String? = null,
) {
    var expandAllTrigger by remember { mutableStateOf(0) }
    var collapseAllTrigger by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val jsonElement = remember(jsonString) { parseJsonForDebug(jsonString) }

    Column(modifier = modifier) {
        if (showToolbar) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label?.let {
                    Text(it, color = colors.mutedColor, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                } ?: Spacer(Modifier.weight(1f))
                Row {
                    IconButton(onClick = { expandAllTrigger++ }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.UnfoldMore, "Expand all", tint = colors.mutedColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = { collapseAllTrigger++ }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.UnfoldLess, "Collapse all", tint = colors.mutedColor, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("json_debug", jsonString)))
                                Toast.makeText(context, "JSON copied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = colors.mutedColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
        ) {
            item {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    JsonTreeNode(
                        name = "root",
                        element = jsonElement,
                        depth = 0,
                        colors = colors,
                        searchText = "",
                        expandAllTrigger = expandAllTrigger,
                        collapseAllTrigger = collapseAllTrigger,
                        initialExpandDepth = initialExpandDepth,
                    )
                }
            }
        }
    }
}

fun parseJsonForDebug(jsonString: String): JsonElement =
    try {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonString.trim().ifBlank { "{}" })
    } catch (e: Exception) {
        JsonPrimitive("Invalid JSON: ${e.message}\n\n${jsonString.take(400)}")
    }

@Composable
fun JsonTreeNode(
    name: String,
    element: JsonElement,
    depth: Int,
    colors: JsonDebugColors,
    searchText: String = "",
    expandAllTrigger: Int = 0,
    collapseAllTrigger: Int = 0,
    initialExpandDepth: Int = 2,
) {
    var expanded by remember(name, depth) { mutableStateOf(depth < initialExpandDepth) }

    val matches = remember(element, name, searchText) {
        if (searchText.isEmpty()) true
        else {
            name.contains(searchText, ignoreCase = true) ||
                (element is JsonPrimitive && element.toString().contains(searchText, ignoreCase = true)) ||
                hasMatchInJsonChildren(element, searchText)
        }
    }
    if (!matches && searchText.isNotEmpty()) return

    LaunchedEffect(expandAllTrigger) { if (expandAllTrigger > 0) expanded = true }
    LaunchedEffect(collapseAllTrigger) { if (collapseAllTrigger > 0) expanded = false }
    LaunchedEffect(searchText) {
        if (searchText.isNotEmpty() && hasMatchInJsonChildren(element, searchText)) expanded = true
    }

    Column(modifier = Modifier.padding(start = if (depth > 0) 12.dp else 0.dp)) {
        when (element) {
            is JsonObject -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 1.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = colors.mutedColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (name == "root") "{" else "$name: {",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) colors.highlightColor else colors.keyColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                    if (!expanded) {
                        Text(" … }", color = colors.faintColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, softWrap = false)
                    }
                }
                if (expanded) {
                    element.forEach { (key, value) ->
                        JsonTreeNode(key, value, depth + 1, colors, searchText, expandAllTrigger, collapseAllTrigger, initialExpandDepth)
                    }
                    Text("}", color = colors.keyColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 12.dp), softWrap = false)
                }
            }
            is JsonArray -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 1.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = colors.mutedColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "$name: [",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) colors.highlightColor else colors.keyColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                    if (!expanded) {
                        Text(" … ]", color = colors.faintColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, softWrap = false)
                    }
                }
                if (expanded) {
                    element.forEachIndexed { index, value ->
                        JsonTreeNode("[$index]", value, depth + 1, colors, searchText, expandAllTrigger, collapseAllTrigger, initialExpandDepth)
                    }
                    Text("]", color = colors.keyColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 12.dp), softWrap = false)
                }
            }
            is JsonPrimitive -> {
                Row(modifier = Modifier.padding(vertical = 1.dp).padding(start = 12.dp)) {
                    Text(
                        text = "$name: ",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) colors.highlightColor else colors.keyColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                    val valueStr = element.toString()
                    Text(
                        text = valueStr,
                        color = when {
                            valueStr.contains(searchText, ignoreCase = true) && searchText.isNotEmpty() -> colors.highlightColor
                            element.isString -> colors.stringColor
                            else -> colors.numberColor
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

fun hasMatchInJsonChildren(element: JsonElement, searchText: String): Boolean {
    if (searchText.isEmpty()) return true
    return when (element) {
        is JsonObject -> element.any {
            it.key.contains(searchText, ignoreCase = true) ||
                (it.value is JsonPrimitive && it.value.toString().contains(searchText, ignoreCase = true)) ||
                hasMatchInJsonChildren(it.value, searchText)
        }
        is JsonArray -> element.any {
            (it is JsonPrimitive && it.toString().contains(searchText, ignoreCase = true)) ||
                hasMatchInJsonChildren(it, searchText)
        }
        else -> false
    }
}
