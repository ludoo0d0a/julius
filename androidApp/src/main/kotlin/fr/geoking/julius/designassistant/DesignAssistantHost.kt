package fr.geoking.julius.designassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.designassistant.v1.DesignAssistantV1Host
import fr.geoking.julius.designassistant.v2.DesignAssistantV2Host

enum class DesignAssistantUiVersion { V1_MOCKUP, V2_PREVIEW }

/**
 * Point d'entrée « Jules Design Assistant » — bascule entre UI mockup (v1) et aperçu v2.
 * Les écrans production [fr.geoking.julius.ui.FeaturesScreen] et [JulesScreen] restent inchangés.
 */
@Composable
fun DesignAssistantHost(
    onBack: () -> Unit,
    initialVersion: DesignAssistantUiVersion = DesignAssistantUiVersion.V2_PREVIEW,
) {
    var version by remember { mutableStateOf(initialVersion) }

    DesignAssistantTheme {
        Column(Modifier.fillMaxSize().background(DesignAssistantColors.NavyDark)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DesignAssistantColors.Navy)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer", tint = Color.White)
                }
                Icon(Icons.Default.Preview, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                Text(
                    "Jules Design Assistant",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DesignAssistantColors.NavyLight)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = version == DesignAssistantUiVersion.V1_MOCKUP,
                    onClick = { version = DesignAssistantUiVersion.V1_MOCKUP },
                    label = { Text("UI actuelle", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = DesignAssistantColors.Navy,
                    ),
                )
                FilterChip(
                    selected = version == DesignAssistantUiVersion.V2_PREVIEW,
                    onClick = { version = DesignAssistantUiVersion.V2_PREVIEW },
                    label = { Text("Aperçu V2", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = DesignAssistantColors.Navy,
                    ),
                )
            }
            when (version) {
                DesignAssistantUiVersion.V1_MOCKUP -> DesignAssistantV1Host(onBack = onBack)
                DesignAssistantUiVersion.V2_PREVIEW -> DesignAssistantV2Host(onBack = {})
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 840, name = "Design Assistant Host")
@Composable
private fun DesignAssistantHostPreview() {
    DesignAssistantHost(onBack = {})
}
