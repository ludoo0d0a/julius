package fr.geoking.julius.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.R

@Composable
fun JulesButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = if (onLongClick != null) {
            modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
            modifier
        },
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_jules),
            contentDescription = "Open Jules",
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun JulesButtonPreview() {
    JulesButton(onClick = {})
}
