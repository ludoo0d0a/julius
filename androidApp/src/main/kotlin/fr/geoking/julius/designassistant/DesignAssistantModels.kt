package fr.geoking.julius.designassistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector

enum class FeatureStatus(val labelFr: String) {
    IN_PROGRESS("En cours"),
    READY("Générée - Prête"),
    IDEA("Idée / En attente"),
    DONE("Terminée"),
    TODO("À faire"),
}

data class DesignProject(
    val id: String,
    val name: String,
    val emoji: String,
    val activeFeaturesCount: Int,
    val promptCount: Int,
    val mainBranch: String,
    val lastModifiedLabel: String,
    val description: String,
    val features: List<DesignFeature>,
)

data class DesignFeature(
    val id: String,
    val name: String,
    val status: FeatureStatus,
    val branch: String? = null,
    val prNumber: Int? = null,
    val prTitle: String? = null,
)

enum class ChatMessageKind {
    USER,
    AGENT,
    CODE,
    CI,
    PR_ACTION,
}

data class DesignChatMessage(
    val id: String,
    val kind: ChatMessageKind,
    val text: String,
    val codeSnippet: String? = null,
    val branch: String? = null,
    val prNumber: Int? = null,
    val prTitle: String? = null,
)

enum class WorkspaceTab(
    val labelFr: String,
    val icon: ImageVector,
) {
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    GENERATED_CODE("Code", Icons.Default.Code),
    MODIFIED_FILES("Fichiers", Icons.Default.Folder),
}
