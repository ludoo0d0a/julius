package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.ui.ColorHelper

sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Header(val title: String, val content: String) : MessageBlock()
    data class Plan(val content: String) : MessageBlock()
    data class BulletTitle(val title: String) : MessageBlock()
    data class ActionList(val actions: List<String>) : MessageBlock()
    data class GitHubPR(val url: String) : MessageBlock()
    data class GitHubLog(val text: String) : MessageBlock()
}

fun parseJulesMessage(text: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    val lines = text.lines()
    var currentText = StringBuilder()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trim()

        when {
            line.startsWith("###") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val title = line.removePrefix("###").trim()
                val content = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("###") && !lines[i].trim().startsWith("<plan>")) {
                    content.add(lines[i])
                    i++
                }
                blocks.add(MessageBlock.Header(title, content.joinToString("\n").trim()))
                continue
            }
            line.startsWith("<plan>") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val content = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("</plan>") && !lines[i].trim().startsWith("###")) {
                    content.add(lines[i])
                    i++
                }
                if (i < lines.size && lines[i].trim().startsWith("</plan>")) i++
                blocks.add(MessageBlock.Plan(content.joinToString("\n").trim()))
                continue
            }
            line.startsWith("**") && line.endsWith("**") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                blocks.add(MessageBlock.BulletTitle(line.removeSurrounding("**").trim()))
            }
            line.contains("github.com/") && line.contains("/pull/") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                // Try to extract the full URL from the line
                val regex = Regex("https?://github\\.com/[^\\s/]+/[^\\s/]+/pull/\\d+")
                val match = regex.find(line)
                if (match != null) {
                    blocks.add(MessageBlock.GitHubPR(match.value))
                    val remaining = line.replace(match.value, "").trim()
                    if (remaining.isNotEmpty()) {
                        currentText.append(remaining).append("\n")
                    }
                } else {
                    currentText.append(lines[i]).append("\n")
                }
            }
            line.getOrNull(0)?.isDigit() == true && line.contains(". ") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val actions = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.getOrNull(0)?.isDigit() == true && l.contains(". ")) {
                        actions.add(l.substringAfter(". ").trim())
                        i++
                    } else break
                }
                blocks.add(MessageBlock.ActionList(actions))
                continue
            }
            else -> {
                currentText.append(lines[i]).append("\n")
            }
        }
        i++
    }

    if (currentText.isNotEmpty()) {
        blocks.add(MessageBlock.Text(currentText.toString().trim()))
    }

    return blocks
}

@Composable
fun JulesMessageContent(
    item: JulesChatItem,
    baseFontSize: Int = 14,
    onSpeak: () -> Unit,
    onMergePr: ((String) -> Unit)? = null,
    prDetails: fr.geoking.julius.persistence.JulesSessionEntity? = null
) {
    val blocks = remember(item) {
        if (item is JulesChatItem.UserMessage) {
            parseJulesMessage(item.text)
        } else {
            val agent = item as JulesChatItem.AgentMessage
            agent.subItems.flatMap { sub ->
                if (sub.type == "github_log") {
                    listOf(MessageBlock.GitHubLog(sub.text))
                } else {
                    parseJulesMessage(sub.text)
                }
            }
        }
    }
    val timestamp = if (item is JulesChatItem.UserMessage) item.createTime else (item as JulesChatItem.AgentMessage).createTime

    Column(
        modifier = Modifier
            .padding(12.dp)
            .combinedClickable(onClick = {}, onLongClick = onSpeak)
    ) {
        blocks.forEach { block ->
            RenderBlock(block, baseFontSize, onMergePr, prDetails)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = timestamp.takeLast(5),
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun RenderBlock(
    block: MessageBlock,
    baseFontSize: Int,
    onMergePr: ((String) -> Unit)? = null,
    prDetails: fr.geoking.julius.persistence.JulesSessionEntity? = null
) {
    when (block) {
        is MessageBlock.Text -> {
            Text(block.content, color = Color.White, fontSize = baseFontSize.sp)
        }
        is MessageBlock.Header -> {
            CollapsibleBlock(title = block.title, content = block.content, baseFontSize = baseFontSize, isAccent = true)
        }
        is MessageBlock.Plan -> {
            CollapsibleBlock(title = "Plan", content = block.content, baseFontSize = baseFontSize, isAccent = false)
        }
        is MessageBlock.BulletTitle -> {
            Text(block.title, color = Color.White, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold)
        }
        is MessageBlock.ActionList -> {
            block.actions.forEachIndexed { index, action ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${index + 1}. ", color = ColorHelper.JulesAccent, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold)
                    Text(action, color = Color.White, fontSize = baseFontSize.sp)
                }
            }
        }
        is MessageBlock.GitHubPR -> {
            GitHubPRCard(block.url, onMergePr, prDetails)
        }
        is MessageBlock.GitHubLog -> {
            GitHubLogBlock(block.text)
        }
    }
}

@Composable
private fun GitHubLogBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            tint = ColorHelper.JulesAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun GitHubPRCard(
    url: String,
    onMergePr: ((String) -> Unit)?,
    session: fr.geoking.julius.persistence.JulesSessionEntity?
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Code,
                    contentDescription = null,
                    tint = ColorHelper.JulesAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GitHub Pull Request",
                    color = ColorHelper.JulesAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (session != null && session.prUrl == url) {
                Text(
                    text = session.prTitle ?: "No title",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!session.prRepo.isNullOrBlank()) {
                    Text(
                        text = session.prRepo,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (statusText, statusColor) = when (session.prState) {
                        "merged" -> "Merged" to Color.Green
                        "closed" -> "Closed" to Color.Red
                        else -> "Open" to Color.Cyan
                    }

                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!session.prBranch.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = session.prBranch,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                if (session.prState == "open" && session.prMergeable == true && onMergePr != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = { onMergePr(url) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Green.copy(alpha = 0.2f),
                            contentColor = Color.Green
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Merge PR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Fallback if session details not available
                Text(url, color = Color.White, fontSize = 12.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
    }
}

@Composable
private fun CollapsibleBlock(title: String, content: String, baseFontSize: Int, isAccent: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isAccent) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                title,
                color = if (isAccent) ColorHelper.JulesAccent else Color.White,
                fontSize = baseFontSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (expanded && content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                content,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (baseFontSize - 1).sp,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }
}
