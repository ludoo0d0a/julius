package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.queue.QueuePolicy
import fr.geoking.julius.queue.queuePolicyFor

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        V3LargeTitle("Réglages", "Réglages")

        Column(Modifier.padding(horizontal = 18.dp)) {
            // Agent accounts
            SectionLabel("Comptes agents", "${settings.agentAccounts.size}")
            if (settings.agentAccounts.isEmpty()) {
                EmptyHint("Aucun compte. Ajoute une clé Jules / Claude dans les réglages avancés.")
            } else {
                V3Card {
                    settings.agentAccounts.forEachIndexed { i, acc ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.label, color = V3.Fg, fontSize = 14.5.sp)
                                Text(acc.backend.name, color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Switch(
                                checked = acc.enabled,
                                onCheckedChange = { on ->
                                    deps.settingsManager.saveSettings(
                                        settings.copy(agentAccounts = settings.agentAccounts.map {
                                            if (it.id == acc.id) it.copy(enabled = on) else it
                                        }),
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = V3.AccentInk, checkedTrackColor = V3.Accent,
                                    uncheckedThumbColor = V3.Muted, uncheckedTrackColor = V3.SurfaceHi,
                                ),
                            )
                        }
                    }
                }
            }

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

            // GitHub
            SectionLabel("GitHub")
            V3Card {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Token d'accès", color = V3.Fg, fontSize = 14.5.sp)
                        Text(
                            if (settings.githubApiKey.isNotBlank()) "Merge & résolution de conflits actifs" else "Non configuré",
                            color = V3.Muted, fontSize = 12.sp,
                        )
                    }
                    val ok = settings.githubApiKey.isNotBlank()
                    StatusPill(StatusVisual(if (ok) "connecté" else "absent", "", if (ok) V3.Success else V3.Danger), showEnum = false)
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
