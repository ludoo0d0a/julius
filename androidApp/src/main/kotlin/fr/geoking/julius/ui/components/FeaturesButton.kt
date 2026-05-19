package fr.geoking.julius.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun FeaturesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Default.List,
            contentDescription = "Features",
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun FeaturesButtonPreview() {
    FeaturesButton(onClick = {})
}
