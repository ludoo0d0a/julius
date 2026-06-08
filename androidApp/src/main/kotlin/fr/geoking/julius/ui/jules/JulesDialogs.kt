package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.ColorHelper

@Composable
internal fun JulesLinkToFeatureDialog(
    session: JulesSessionEntity,
    features: List<FeatureEntity>,
    onDismiss: () -> Unit,
    onLink: (String?) -> Unit,
    onCreateAndLink: (String) -> Unit,
) {
    var newFeatureTitle by remember { mutableStateOf("") }
    var showCreateNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to Feature") },
        containerColor = ColorHelper.JulesListBg,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showCreateNew) {
                    OutlinedTextField(
                        value = newFeatureTitle,
                        onValueChange = { newFeatureTitle = it },
                        label = { Text("Feature Title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    if (session.featureId != null) {
                        TextButton(
                            onClick = { onLink(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Unlink from feature", color = Color.Red)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(features) { feature ->
                            val isSelected = feature.id == session.featureId
                            ListItem(
                                headlineContent = { Text(feature.title) },
                                modifier = Modifier.clickable { onLink(feature.id) },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = ColorHelper.JulesAccent)
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = if (isSelected) ColorHelper.JulesAccent else Color.White,
                                ),
                            )
                        }
                    }

                    TextButton(
                        onClick = { showCreateNew = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Create new feature")
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateNew) {
                Button(
                    onClick = { onCreateAndLink(newFeatureTitle) },
                    enabled = newFeatureTitle.isNotBlank(),
                ) {
                    Text("Create & Link")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showCreateNew) showCreateNew = false else onDismiss()
            }) {
                Text("Cancel")
            }
        },
    )
}
