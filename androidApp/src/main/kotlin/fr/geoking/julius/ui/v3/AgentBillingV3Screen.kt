package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.ui.AgentApiUsageTarget
import fr.geoking.julius.ui.AgentBillingInfoPage

@Composable
fun AgentBillingV3Screen(targetKey: String) {
    val target = AgentApiUsageTarget.decode(targetKey) ?: return
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        AgentBillingInfoPage(target = target)
    }
}

@Composable
fun AgentBillingNavV3Row(title: String, subtitle: String, onClick: () -> Unit) {
    V3Card {
        V3Row(
            title = title,
            subtitle = subtitle,
            onClick = onClick,
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = V3.Faint) },
        )
    }
}
