package fr.geoking.julius.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.ui.components.JsonDebugColors
import fr.geoking.julius.ui.components.JsonTreeNode
import fr.geoking.julius.ui.components.parseJsonForDebug

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

    val jsonElement = remember(json) { parseJsonForDebug(json) }
    val colors = remember {
        JsonDebugColors(
            keyColor = V3.Muted,
            stringColor = V3.Success.copy(alpha = 0.8f),
            numberColor = V3.Accent.copy(alpha = 0.8f),
            mutedColor = V3.Muted,
            faintColor = V3.Faint,
            highlightColor = V3.Accent,
        )
    }

    Column(Modifier.fillMaxSize().background(V3.Bg)) {
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
                        colors = colors,
                        searchText = searchText,
                        expandAllTrigger = expandAllTrigger,
                        collapseAllTrigger = collapseAllTrigger,
                        initialExpandDepth = 2,
                    )
                }
            }
        }
    }
}
