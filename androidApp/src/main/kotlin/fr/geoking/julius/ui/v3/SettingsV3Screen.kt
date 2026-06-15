package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.queue.QueuePolicy
import fr.geoking.julius.queue.queuePolicyFor
import java.util.UUID

@Composable
fun SettingsV3Screen(deps: V3Deps) {
    val settings by deps.settingsManager.settings.collectAsState()
    val backend = settings.codingAgentBackend
    val policy = settings.queuePolicyFor(backend)

    fun savePolicy(transform: (QueuePolicy) -> QueuePolicy) {
        deps.settingsManager.saveSettings(
            settings.copy(queuePolicies = settings.queuePolicies + (backend to transform(policy))),
        )
    }

    // Persist accounts and derive julesKeys / anthropicApiKey (mirrors the original settings screen).
    fun saveAccounts(list: List<AgentAccount>) {
        val julesKeys = list.filter { it.backend == CodingAgentBackend.JULES && it.enabled }.map { it.apiKey }
        val anthropic = list.firstOrNull { it.backend == CodingAgentBackend.CLAUDE_CODE && it.enabled }?.apiKey
            ?: settings.anthropicApiKey
        deps.settingsManager.saveSettings(
            settings.copy(agentAccounts = list, julesKeys = julesKeys, anthropicApiKey = anthropic),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            // Agent accounts — editable label + API key per account, like the original screen.
            SectionLabel("Comptes agents", "${settings.agentAccounts.size}")
            if (settings.agentAccounts.isEmpty()) {
                EmptyHint("Aucun compte. Ajoute une clé Jules / Claude ci-dessous.")
            }
            settings.agentAccounts.forEach { acc ->
                var label by remember(acc.id) { mutableStateOf(acc.label) }
                var key by remember(acc.id) { mutableStateOf(acc.apiKey) }
                V3Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(acc.backend.name, color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Switch(
                                checked = acc.enabled,
                                onCheckedChange = { on -> saveAccounts(settings.agentAccounts.map { if (it.id == acc.id) it.copy(enabled = on) else it }) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = V3.AccentInk, checkedTrackColor = V3.Accent,
                                    uncheckedThumbColor = V3.Muted, uncheckedTrackColor = V3.SurfaceHi,
                                ),
                            )
                            IconButton(onClick = { saveAccounts(settings.agentAccounts.filter { it.id != acc.id }) }) {
                                Icon(Icons.Filled.Delete, "Supprimer", tint = V3.Muted)
                            }
                        }
                        OutlinedTextField(
                            value = label, onValueChange = { v -> label = v; saveAccounts(settings.agentAccounts.map { if (it.id == acc.id) it.copy(label = v) else it }) },
                            label = { Text("Libellé") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = key, onValueChange = { v -> key = v; saveAccounts(settings.agentAccounts.map { if (it.id == acc.id) it.copy(apiKey = v.trim()) else it }) },
                            label = { Text("Clé API") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            AddAccountCard(defaultBackend = backend) { newAcc -> saveAccounts(settings.agentAccounts + newAcc) }

            // Queue strategy (per backend)
            SectionLabel("Stratégie de file", backend.name)
            V3Card {
                StepperRow2(
                    title = "Sessions en parallèle", mono = "parallelLimit", value = policy.parallelLimit,
                    onDec = { savePolicy { p -> p.copy(parallelLimit = (p.parallelLimit - 1).coerceAtLeast(1)) } },
                    onInc = { savePolicy { p -> p.copy(parallelLimit = p.parallelLimit + 1) } },
                )
                HorizontalDivider(color = V3.Border)
                StepperRow2(
                    title = "Limite quotidienne / compte", mono = "dailyLimitPerAccount", value = policy.dailyLimitPerAccount,
                    onDec = { savePolicy { p -> p.copy(dailyLimitPerAccount = (p.dailyLimitPerAccount - 1).coerceAtLeast(1)) } },
                    onInc = { savePolicy { p -> p.copy(dailyLimitPerAccount = p.dailyLimitPerAccount + 1) } },
                )
                HorizontalDivider(color = V3.Border)
                ToggleRow("Auto-merge si CI ✓", "autoMergeOnCiSuccess", policy.autoMergeOnCiSuccess) {
                    savePolicy { p -> p.copy(autoMergeOnCiSuccess = it) }
                }
            }

            // GitHub — editable token (merge / conflict resolution / CI).
            SectionLabel("GitHub")
            V3Card {
                Column(Modifier.padding(14.dp)) {
                    var ghToken by remember { mutableStateOf(settings.githubApiKey) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Token d'accès (PAT)", color = V3.Fg, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                        val ok = settings.githubApiKey.isNotBlank()
                        StatusPill(StatusVisual(if (ok) "connecté" else "absent", "", if (ok) V3.Success else V3.Danger), showEnum = false)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ghToken,
                        onValueChange = { v -> ghToken = v; deps.settingsManager.saveSettings(settings.copy(githubApiKey = v.trim())) },
                        label = { Text("GitHub token") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Merge, résolution de conflits et statut CI.", color = V3.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // General
            SectionLabel("Général")
            V3Card {
                ToggleRow("Journaux de debug", "debugLoggingEnabled", settings.debugLoggingEnabled) {
                    deps.settingsManager.setDebugLoggingEnabled(it)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountCard(defaultBackend: CodingAgentBackend, onAdd: (AgentAccount) -> Unit) {
    var backend by remember { mutableStateOf(defaultBackend) }
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    V3Card {
        Column(Modifier.padding(14.dp)) {
            Text("Ajouter un compte", color = V3.Fg, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CodingAgentBackend.JULES, CodingAgentBackend.CLAUDE_CODE).forEach { b ->
                    FilterChip(
                        selected = backend == b,
                        onClick = { backend = b },
                        label = { Text(b.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = V3.Accent, selectedLabelColor = V3.AccentInk,
                            containerColor = V3.SurfaceHi, labelColor = V3.Muted,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Libellé (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Clé API") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (key.isNotBlank()) {
                        onAdd(
                            AgentAccount(
                                id = UUID.randomUUID().toString(),
                                label = label.ifBlank { backend.name },
                                backend = backend,
                                apiKey = key.trim(),
                            ),
                        )
                        key = ""; label = ""
                    }
                },
                enabled = key.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text("Ajouter le compte") }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StepperRow2(title: String, mono: String, value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = V3.Fg, fontSize = 14.5.sp)
            Text(mono, color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        OutlinedIconButton(onClick = onDec) { Text("−", color = V3.Fg, fontSize = 18.sp) }
        Text("$value", color = V3.Accent, fontSize = 15.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(min = 34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedIconButton(onClick = onInc) { Text("+", color = V3.Fg, fontSize = 18.sp) }
    }
}

@Composable
private fun ToggleRow(title: String, mono: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = V3.Fg, fontSize = 14.5.sp)
            Text(mono, color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = V3.AccentInk, checkedTrackColor = V3.Accent,
                uncheckedThumbColor = V3.Muted, uncheckedTrackColor = V3.SurfaceHi,
            ),
        )
    }
}
