package fr.geoking.julius.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonDebugScreen(
    title: String,
    json: String,
    onBack: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var expandAllTrigger by remember { mutableStateOf(0) }
    var collapseAllTrigger by remember { mutableStateOf(0) }

    val jsonElement = remember(json) {
        try {
            Json { prettyPrint = true }.parseToJsonElement(json)
        } catch (e: Exception) {
            JsonPrimitive("Invalid JSON: ${e.message}")
        }
    }

    Column(Modifier.fillMaxSize().background(V3.Bg)) {
        // Search bar
        TextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = V3.SurfaceHi,
                unfocusedContainerColor = V3.Surface,
                focusedTextColor = V3.Fg,
                unfocusedTextColor = V3.Fg,
                cursorColor = V3.Accent
            ),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { expandAllTrigger++ }) {
                Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tout déplier", fontSize = 12.sp)
            }
            TextButton(onClick = { collapseAllTrigger++ }) {
                Icon(Icons.Default.UnfoldLess, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tout replier", fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            item {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    JsonTreeNode(
                        name = "root",
                        element = jsonElement,
                        depth = 0,
                        searchText = searchText,
                        expandAllTrigger = expandAllTrigger,
                        collapseAllTrigger = collapseAllTrigger
                    )
                }
            }
        }
    }
}

@Composable
fun JsonTreeNode(
    name: String,
    element: JsonElement,
    depth: Int,
    searchText: String,
    expandAllTrigger: Int,
    collapseAllTrigger: Int
) {
    var expanded by remember { mutableStateOf(depth < 2) }

    val matches = remember(element, name, searchText) {
        if (searchText.isEmpty()) true
        else {
            name.contains(searchText, ignoreCase = true) ||
                    (element is JsonPrimitive && element.toString().contains(searchText, ignoreCase = true)) ||
                    hasMatchInChildren(element, searchText)
        }
    }

    if (!matches && searchText.isNotEmpty()) return

    LaunchedEffect(expandAllTrigger) { if (expandAllTrigger > 0) expanded = true }
    LaunchedEffect(collapseAllTrigger) { if (collapseAllTrigger > 0) expanded = false }

    // Auto-expand if search matches children
    LaunchedEffect(searchText) {
        if (searchText.isNotEmpty() && hasMatchInChildren(element, searchText)) {
            expanded = true
        }
    }

    Column(modifier = Modifier.padding(start = if (depth > 0) 12.dp else 0.dp)) {
        when (element) {
            is JsonObject -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = V3.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (name == "root") "{" else "$name: {",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) V3.Accent else V3.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!expanded) {
                        Text(" … }", color = V3.Faint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (expanded) {
                    element.forEach { (key, value) ->
                        JsonTreeNode(key, value, depth + 1, searchText, expandAllTrigger, collapseAllTrigger)
                    }
                    Text(
                        text = "}",
                        color = V3.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            is JsonArray -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = V3.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$name: [",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) V3.Accent else V3.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!expanded) {
                        Text(" … ]", color = V3.Faint, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (expanded) {
                    element.forEachIndexed { index, value ->
                        JsonTreeNode("[$index]", value, depth + 1, searchText, expandAllTrigger, collapseAllTrigger)
                    }
                    Text(
                        text = "]",
                        color = V3.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            is JsonPrimitive -> {
                Row(modifier = Modifier.padding(vertical = 2.dp).padding(start = 16.dp)) {
                    Text(
                        text = "$name: ",
                        color = if (name.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) V3.Accent else V3.Muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val valueStr = element.toString()
                    Text(
                        text = valueStr,
                        color = if (valueStr.contains(searchText, ignoreCase = true) && searchText.isNotEmpty()) V3.Success else if (element.isString) V3.Success.copy(alpha = 0.8f) else V3.Accent.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun hasMatchInChildren(element: JsonElement, searchText: String): Boolean {
    if (searchText.isEmpty()) return true
    return when (element) {
        is JsonObject -> element.any { it.key.contains(searchText, ignoreCase = true) || (it.value is JsonPrimitive && it.value.toString().contains(searchText, ignoreCase = true)) || hasMatchInChildren(it.value, searchText) }
        is JsonArray -> element.any { (it is JsonPrimitive && it.toString().contains(searchText, ignoreCase = true)) || hasMatchInChildren(it, searchText) }
        else -> false
    }
}
