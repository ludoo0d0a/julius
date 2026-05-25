package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.ui.ColorHelper

sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Header(val title: String, val content: String) : MessageBlock()
    data class Plan(val content: String) : MessageBlock()
    data class BulletTitle(val title: String) : MessageBlock()
    data class ActionList(val actions: List<String>) : MessageBlock()
    data class GitHubPR(val url: String) : MessageBlock()
    data class GitHubBranch(val url: String) : MessageBlock()
    data class GitHubLog(val text: String) : MessageBlock()
    data class Instruction(val text: String, val keyword: String) : MessageBlock()
    data class SectionHeader(val title: String) : MessageBlock()
}

fun parseJulesMessage(text: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    val lines = text.lines()
    var currentText = StringBuilder()
    var i = 0

    val keywords = listOf("updated", "verified", "defined", "created", "refactored", "integrated")
    val sections = listOf("code reviewed", "completed pre-commit steps", "all plan steps completed", "plan approved")

    while (i < lines.size) {
        val line = lines[i].trim()
        val lowerLine = line.lowercase()

        when {
            line.startsWith("##") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val title = line.trim().replace(Regex("^#+"), "").trim().removeSuffix(".")
                val content = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("##") && !lines[i].trim().startsWith("<plan>")) {
                    content.add(lines[i])
                    i++
                }
                blocks.add(MessageBlock.Header(title, content.joinToString("\n").trim()))
                continue
            }
            line.startsWith("<plan>") || line.startsWith("Plan:") || line.startsWith("**Plan:**") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val content = mutableListOf<String>()

                if (line.startsWith("<plan>")) {
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("</plan>") && !lines[i].trim().startsWith("##")) {
                        content.add(lines[i])
                        i++
                    }
                    if (i < lines.size && lines[i].trim().startsWith("</plan>")) i++
                } else {
                    val firstLineContent = line.substringAfter(":").trim()
                    if (firstLineContent.isNotEmpty()) content.add(firstLineContent)
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].trim().startsWith("##") && !lines[i].trim().startsWith("<plan>")) {
                        content.add(lines[i])
                        i++
                    }
                }
                blocks.add(MessageBlock.Plan(content.joinToString("\n").trim()))
                continue
            }
            sections.any { lowerLine.startsWith(it) } -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                blocks.add(MessageBlock.SectionHeader(line))
            }
            keywords.any { lowerLine.startsWith(it) } -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val keyword = keywords.first { lowerLine.startsWith(it) }
                blocks.add(MessageBlock.Instruction(line, keyword))
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
            line.contains("github.com/") && line.contains("/tree/") -> {
                if (currentText.isNotEmpty()) {
                    blocks.add(MessageBlock.Text(currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                val regex = Regex("https?://github\\.com/[^\\s/]+/[^\\s/]+/tree/[^/\\s?]+")
                val match = regex.find(line)
                if (match != null) {
                    blocks.add(MessageBlock.GitHubBranch(match.value))
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
    onSolveConflicts: ((String) -> Unit)? = null,
    onAutoSolveConflicts: ((String) -> Unit)? = null,
    onCreatePr: ((fr.geoking.julius.api.github.GitHubBranchRef) -> Unit)? = null,
    prDetails: fr.geoking.julius.persistence.JulesSessionEntity? = null,
    julesRepository: fr.geoking.julius.repository.JulesRepository? = null,
    githubToken: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
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
            .combinedClickable(
                onClick = {
                    val textToCopy = if (item is JulesChatItem.UserMessage) item.text else (item as JulesChatItem.AgentMessage).text
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                android.content.ClipData.newPlainText("jules_message", textToCopy)
                            )
                        )
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClick = onSpeak
            )
    ) {
        blocks.forEach { block ->
            RenderMessageBlock(
                block = block,
                baseFontSize = baseFontSize,
                onMergePr = onMergePr,
                onSolveConflicts = onSolveConflicts,
                onAutoSolveConflicts = onAutoSolveConflicts,
                onCreatePr = onCreatePr,
                prDetails = prDetails,
                julesRepository = julesRepository,
                githubToken = githubToken
            )
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
fun RenderMessageBlock(
    block: MessageBlock,
    baseFontSize: Int,
    textColor: Color = Color.White,
    onMergePr: ((String) -> Unit)? = null,
    onSolveConflicts: ((String) -> Unit)? = null,
    onAutoSolveConflicts: ((String) -> Unit)? = null,
    onCreatePr: ((fr.geoking.julius.api.github.GitHubBranchRef) -> Unit)? = null,
    prDetails: fr.geoking.julius.persistence.JulesSessionEntity? = null,
    julesRepository: fr.geoking.julius.repository.JulesRepository? = null,
    githubToken: String? = null
) {
    when (block) {
        is MessageBlock.Text -> {
            Text(block.content, color = textColor, fontSize = baseFontSize.sp)
        }
        is MessageBlock.Header -> {
            CollapsibleBlock(title = block.title, content = block.content, baseFontSize = baseFontSize, isAccent = true, textColor = textColor)
        }
        is MessageBlock.Plan -> {
            CollapsibleBlock(
                title = "Plan 🚀",
                content = if (block.content.isNotBlank()) "🚀 ${block.content}" else "",
                baseFontSize = baseFontSize,
                isAccent = false,
                initialExpanded = false,
                textColor = textColor
            )
        }
        is MessageBlock.Instruction -> {
            InstructionItem(block.text, block.keyword, baseFontSize, textColor = textColor)
        }
        is MessageBlock.SectionHeader -> {
            SectionHeaderItem(block.title, baseFontSize, textColor = textColor)
        }
        is MessageBlock.BulletTitle -> {
            Text(block.title, color = textColor, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold)
        }
        is MessageBlock.ActionList -> {
            block.actions.forEachIndexed { index, action ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${index + 1}. ", color = ColorHelper.JulesAccent, fontSize = baseFontSize.sp, fontWeight = FontWeight.Bold)
                    Text(action, color = textColor, fontSize = baseFontSize.sp)
                }
            }
        }
        is MessageBlock.GitHubPR -> {
            GitHubResourceCard(
                url = block.url,
                onMergePr = onMergePr,
                onSolveConflicts = onSolveConflicts,
                onAutoSolveConflicts = onAutoSolveConflicts,
                onCreatePr = onCreatePr,
                session = prDetails,
                julesRepository = julesRepository,
                githubToken = githubToken
            )
        }
        is MessageBlock.GitHubBranch -> {
            GitHubResourceCard(
                url = block.url,
                onMergePr = onMergePr,
                onSolveConflicts = onSolveConflicts,
                onAutoSolveConflicts = onAutoSolveConflicts,
                onCreatePr = onCreatePr,
                session = prDetails,
                julesRepository = julesRepository,
                githubToken = githubToken
            )
        }
        is MessageBlock.GitHubLog -> {
            GitHubLogBlock(block.text)
        }
    }
}

@Composable
private fun InstructionItem(text: String, keyword: String, baseFontSize: Int, textColor: Color = Color.White) {
    val icon = when (keyword) {
        "updated" -> Icons.Default.Refresh
        "verified" -> Icons.Default.AssignmentTurnedIn
        "defined" -> Icons.Default.List
        "created" -> Icons.Default.Add
        "refactored" -> Icons.Default.Build
        "integrated" -> Icons.Default.Link
        else -> Icons.Default.Check
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorHelper.JulesAccent.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = baseFontSize.sp
        )
    }
}

@Composable
private fun SectionHeaderItem(title: String, baseFontSize: Int, textColor: Color = Color.White) {
    val icon = when {
        title.lowercase().contains("pre-commit") -> Icons.AutoMirrored.Filled.Rule
        title.lowercase().contains("plan steps completed") -> Icons.Default.DoneAll
        title.lowercase().contains("plan approved") -> Icons.Default.DoneAll
        title.lowercase().contains("reviewed") -> Icons.AutoMirrored.Filled.Rule
        else -> Icons.Default.Check
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Green.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = textColor,
            fontSize = (baseFontSize + 1).sp,
            fontWeight = FontWeight.Bold
        )
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
private fun GitHubResourceCard(
    url: String,
    onMergePr: ((String) -> Unit)?,
    onSolveConflicts: ((String) -> Unit)? = null,
    onAutoSolveConflicts: ((String) -> Unit)? = null,
    onCreatePr: ((fr.geoking.julius.api.github.GitHubBranchRef) -> Unit)? = null,
    session: fr.geoking.julius.persistence.JulesSessionEntity?,
    julesRepository: fr.geoking.julius.repository.JulesRepository? = null,
    githubToken: String? = null
) {
    var details by remember { mutableStateOf<fr.geoking.julius.api.github.GitHubClient.GitHubPullRequestDetail?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(url, session, githubToken) {
        if (githubToken != null && julesRepository != null) {
            if (session == null || session.prUrl != url) {
                loading = true
                val res = julesRepository.getGitHubResourceDetails(githubToken, url)
                details = res.getOrNull()
                loading = false
            }
        }
    }

    val prTitle = details?.title ?: (if (session != null && session.prUrl == url) session.prTitle else null)
    val prRepo = details?.repository?.fullName ?: (if (session != null && session.prUrl == url) session.prRepo else null)
    val prState = details?.state?.let { if (details?.merged == true) "merged" else it } ?: (if (session != null && session.prUrl == url) session.prState else null)
    val prMergeable = details?.mergeable ?: (if (session != null && session.prUrl == url) session.prMergeable else null)
    val prBranch = details?.head?.ref ?: (if (session != null && session.prUrl == url) session.prBranch else null)
    val prNumber = details?.number ?: (if (session != null && session.prUrl == url) session.prId?.toIntOrNull() else null)

    val isBranch = url.contains("/tree/")
    val branchRef = remember(url) { fr.geoking.julius.api.github.parseGitHubBranchUrl(url) }

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
                    text = if (isBranch && prState == null) "GitHub Branch" else "GitHub Pull Request",
                    color = ColorHelper.JulesAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (loading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp, color = ColorHelper.JulesAccent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (prTitle != null || prRepo != null || prState != null) {
                Text(
                    text = prTitle ?: "No title",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!prRepo.isNullOrBlank()) {
                    Text(
                        text = prRepo,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (statusText, statusColor) = when (prState) {
                        "merged" -> "Merged" to Color.Green
                        "closed" -> "Closed" to Color.Red
                        "open" -> "Open" to Color.Cyan
                        else -> "Unknown" to Color.Gray
                    }

                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val branchToShow = prBranch ?: branchRef?.branch
                    if (!branchToShow.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = branchToShow,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    if (prNumber != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "#$prNumber",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (prState == "open") {
                    if (prMergeable == true && onMergePr != null) {
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
                    } else if (prMergeable == false) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("This PR has merge conflicts.", color = Color.Red, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onAutoSolveConflicts?.invoke(url) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Auto solve", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onSolveConflicts?.invoke(url) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Manual solve", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (isBranch && branchRef != null && prState == null && !loading) {
                Text(
                    text = branchRef.branch,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${branchRef.owner}/${branchRef.repo}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = { onCreatePr?.invoke(branchRef) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ColorHelper.JulesAccent.copy(alpha = 0.2f),
                        contentColor = ColorHelper.JulesAccent
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publish Branch", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Fallback
                Text(url, color = Color.White, fontSize = 12.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, modifier = Modifier.clickable { /* open url? */ })
            }
        }
    }
}

@Composable
private fun CollapsibleBlock(
    title: String,
    content: String,
    baseFontSize: Int,
    isAccent: Boolean,
    initialExpanded: Boolean = false,
    textColor: Color = Color.White
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

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
                color = if (isAccent) ColorHelper.JulesAccent else textColor,
                fontSize = baseFontSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (expanded && content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                content,
                color = textColor.copy(alpha = 0.9f),
                fontSize = (baseFontSize - 1).sp,
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }
}
