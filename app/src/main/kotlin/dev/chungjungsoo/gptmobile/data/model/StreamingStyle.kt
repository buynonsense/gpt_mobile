package dev.chungjungsoo.gptmobile.data.model

/**
 * AI 流式响应的输出样式
 */
enum class StreamingStyle(val value: Int) {
    /** 打字机式：显示 ▊ 光标，传统逐字生成效果 */
    TYPEWRITER(0),

    /** 淡入淡出式：Pending 块呼吸透明度变化，内容增长但视觉上是隐现 */
    FADE_IN_OUT(1);

    companion object {
        fun getByValue(value: Int): StreamingStyle? = entries.firstOrNull { it.value == value }
    }
}
