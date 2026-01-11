package dev.chungjungsoo.gptmobile.util

object MarkdownUtils {
    private val imageRegex = Regex("""!\[[^\]]*]\([^\)]*\)""")
    private val linkRegex = Regex("""\[([^\]]+)]\([^\)]*\)""")
    private val inlineCodeRegex = Regex("""`([^`]+)`""")
    private val referenceRegex = Regex("""\[[0-9^]+]""")
    private val leadingListMarkerRegex = Regex("""(?m)^\s*[+-]\s+""")

    fun stripMarkdown(markdown: String): String {
        var text = markdown
        
        // 1. 移除代码块
        while (text.contains("```")) {
            val start = text.indexOf("```")
            val end = text.indexOf("```", start + 3)
            if (end != -1) {
                text = text.removeRange(start, end + 3)
            } else {
                break
            }
        }

        // 2. 移除图片 ![alt](url)
        // 说明：这里使用标准的 Java/Kotlin 正则转义，避免在 Android 上出现 PatternSyntaxException。
        text = text.replace(imageRegex, "")

        // 3. 移除链接 [text](url) -> text
        text = text.replace(linkRegex) { matchResult ->
            matchResult.groupValues[1]
        }

        // 4. 移除行内代码 `code` -> code
        text = text.replace(inlineCodeRegex) { matchResult ->
            matchResult.groupValues[1]
        }

        // 5. 移除引用标记 [1], [^1]
        // [0-9^]+ 匹配数字或插入符号（由于 ^ 不是第一个字符，所以是字面量）
        text = text.replace(referenceRegex, "")

        // 6. 移除其他常见符号 (*, #, >, |, ~, -, _)
        val symbols = charArrayOf('*', '#', '>', '|', '~', '_')
        for (char in symbols) {
            text = text.replace(char.toString(), "")
        }
        
        // 7. 移除行首的列表符号（- 或 +）
        // 仅移除“列表标记 + 后续空白”，避免误伤正常的正负号或减号表达式。
        text = text.replace(leadingListMarkerRegex, "")

        // 8. 压缩多余换行
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n")
        }
        
        return text.trim()
    }
}
