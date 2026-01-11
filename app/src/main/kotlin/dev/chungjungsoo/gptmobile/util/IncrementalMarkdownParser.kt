package dev.chungjungsoo.gptmobile.util

/**
 * 基于 incremark 思想的增量 Markdown 分块解析器。
 *
 * 目标：把“每次重解析整段 Markdown”变成“只解析最新的 Pending 区”，
 * 并用稳定 ID（字符偏移量）让 UI 只重绘最后一块。
 */
class IncrementalMarkdownParser {
    private val completedBlocks = mutableListOf<MarkdownBlock>()
    private val pendingBuffer = StringBuilder()

    /** 已归档字符总长度（用于生成稳定 ID） */
    private var globalOffset: Int = 0

    fun reset() {
        completedBlocks.clear()
        pendingBuffer.clear()
        globalOffset = 0
    }

    /**
     * 追加流式 chunk，返回最新的块列表。
     */
    fun append(chunk: String): IncrementalUpdate {
        if (chunk.isEmpty()) {
            return IncrementalUpdate(
                allBlocks = completedBlocks.toList(),
                newCompletedCount = 0
            )
        }

        pendingBuffer.append(chunk)

        val boundaryIndex = findStableBoundary(pendingBuffer)
        var newlyArchived = 0

        if (boundaryIndex != -1) {
            val stableText = pendingBuffer.substring(0, boundaryIndex)
            val remainingText = pendingBuffer.substring(boundaryIndex)

            val newStableBlocks = parseMarkdownBlocks(
                text = stableText,
                startOffset = globalOffset,
                isPending = false
            )

            if (newStableBlocks.isNotEmpty()) {
                completedBlocks.addAll(newStableBlocks)
                newlyArchived = newStableBlocks.size
            }

            globalOffset += stableText.length
            pendingBuffer.clear()
            pendingBuffer.append(remainingText)
        }

        val pendingBlocks = if (pendingBuffer.isNotEmpty()) {
            parseMarkdownBlocks(
                text = pendingBuffer.toString(),
                startOffset = globalOffset,
                isPending = true
            )
        } else {
            emptyList()
        }

        return IncrementalUpdate(
            allBlocks = completedBlocks + pendingBlocks,
            newCompletedCount = newlyArchived
        )
    }

    /**
     * 稳定边界检测：
     * - 如果仍处于代码块（``` 未闭合），绝不切分。
     * - 否则优先切在最后一个空行（\n\n）之后；没有空行再退化到最后一个换行符之后。
     */
    private fun findStableBoundary(buffer: StringBuilder): Int {
        if (buffer.isEmpty()) return -1

        val text = buffer.toString()
        val codeFenceCount = countTripleBackticks(text)
        val inCodeFence = (codeFenceCount % 2 == 1)
        if (inCodeFence) return -1

        val lastDoubleNewline = text.lastIndexOf("\n\n")
        if (lastDoubleNewline != -1) return lastDoubleNewline + 2

        val lastNewline = text.lastIndexOf('\n')
        if (lastNewline != -1) return lastNewline + 1

        return -1
    }

    private fun countTripleBackticks(text: String): Int {
        var count = 0
        var index = 0
        while (true) {
            val next = text.indexOf("```", startIndex = index)
            if (next == -1) return count
            count += 1
            index = next + 3
        }
    }

    /**
     * 将一段 Markdown 解析为多个块。
     *
     * 注意：这里的“解析”是轻量级分块，不依赖第三方 Markdown AST。
     * 我们只需要：
     * 1) 尽量在空行处分块；
     * 2) 代码块（```...```）作为独立块；
     * 3) 生成稳定 ID：blk_{startOffset + 块起始偏移}
     */
    private fun parseMarkdownBlocks(text: String, startOffset: Int, isPending: Boolean): List<MarkdownBlock> {
        if (text.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()

        var cursor = 0
        var blockStart = 0
        var inCodeFence = false
        var currentType: BlockType = BlockType.PARAGRAPH

        fun flushBlock(endExclusive: Int) {
            if (endExclusive <= blockStart) return
            val raw = text.substring(blockStart, endExclusive)
            if (raw.isBlank()) {
                blockStart = endExclusive
                return
            }

            val absoluteOffset = startOffset + blockStart
            blocks.add(
                MarkdownBlock(
                    id = "blk_$absoluteOffset",
                    content = raw,
                    type = currentType,
                    isPending = isPending
                )
            )
            blockStart = endExclusive
            currentType = BlockType.PARAGRAPH
        }

        while (cursor <= text.length) {
            val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
            val line = text.substring(cursor, lineEnd)
            val lineWithNewlineEnd = if (lineEnd < text.length) lineEnd + 1 else lineEnd

            val trimmed = line.trimStart()
            val isFenceLine = trimmed.startsWith("```")

            if (!inCodeFence) {
                if (isFenceLine) {
                    // 先把 fence 前面的普通内容刷掉
                    if (cursor > blockStart) {
                        currentType = guessType(text.substring(blockStart, cursor))
                        flushBlock(cursor)
                    }
                    inCodeFence = true
                    currentType = BlockType.CODE_BLOCK
                } else if (line.isBlank()) {
                    // 空行：把当前段落（包含这行换行）刷掉，保留空行用于段落间距
                    currentType = guessType(text.substring(blockStart, lineWithNewlineEnd))
                    flushBlock(lineWithNewlineEnd)
                }
            } else {
                // 代码块内：遇到 fence 行即闭合，并把代码块整体刷掉
                if (isFenceLine) {
                    inCodeFence = false
                    currentType = BlockType.CODE_BLOCK
                    flushBlock(lineWithNewlineEnd)
                }
            }

            if (lineEnd == text.length) break
            cursor = lineWithNewlineEnd
        }

        // 尾块
        if (blockStart < text.length) {
            currentType = if (inCodeFence) BlockType.CODE_BLOCK else guessType(text.substring(blockStart))
            flushBlock(text.length)
        }

        return blocks
    }

    private fun guessType(raw: String): BlockType {
        val t = raw.trimStart()
        if (t.startsWith("```")) return BlockType.CODE_BLOCK
        if (t.startsWith("#")) return BlockType.HEADING
        if (t.startsWith("-") || t.startsWith("+") || t.startsWith("*")) return BlockType.LIST_ITEM
        return BlockType.PARAGRAPH
    }
}

// 基础数据单元：Markdown 块（段落、标题、代码块等）
data class MarkdownBlock(
    val id: String,
    val content: String,
    val type: BlockType,
    val isPending: Boolean
)

enum class BlockType {
    PARAGRAPH,
    HEADING,
    CODE_BLOCK,
    LIST_ITEM,
    OTHER
}

// 每次追加后的返回结果
data class IncrementalUpdate(
    val allBlocks: List<MarkdownBlock>,
    val newCompletedCount: Int
)
