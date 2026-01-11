package dev.chungjungsoo.gptmobile.data.model

/**
 * AI 流式响应的输出样式
 */
enum class StreamingStyle(val value: Int) {
    /** 打字机式：显示 ▊ 光标，传统逐字生成效果 */
    TYPEWRITER(0),

    /** 闪现：按块快速淡入；生成中用省略号提示，不展示 Pending 块内容 */
    FLASH(1),

    /** 淡入淡出：按块慢速淡入，可根据响应速度动态调整淡入时长 */
    FADE_IN_OUT(2);

    companion object {
        fun getByValue(value: Int): StreamingStyle? = entries.firstOrNull { it.value == value }
    }
}
