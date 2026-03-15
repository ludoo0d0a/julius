package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.HistoryItem
import fr.geoking.julius.shared.toHistoryScreenState

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)

@Composable
fun HistoryScreen(
    state: ConversationState,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val screenState = state.toHistoryScreenState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepPurple, DarkBackground)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Lavender,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = screenState.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (screenState.isEmpty) {
                Text(
                    text = screenState.emptyMessage,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    fontSize = 16.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    screenState.items.forEach { item ->
                        HistoryMessageItem(item = item)
                        Spacer(modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessageItem(item: HistoryItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (item.isUser) Lavender.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (item.isUser) 16.dp else 4.dp,
                bottomEnd = if (item.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = item.text,
                color = Color.White,
                modifier = Modifier.padding(14.dp),
                fontSize = 15.sp
            )
        }
    }
}
