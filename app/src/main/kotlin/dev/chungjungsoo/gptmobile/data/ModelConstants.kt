package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ApiType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    val openaiModels = linkedSetOf<String>()
    val anthropicModels = linkedSetOf<String>()
    val googleModels = linkedSetOf<String>()
    val groqModels = linkedSetOf<String>()
    val ollamaModels = linkedSetOf<String>()

    const val OPENAI_API_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/"
    const val GOOGLE_API_URL = "https://generativelanguage.googleapis.com"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
        ApiType.GOOGLE -> GOOGLE_API_URL
        ApiType.GROQ -> GROQ_API_URL
        ApiType.OLLAMA -> ""
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 4096

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
