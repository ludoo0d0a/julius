package fr.geoking.julius.ui.harness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.ui.ColorHelper

@Composable
fun AddFeatureScreen(
    defaultSourceName: String,
    onBack: () -> Unit,
    onSave: (title: String, description: String, sourceName: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf(defaultSourceName) }

    BackHandler(onBack = onBack)

    Surface(modifier = Modifier.fillMaxSize(), color = ColorHelper.JulesBg) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("New feature", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / prompt") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 4,
            )
            OutlinedTextField(
                value = sourceName,
                onValueChange = { sourceName = it },
                label = { Text("Repository source id") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            FilledTonalButton(
                onClick = {
                    if (title.isNotBlank()) onSave(title.trim(), description.trim(), sourceName.trim())
                },
                modifier = Modifier.padding(top = 16.dp),
                enabled = title.isNotBlank(),
            ) {
                Text("Add to queue")
            }
        }
    }
}
