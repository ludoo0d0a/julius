package fr.geoking.julius.designassistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.designassistant.v1.DesignAssistantV1Host
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository

/**
 * Point d'entrée « Jules Design Assistant ».
 * Les écrans production [fr.geoking.julius.ui.FeaturesScreen] et [JulesScreen] restent inchangés.
 */
@Composable
fun DesignAssistantHost(
    onBack: () -> Unit,
    julesRepository: JulesRepository? = null,
    featureRepository: FeatureRepository? = null,
    settingsManager: SettingsManager? = null,
    buildRepository: GitHubBuildRepository? = null,
) {
    DesignAssistantTheme {
        DesignAssistantV1Host(
            onBack = onBack,
            julesRepository = julesRepository,
            featureRepository = featureRepository,
            settingsManager = settingsManager,
            buildRepository = buildRepository,
        )
    }
}

@Preview(showBackground = true, heightDp = 840, name = "Design Assistant Host")
@Composable
private fun DesignAssistantHostPreview() {
    DesignAssistantHost(onBack = {})
}
