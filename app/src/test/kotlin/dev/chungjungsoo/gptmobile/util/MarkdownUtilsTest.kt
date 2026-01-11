package dev.chungjungsoo.gptmobile.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUtilsTest {
    @Test
    fun stripMarkdown_shouldNotCrash_andShouldRemoveImagesAndLinks() {
        val input = """
            这是图片：![alt](https://example.com/a.png)
            这是链接：[点我](https://example.com)
            行内代码：`code`
            - 列表项1
            + 列表项2
        """.trimIndent()

        val output = MarkdownUtils.stripMarkdown(input)

        // 图片被移除，链接保留文本，行内代码去掉反引号
        assertEquals(
            """
                这是图片：
                这是链接：点我
                行内代码：code
                列表项1
                列表项2
            """.trimIndent(),
            output
        )
    }
}
