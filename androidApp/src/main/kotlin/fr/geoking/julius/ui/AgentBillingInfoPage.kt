package fr.geoking.julius.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.ui.openExternalUrl

@Composable
fun AgentBillingInfoPage(target: AgentApiUsageTarget) {
    val info = AgentApiUsageCatalog.infoFor(target)
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            info.summary,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        BillingSection("Billing model", info.billingModel)
        if (info.tokenUsage.isNotEmpty()) {
            BillingSection("Token & usage metering", bullets = info.tokenUsage)
        }
        if (info.rateLimits.isNotEmpty()) {
            BillingSection("Rate limits & quotas", bullets = info.rateLimits)
        }
        if (info.costNotes.isNotEmpty()) {
            BillingSection("Cost tips", bullets = info.costNotes)
        }

        if (info.docLinks.isNotEmpty()) {
            SettingsHeader("Official documentation")
            info.docLinks.forEach { link ->
                TextButton(
                    onClick = { context.openExternalUrl(link.url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(link.label, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BillingSection(title: String, body: String? = null, bullets: List<String> = emptyList()) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SettingsHeader(title)
    body?.let {
        Text(
            it,
            color = muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    bullets.forEach { line ->
        Text(
            "• $line",
            color = muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** Compact row linking to the billing page (Settings list style). */
@Composable
fun AgentBillingNavRow(onClick: () -> Unit) {
    SettingsListItem(
        title = "Usage, tokens & billing",
        subtitle = "Rate limits, quotas, and cost model",
        onClick = onClick,
    )
}
