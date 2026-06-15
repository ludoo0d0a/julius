package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.queue.queuePolicyFor
import kotlinx.coroutines.launch

@Composable
fun SchedulerV3Screen(
    deps: V3Deps,
    onOpenFeature: (String) -> Unit,
    onSeeAllFeatures: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by deps.settingsManager.settings.collectAsState()
    val status by deps.queueEngine.status.collectAsState()
    val featuresFlow = remember { deps.featureRepository.getAllFeatures() }
    val features by featuresFlow.collectAsState(initial = emptyList())

    val backend = settings.codingAgentBackend
    val policy = settings.queuePolicyFor(backend)

    val quotaUsed = status.accounts.sumOf { it.usedToday }
    val quotaLimit = status.accounts.sumOf { it.dailyLimit }
    val latest = remember(features) { features.sortedByDescending { it.updatedAt }.take(6) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        V3LargeTitle("Scheduler", "Scheduler", "Séquenceur d'agents · file & quotas")

        Column(Modifier.padding(horizontal = 18.dp)) {
            // KPIs
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                KpiCell("${status.activeCount}/${status.parallelLimit}", "En cours", accent = true, modifier = Modifier.weight(1f))
                KpiCell("${status.pendingCount}", "En file", modifier = Modifier.weight(1f))
                KpiCell(if (quotaLimit > 0) "$quotaUsed/$quotaLimit" else "—", "Quota jour", modifier = Modifier.weight(1f))
            }

            // Latest features
            SectionLabel("Dernières features", "tous projets")
            if (latest.isEmpty()) {
                EmptyHint("Aucune feature pour l'instant.")
            } else {
                V3Card {
                    latest.forEachIndexed { i, f ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        FeatureRow(f, onClick = { onOpenFeature(f.id) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onSeeAllFeatures) { Text("Voir toutes les features", color = V3.Accent) }
            }

            // Accounts / quotas
            SectionLabel("Comptes · quotas", "dailyLimitPerAccount ${policy.dailyLimitPerAccount}")
            if (status.accounts.isEmpty()) {
                EmptyHint("Aucun compte agent configuré.")
            } else {
                V3Card {
                    status.accounts.forEachIndexed { i, acc ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        val frac = if (acc.dailyLimit > 0) (acc.usedToday.toFloat() / acc.dailyLimit).coerceIn(0f, 1f) else 0f
                        val tint = when {
                            acc.dailyLimit > 0 && acc.usedToday >= acc.dailyLimit -> V3.Danger
                            frac >= 0.7f -> V3.Warn
                            else -> V3.Accent
                        }
                        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(acc.label, color = V3.Fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(
                                    if (acc.dailyLimit > 0) "${acc.usedToday}/${acc.dailyLimit}" else "${acc.usedToday}",
                                    color = tint, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                )
                                if (acc.activeSessions > 0) {
                                    Text(" · ${acc.activeSessions} act.", color = V3.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = tint,
                                trackColor = V3.SurfaceHi,
                            )
                        }
                    }
                }
            }

            // Options
            SectionLabel("Options")
            V3Card {
                ControlRow(
                    title = "File en pause",
                    subtitle = "Suspend le démarrage de nouvelles sessions",
                    checked = policy.queuePaused,
                ) {
                    deps.settingsManager.saveSettings(
                        settings.copy(queuePolicies = settings.queuePolicies + (backend to policy.copy(queuePaused = it))),
                    )
                    scope.launch { deps.queueEngine.tick() }
                }
                HorizontalDivider(color = V3.Border)
                ControlRow(
                    title = "Auto-merge si CI ✓",
                    subtitle = "autoMergeOnCiSuccess",
                    subtitleMono = true,
                    checked = policy.autoMergeOnCiSuccess,
                ) {
                    deps.settingsManager.saveSettings(
                        settings.copy(queuePolicies = settings.queuePolicies + (backend to policy.copy(autoMergeOnCiSuccess = it))),
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ControlRow(
    title: String,
    subtitle: String,
    subtitleMono: Boolean = false,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = V3.Fg, fontSize = 14.5.sp)
            Text(
                subtitle, color = V3.Muted, fontSize = if (subtitleMono) 11.sp else 12.sp,
                fontFamily = if (subtitleMono) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.padding(top = 2.dp),
            )
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

@Composable
fun FeatureRow(f: FeatureEntity, onClick: () -> Unit) {
    val v = featureStatusVisual(f.status)
    V3Row(
        title = f.title,
        subtitle = f.sourceName,
        leadingIcon = Icons.Filled.Schedule,
        leadingTint = v.color,
        onClick = onClick,
        trailing = { StatusPill(v) },
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = V3.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp, horizontal = 6.dp))
}
