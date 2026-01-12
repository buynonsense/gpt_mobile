package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.text.util.Linkify
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.util.MarkdownBlock
import dev.chungjungsoo.gptmobile.util.getPlatformAPIBrandText
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    isLoading: Boolean,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )

    Column(horizontalAlignment = Alignment.End) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(32.dp),
            colors = cardColor
        ) {
            MarkdownText(
                modifier = Modifier.padding(16.dp),
                markdown = text,
                isTextSelectable = true,
                linkifyMask = Linkify.WEB_URLS
            )
        }
        Row {
            if (!isLoading) {
                EditTextChip(onEditClick)
                Spacer(modifier = Modifier.width(8.dp))
            }
            CopyTextChip(onCopyClick)
        }
    }
}

@Composable
fun OpponentChatBubble(
    modifier: Modifier = Modifier,
    canRetry: Boolean,
    isLoading: Boolean,
    isError: Boolean = false,
    apiType: ApiType,
    text: String,
    modelName: String? = null,
    availableModels: List<String> = emptyList(),
    markdownBlocks: List<MarkdownBlock>? = null,
    streamingStyle: StreamingStyle = StreamingStyle.TYPEWRITER,
    onCopyClick: () -> Unit = {},
    onCopyPlainTextClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onModelSelected: (String) -> Unit = {}
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
    )

    var isModelMenuExpanded by remember { mutableStateOf(false) }

    // “淡入淡出式”动画：
    // - 闪现：不显示 Pending 块内容，新完成块快速淡入，底部呼吸省略号表示“正在生成”
    // - 淡入淡出：不显示 Pending 块内容，新完成块慢速淡入，淡入时长随响应速度动态调整
    val streamTransition = rememberInfiniteTransition(label = "streamFade")
    val pendingAlpha = streamTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pendingAlpha"
    ).value

    val pendingDots = streamTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "pendingDots"
    ).value

    val loadingText = "Loading" + ".".repeat(pendingDots.toInt().coerceIn(0, 3))

    val appearedBlockIds = remember { mutableStateMapOf<String, Boolean>() }

    var lastCompletedBlockId by remember { mutableStateOf<String?>(null) }
    var lastCompletedAtMs by remember { mutableStateOf(0L) }
    var fadeInDurationMs by remember { mutableStateOf(900) }

    Column(modifier = modifier) {
        if (modelName != null) {
            Box {
                Text(
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 4.dp)
                        .clickable { if (!isLoading) isModelMenuExpanded = true },
                    text = modelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DropdownMenu(
                    expanded = isModelMenuExpanded,
                    onDismissRequest = { isModelMenuExpanded = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onModelSelected(model)
                                isModelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = cardColor
            ) {
                if (isLoading && markdownBlocks != null && markdownBlocks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        val lastIndex = markdownBlocks.lastIndex
                        val hasPending = markdownBlocks.lastOrNull()?.isPending == true

                        val latestCompletedId = markdownBlocks.lastOrNull { !it.isPending }?.id
                        LaunchedEffect(latestCompletedId) {
                            if (streamingStyle != StreamingStyle.FADE_IN_OUT) return@LaunchedEffect
                            if (latestCompletedId == null) return@LaunchedEffect
                            if (latestCompletedId == lastCompletedBlockId) return@LaunchedEffect

                            val now = System.currentTimeMillis()
                            if (lastCompletedAtMs != 0L) {
                                val intervalMs = (now - lastCompletedAtMs).coerceIn(200L, 2500L)
                                val target = (intervalMs * 0.9f).toInt().coerceIn(650, 1800)
                                fadeInDurationMs = (fadeInDurationMs * 0.7f + target * 0.3f).toInt()
                            }
                            lastCompletedAtMs = now
                            lastCompletedBlockId = latestCompletedId
                        }

                        markdownBlocks.forEachIndexed { index, block ->
                            if ((streamingStyle == StreamingStyle.FLASH || streamingStyle == StreamingStyle.FADE_IN_OUT) && block.isPending) {
                                return@forEachIndexed
                            }

                            key(block.id) {
                                val isLastPending = index == lastIndex && block.isPending
                                val markdown = when (streamingStyle) {
                                    StreamingStyle.TYPEWRITER -> block.content
                                    StreamingStyle.FLASH, StreamingStyle.FADE_IN_OUT -> block.content
                                }

                                val alpha = when (streamingStyle) {
                                    StreamingStyle.TYPEWRITER -> 1f
                                    StreamingStyle.FLASH, StreamingStyle.FADE_IN_OUT -> {
                                        val isAppeared = appearedBlockIds[block.id] == true
                                        val target = if (isAppeared) 1f else 0f

                                        LaunchedEffect(block.id) {
                                            appearedBlockIds[block.id] = true
                                        }

                                        val durationMs = when (streamingStyle) {
                                            StreamingStyle.FLASH -> 220
                                            StreamingStyle.FADE_IN_OUT -> fadeInDurationMs
                                            else -> 220
                                        }
                                        val animatedAlpha by animateFloatAsState(
                                            targetValue = target,
                                            animationSpec = tween(durationMillis = durationMs),
                                            label = "blockFadeIn"
                                        )
                                        animatedAlpha
                                    }
                                }

                                MarkdownText(
                                    modifier = Modifier.alpha(alpha),
                                    markdown = markdown,
                                    isTextSelectable = true,
                                    linkifyMask = Linkify.WEB_URLS
                                )
                            }
                        }

                        // 生成中提示：始终固定在底部，避免“提示符跟着文本跑”。
                        if (hasPending) {
                            Text(
                                modifier = Modifier.alpha(pendingAlpha),
                                text = loadingText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    val markdown = when (streamingStyle) {
                        StreamingStyle.TYPEWRITER -> text.trimIndent()
                        StreamingStyle.FLASH, StreamingStyle.FADE_IN_OUT -> text.trimIndent()
                    }
                    val alpha = when (streamingStyle) {
                        StreamingStyle.TYPEWRITER -> 1f
                        StreamingStyle.FLASH, StreamingStyle.FADE_IN_OUT -> if (isLoading) pendingAlpha else 1f
                    }

                    Column(modifier = Modifier.padding(24.dp)) {
                        MarkdownText(
                            modifier = Modifier.alpha(alpha),
                            markdown = markdown,
                            isTextSelectable = true,
                            linkifyMask = Linkify.WEB_URLS
                        )

                        if (isLoading) {
                            Text(
                                modifier = Modifier.alpha(pendingAlpha),
                                text = loadingText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                if (!isLoading) {
                    BrandText(apiType)
                }
            }

            if (!isLoading) {
                Row(modifier = Modifier.align(Alignment.End)) {
                    if (!isError) {
                        CopyPlainTextChip(onCopyPlainTextClick)
                        Spacer(modifier = Modifier.width(8.dp))
                        CopyTextChip(onCopyClick)
                    }
                    if (canRetry) {
                        Spacer(modifier = Modifier.width(8.dp))
                        RetryChip(onRetryClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTextChip(onEditClick: () -> Unit) {
    AssistChip(
        onClick = onEditClick,
        label = { Text(stringResource(R.string.edit)) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun CopyPlainTextChip(onCopyClick: () -> Unit) {
    AssistChip(
        onClick = onCopyClick,
        label = { Text(stringResource(R.string.copy_plain_text)) },
        leadingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
                contentDescription = stringResource(R.string.copy_plain_text),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun CopyTextChip(onCopyClick: () -> Unit) {
    AssistChip(
        onClick = onCopyClick,
        label = { Text(stringResource(R.string.copy_text)) },
        leadingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
                contentDescription = stringResource(R.string.copy_text),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun RetryChip(onRetryClick: () -> Unit) {
    AssistChip(
        onClick = onRetryClick,
        label = { Text(stringResource(R.string.retry)) },
        leadingIcon = {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.retry),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun BrandText(apiType: ApiType) {
    Box(
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            modifier = Modifier.align(Alignment.CenterEnd),
            text = getPlatformAPIBrandText(apiType),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
fun UserChatBubblePreview() {
    val sampleText = """
        How can I print hello world
        in Python?
    """.trimIndent()
    GPTMobileTheme {
        UserChatBubble(text = sampleText, isLoading = false, onCopyClick = {}, onEditClick = {})
    }
}

@Preview
@Composable
fun OpponentChatBubblePreview() {
    val sampleText = """
        # Demo
    
        Emphasis, aka italics, with *asterisks* or _underscores_. Strong emphasis, aka bold, with **asterisks** or __underscores__. Combined emphasis with **asterisks and _underscores_**. [Links with two blocks, text in square-brackets, destination is in parentheses.](https://www.example.com). Inline `code` has `back-ticks around` it.
    
        1. First ordered list item
        2. Another item
            * Unordered sub-list.
        3. And another item.
            You can have properly indented paragraphs within list items. Notice the blank line above, and the leading spaces (at least one, but we'll use three here to also align the raw Markdown).
    
        * Unordered list can use asterisks
        - Or minuses
        + Or pluses
    """.trimIndent()
    GPTMobileTheme {
        OpponentChatBubble(
            text = sampleText,
            canRetry = true,
            isLoading = false,
            apiType = ApiType.OPENAI,
            modelName = "gpt-4o",
            availableModels = listOf("gpt-4o", "gpt-3.5-turbo"),
            onCopyClick = {},
            onRetryClick = {}
        )
    }
}
