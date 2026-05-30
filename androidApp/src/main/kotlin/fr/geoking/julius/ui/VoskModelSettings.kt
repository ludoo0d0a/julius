package fr.geoking.julius.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.feature.voice.VoskModelHelper
import fr.geoking.julius.feature.voice.VoskModelVariant
import kotlinx.coroutines.launch

@Composable
fun VoskModelSettings(
    context: android.content.Context,
    onModelReadyChanged: (Boolean) -> Unit
) {
    val helper = remember { VoskModelHelper(context) }
    val scope = rememberCoroutineScope()
    var isModelDownloaded by remember { mutableStateOf(helper.isModelDownloaded()) }
    var downloadVariant by remember { mutableStateOf<VoskModelVariant?>(null) }
    var downloadBytes by remember { mutableLongStateOf(0L) }
    var downloadTotal by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Vosk Model", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)

        if (downloadVariant != null) {
            val progress = downloadTotal?.let { if (it > 0) downloadBytes.toFloat() / it else 0f } ?: 0f
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Downloading ${downloadVariant?.displayName}...", fontSize = 12.sp, color = Color.White)
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Text("${downloadBytes / 1024} KB / ${downloadTotal?.let { it / 1024 } ?: "?"} KB", fontSize = 10.sp, color = Color.Gray)
            }
        } else {
            VoskModelVariant.entries.forEach { variant ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(variant.displayName, fontSize = 13.sp, color = Color.White)
                        Text(variant.sizeDescription, fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            downloadVariant = variant
                            downloadError = null
                            scope.launch {
                                helper.downloadAndExtract(variant) { bytes, total ->
                                    downloadBytes = bytes
                                    downloadTotal = total
                                }
                                .onSuccess {
                                    isModelDownloaded = true
                                    onModelReadyChanged(true)
                                    downloadVariant = null
                                }
                                .onFailure {
                                    downloadError = it.message
                                    downloadVariant = null
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }
        }

        downloadError?.let {
            Text(it, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
