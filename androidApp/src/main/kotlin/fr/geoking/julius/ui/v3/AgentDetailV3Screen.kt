package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.ui.AgentApiUsageTarget
import fr.geoking.julius.queue.AgentAccount
import java.util.UUID

/** Per-agent add/update screen, reached from the Settings agent list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailV3Screen(
    deps: V3Deps,
    accountId: String?,
    onBack: () -> Unit,
    onOpenBilling: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val existing = remember(accountId, settings.agentAccounts) { settings.agentAccounts.firstOrNull { it.id == accountId } }

    var label by remember(accountId) { mutableStateOf(existing?.label ?: "") }
    var key by remember(accountId) { mutableStateOf(existing?.apiKey ?: "") }
    var backend by remember(accountId) { mutableStateOf(existing?.backend ?: settings.codingAgentBackend) }
    var enabled by remember(accountId) { mutableStateOf(existing?.enabled ?: true) }

    fun persist(list: List<AgentAccount>) {
        val julesKeys = list.filter { it.backend == CodingAgentBackend.JULES && it.enabled }.map { it.apiKey }
        val anthropic = list.firstOrNull { it.backend == CodingAgentBackend.CLAUDE_CODE && it.enabled }?.apiKey
            ?: settings.anthropicApiKey
        deps.settingsManager.saveSettings(
            settings.copy(agentAccounts = list, julesKeys = julesKeys, anthropicApiKey = anthropic),
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = V3.Fg, unfocusedTextColor = V3.Fg,
        focusedBorderColor = V3.Accent, unfocusedBorderColor = V3.Border,
        focusedLabelColor = V3.Accent, unfocusedLabelColor = V3.Muted,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 16.dp)) {
        AgentBillingNavV3Row(
            title = "Usage, tokens & billing",
            subtitle = "Rate limits and cost model for $backend",
            onClick = { onOpenBilling(AgentApiUsageTarget.Coding(backend).encode()) },
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel("Backend")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(CodingAgentBackend.JULES, CodingAgentBackend.CLAUDE_CODE).forEach { b ->
                FilterChip(
                    selected = backend == b,
                    onClick = { backend = b },
                    label = { Text(b.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = V3.Accent, selectedLabelColor = V3.AccentInk,
                        containerColor = V3.Surface, labelColor = V3.Muted,
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Libellé") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Clé API") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )

        Spacer(Modifier.height(8.dp))
        V3Card {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activé", color = V3.Fg, fontSize = 14.5.sp)
                    Text("Utilisé par le scheduler", color = V3.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = enabled, onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = V3.AccentInk, checkedTrackColor = V3.Accent,
                        uncheckedThumbColor = V3.Muted, uncheckedTrackColor = V3.SurfaceHi,
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val id = accountId ?: UUID.randomUUID().toString()
                val updated = AgentAccount(
                    id = id,
                    label = label.ifBlank { backend.name },
                    backend = backend,
                    apiKey = key.trim(),
                    enabled = enabled,
                )
                val list = if (existing != null) {
                    settings.agentAccounts.map { if (it.id == id) updated else it }
                } else {
                    settings.agentAccounts + updated
                }
                persist(list)
                onBack()
            },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
        ) { Text(if (existing != null) "Enregistrer" else "Ajouter l'agent", fontSize = 15.sp) }

        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { persist(settings.agentAccounts.filter { it.id != accountId }); onBack() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Supprimer l'agent", color = V3.Danger) }
        }
        Spacer(Modifier.height(96.dp))
    }
}
