package fr.geoking.julius.designassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.designassistant.v1.DesignAssistantV1Host
import fr.geoking.julius.designassistant.v2.DesignAssistantV2Host
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository

enum class DesignAssistantUiVersion { V1_MOCKUP, V2_PREVIEW }

/**
 * Point d'entrée « Jules Design Assistant » — bascule entre UI mockup (v1) et aperçu v2.
 * Les écrans production [fr.geoking.julius.ui.FeaturesScreen] et [JulesScreen] restent inchangés.
 */
@Composable
fun DesignAssistantHost(
    onBack: () -> Unit,
    julesRepository: JulesRepository? = null,
    featureRepository: FeatureRepository? = null,
    settingsManager: SettingsManager? = null,
    buildRepository: GitHubBuildRepository? = null,
    initialVersion: DesignAssistantUiVersion = DesignAssistantUiVersion.V2_PREVIEW,
) {
    var version by rememberSaveable { mutableStateOf(initialVersion) }

    DesignAssistantTheme {
        when (version) {
            DesignAssistantUiVersion.V2_PREVIEW -> DesignAssistantV2Host(
                onBack = onBack,
                onSwitchToV1 = { version = DesignAssistantUiVersion.V1_MOCKUP },
                julesRepository = julesRepository,
                featureRepository = featureRepository,
                settingsManager = settingsManager,
                buildRepository = buildRepository,
            )
            DesignAssistantUiVersion.V1_MOCKUP -> DesignAssistantV1Host(
                onBack = { version = DesignAssistantUiVersion.V2_PREVIEW },
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 840, name = "Design Assistant Host")
@Composable
private fun DesignAssistantHostPreview() {
    DesignAssistantHost(onBack = {})
}
