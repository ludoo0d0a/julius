package fr.geoking.julius.ui.harness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.jules.JulesConflictResolutionSheet

@Composable
fun PrConflictResolutionScreen(
    session: JulesSessionEntity,
    githubToken: String,
    julesRepository: JulesRepository,
    onBack: () -> Unit,
) {
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    val prUrl = session.prUrl

    LaunchedEffect(prUrl) {
        if (prUrl != null && githubToken.isNotBlank()) {
            val res = julesRepository.getConflictingFiles(githubToken, prUrl)
            if (res.isSuccess) files = res.getOrDefault(emptyList())
        }
    }

    BackHandler(onBack = onBack)

    Surface(modifier = Modifier.fillMaxSize(), color = ColorHelper.JulesBg) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            if (files.isEmpty()) {
                Text("No conflicting files found.", color = Color.White, modifier = Modifier.padding(16.dp))
            } else {
                JulesConflictResolutionSheet(
                    session = session,
                    files = files,
                    githubToken = githubToken,
                    julesRepository = julesRepository,
                    onDismiss = onBack,
                )
            }
        }
    }
}
