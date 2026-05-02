package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
    onSpeak: () -> Unit
) {
    val text = if (item is JulesChatItem.UserMessage) item.text
    else (item as JulesChatItem.AgentMessage).let { if (it.subItems.size == 1) it.subItems.first().text else it.text }

    val blocks = remember(text) { parseJulesMessage(text) }
    val timestamp = if (item is JulesChatItem.UserMessage) item.createTime else (item as JulesChatItem.AgentMessage).createTime

    Column(
        modifier = Modifier
            .padding(12.dp)
            .combinedClickable(onClick = {}, onLongClick = onSpeak)
    ) {
        blocks.forEach { block ->
            RenderBlock(block, baseFontSize)
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
private fun RenderBlock(block: MessageBlock, baseFontSize: Int) {
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
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
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
