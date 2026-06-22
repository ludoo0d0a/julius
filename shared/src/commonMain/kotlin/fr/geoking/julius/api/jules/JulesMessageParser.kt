package fr.geoking.julius.api.jules

sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Header(val title: String, val content: String) : MessageBlock()
    data class Plan(val content: String) : MessageBlock()
    data class BulletTitle(val title: String) : MessageBlock()
    data class ActionList(val actions: List<String>) : MessageBlock()
    data class GitHubPR(val url: String) : MessageBlock()
    data class GitHubBranch(val url: String) : MessageBlock()
    data class GitHubLog(val text: String) : MessageBlock()
    data class Error(val text: String) : MessageBlock()
    data class Instruction(val text: String, val keyword: String) : MessageBlock()
    data class SectionHeader(val title: String) : MessageBlock()
}

@kotlinx.serialization.Serializable
data class JulesErrorDetails(
    val error: String,
    val url: String? = null,
    val httpCode: Int? = null,
    val requestBody: String? = null
)

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
