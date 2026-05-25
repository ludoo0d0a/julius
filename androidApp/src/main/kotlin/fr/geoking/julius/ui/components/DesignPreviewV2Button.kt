package fr.geoking.julius.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/** Opens the Jules Design Assistant V2 UI preview (mock data). */
@Composable
fun DesignPreviewV2Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Default.Preview,
            contentDescription = "Aperçu interface V2",
            tint = Color(0xFF90CAF9),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun DesignPreviewV2ButtonPreview() {
    DesignPreviewV2Button(onClick = {})
}
