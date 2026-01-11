package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.StreamingStyle
import dev.chungjungsoo.gptmobile.data.repository.AiMaskRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.util.IncrementalMarkdownParser
import dev.chungjungsoo.gptmobile.util.MarkdownBlock
import dev.chungjungsoo.gptmobile.util.handleStates
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val aiMaskRepository: AiMaskRepository
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    private val maskId: Int = savedStateHandle["maskId"] ?: -1
    val enabledPlatformsInChat = enabledPlatformString.split(',').map { s -> ApiType.valueOf(s) }
    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

    private val _chatRoom = MutableStateFlow<ChatRoom>(ChatRoom(id = -1, title = "", enabledPlatform = enabledPlatformsInChat))
    val chatRoom = _chatRoom.asStateFlow()

    private val _isChatTitleDialogOpen = MutableStateFlow(false)
    val isChatTitleDialogOpen = _isChatTitleDialogOpen.asStateFlow()

    private val _isEditQuestionDialogOpen = MutableStateFlow(false)
    val isEditQuestionDialogOpen = _isEditQuestionDialogOpen.asStateFlow()

    // Enabled platforms list
    private val _enabledPlatformsInApp = MutableStateFlow(listOf<ApiType>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    private val _streamingStyle = MutableStateFlow(StreamingStyle.TYPEWRITER)
    val streamingStyle = _streamingStyle.asStateFlow()

    // List of question & answers (User, Assistant)
    private val _messages = MutableStateFlow(listOf<Message>())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // User input used for TextField
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    // Used for passing user question to Edit User Message Dialog
    private val _editedQuestion = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val editedQuestion = _editedQuestion.asStateFlow()

    // Loading state for each platforms
    private val _openaiLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val openaiLoadingState = _openaiLoadingState.asStateFlow()

    private val _anthropicLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val anthropicLoadingState = _anthropicLoadingState.asStateFlow()

    private val _googleLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val googleLoadingState = _googleLoadingState.asStateFlow()

    private val _groqLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val groqLoadingState = _groqLoadingState.asStateFlow()

    private val _ollamaLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val ollamaLoadingState = _ollamaLoadingState.asStateFlow()

    private val _geminiNanoLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val geminiNanoLoadingState = _geminiNanoLoadingState.asStateFlow()

    // Total loading state. It should be updated if one of the loading state has changed.
    // If all loading states are idle, this value should have `true`.
    private val _isIdle = MutableStateFlow(true)
    val isIdle = _isIdle.asStateFlow()

    // State for the message loading state (From the database)
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    // Currently active(chat completion) user input. This is used when user input is sent.
    private val _userMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val userMessage = _userMessage.asStateFlow()

    // Currently active(chat completion) assistant output. This is used when data is received from the API.
    private val _openAIMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OPENAI))
    val openAIMessage = _openAIMessage.asStateFlow()

    private val _anthropicMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.ANTHROPIC))
    val anthropicMessage = _anthropicMessage.asStateFlow()

    private val _googleMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GOOGLE))
    val googleMessage = _googleMessage.asStateFlow()

    private val _groqMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GROQ))
    val groqMessage = _groqMessage.asStateFlow()

    private val _ollamaMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OLLAMA))
    val ollamaMessage = _ollamaMessage.asStateFlow()

    private val _geminiNanoMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val geminiNanoMessage = _geminiNanoMessage.asStateFlow()

    // Flows for assistant message streams
    private val openAIFlow = MutableSharedFlow<ApiState>()
    private val anthropicFlow = MutableSharedFlow<ApiState>()
    private val googleFlow = MutableSharedFlow<ApiState>()
    private val groqFlow = MutableSharedFlow<ApiState>()
    private val ollamaFlow = MutableSharedFlow<ApiState>()
    private val geminiNanoFlow = MutableSharedFlow<ApiState>()

    // --- 流式渲染优化：增量分块（incremark 模式） ---
    private class StreamingMarkdownAccumulator {
        private val builder = StringBuilder()
        private val parser = IncrementalMarkdownParser()

        fun reset() {
            builder.clear()
            parser.reset()
        }

        fun append(chunk: String): List<MarkdownBlock> {
            builder.append(chunk)
            return parser.append(chunk).allBlocks
        }

        fun buildFinalText(): String = builder.toString()
    }

    private val openAIAccumulator = StreamingMarkdownAccumulator()
    private val anthropicAccumulator = StreamingMarkdownAccumulator()
    private val googleAccumulator = StreamingMarkdownAccumulator()
    private val groqAccumulator = StreamingMarkdownAccumulator()
    private val ollamaAccumulator = StreamingMarkdownAccumulator()
    private val geminiNanoAccumulator = StreamingMarkdownAccumulator()

    private val _openAIMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val openAIMarkdownBlocks = _openAIMarkdownBlocks.asStateFlow()

    private val _anthropicMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val anthropicMarkdownBlocks = _anthropicMarkdownBlocks.asStateFlow()

    private val _googleMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val googleMarkdownBlocks = _googleMarkdownBlocks.asStateFlow()

    private val _groqMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val groqMarkdownBlocks = _groqMarkdownBlocks.asStateFlow()

    private val _ollamaMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val ollamaMarkdownBlocks = _ollamaMarkdownBlocks.asStateFlow()

    private val _geminiNanoMarkdownBlocks = MutableStateFlow<List<MarkdownBlock>>(emptyList())
    val geminiNanoMarkdownBlocks = _geminiNanoMarkdownBlocks.asStateFlow()

    private fun resetStreamState(apiType: ApiType) {
        when (apiType) {
            ApiType.OPENAI -> {
                openAIAccumulator.reset()
                _openAIMarkdownBlocks.update { emptyList() }
                _openAIMessage.update { it.copy(content = "") }
            }

            ApiType.ANTHROPIC -> {
                anthropicAccumulator.reset()
                _anthropicMarkdownBlocks.update { emptyList() }
                _anthropicMessage.update { it.copy(content = "") }
            }

            ApiType.GOOGLE -> {
                googleAccumulator.reset()
                _googleMarkdownBlocks.update { emptyList() }
                _googleMessage.update { it.copy(content = "") }
            }

            ApiType.GROQ -> {
                groqAccumulator.reset()
                _groqMarkdownBlocks.update { emptyList() }
                _groqMessage.update { it.copy(content = "") }
            }

            ApiType.OLLAMA -> {
                ollamaAccumulator.reset()
                _ollamaMarkdownBlocks.update { emptyList() }
                _ollamaMessage.update { it.copy(content = "") }
            }
        }
    }

    init {
        Log.d("ViewModel", "$chatRoomId")
        Log.d("ViewModel", "$enabledPlatformsInChat")
        fetchChatRoom()
        viewModelScope.launch { fetchMessages() }
        fetchEnabledPlatformsInApp()
        fetchStreamingStyle()
        observeFlow()
    }

    fun askQuestion() {
        Log.d("Question: ", _question.value)
        _userMessage.update { it.copy(content = _question.value, createdAt = currentTimeStamp) }
        _question.update { "" }
        completeChat()
    }

    fun closeChatTitleDialog() = _isChatTitleDialogOpen.update { false }

    fun closeEditQuestionDialog() {
        _editedQuestion.update { Message(chatId = chatRoomId, content = "", platformType = null) }
        _isEditQuestionDialogOpen.update { false }
    }

    fun editQuestion(q: Message) {
        _messages.update { it.filter { message -> message.id < q.id && message.createdAt < q.createdAt } }
        _userMessage.update { it.copy(content = q.content, createdAt = currentTimeStamp) }
        completeChat()
    }

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }

    fun openEditQuestionDialog(question: Message) {
        _editedQuestion.update { question }
        _isEditQuestionDialogOpen.update { true }
    }

    fun generateDefaultChatTitle(): String? = chatRepository.generateDefaultChatTitle(_messages.value)

    fun generateAIChatTitle() {
        viewModelScope.launch {
            _geminiNanoLoadingState.update { LoadingState.Loading }
            _geminiNanoMessage.update { it.copy(content = "") }
        }
    }

    fun retryQuestion(message: Message) {
        val latestQuestionIndex = _messages.value.indexOfLast { it.platformType == null }

        if (latestQuestionIndex != -1 && _isIdle.value) {
            // Update user input to latest question
            _userMessage.update { _messages.value[latestQuestionIndex] }

            // Get previous answers from the assistant
            val previousAnswers = enabledPlatformsInChat.mapNotNull { apiType -> _messages.value.lastOrNull { it.platformType == apiType } }

            // Remove latest question & answers
            _messages.update { it - setOf(_messages.value[latestQuestionIndex]) - previousAnswers.toSet() }

            // Restore messages that are not retrying
            enabledPlatformsInChat.forEach { apiType ->
                when (apiType) {
                    message.platformType -> {}
                    else -> restoreMessageState(apiType, previousAnswers)
                }
            }
        }
        message.platformType?.let { updateLoadingState(it, LoadingState.Loading) }

        message.platformType?.let { resetStreamState(it) }

        when (message.platformType) {
            ApiType.OPENAI -> {
                _openAIMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeOpenAIChat()
            }

            ApiType.ANTHROPIC -> {
                _anthropicMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeAnthropicChat()
            }

            ApiType.GOOGLE -> {
                _googleMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeGoogleChat()
            }

            ApiType.GROQ -> {
                _groqMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeGroqChat()
            }

            ApiType.OLLAMA -> {
                _ollamaMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeOllamaChat()
            }

            else -> {}
        }
    }

    fun updateChatTitle(title: String) {
        // Should be only used for changing chat title after the chatroom is created.
        if (_chatRoom.value.id > 0) {
            _chatRoom.update { it.copy(title = title) }
            viewModelScope.launch {
                chatRepository.updateChatTitle(_chatRoom.value, title)
            }
        }
    }

    fun exportChat(): Pair<String, String> {
        // Build the chat history in Markdown format
        val chatHistoryMarkdown = buildString {
            appendLine("# Chat Export: \"${chatRoom.value.title}\"")
            appendLine()
            appendLine("**Exported on:** ${formatCurrentDateTime()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Chat History")
            appendLine()
            messages.value.forEach { message ->
                val sender = if (message.platformType == null) "User" else "Assistant"
                appendLine("**$sender:**")
                appendLine(message.content)
                appendLine()
            }
        }

        // Save the Markdown file
        val fileName = "export_${chatRoom.value.title}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun formatCurrentDateTime(): String {
        val currentDate = java.util.Date()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault())
        return format.format(currentDate)
    }

    fun updateQuestion(q: String) = _question.update { q }

    private fun addMessage(message: Message) = _messages.update { it + listOf(message) }

    private fun clearQuestionAndAnswers() {
        _userMessage.update { it.copy(id = 0, content = "") }
        _openAIMessage.update { it.copy(id = 0, content = "") }
        _anthropicMessage.update { it.copy(id = 0, content = "") }
        _googleMessage.update { it.copy(id = 0, content = "") }
        _groqMessage.update { it.copy(id = 0, content = "") }
        _ollamaMessage.update { it.copy(id = 0, content = "") }

        // 清理增量渲染状态
        openAIAccumulator.reset()
        anthropicAccumulator.reset()
        googleAccumulator.reset()
        groqAccumulator.reset()
        ollamaAccumulator.reset()
        geminiNanoAccumulator.reset()
        _openAIMarkdownBlocks.update { emptyList() }
        _anthropicMarkdownBlocks.update { emptyList() }
        _googleMarkdownBlocks.update { emptyList() }
        _groqMarkdownBlocks.update { emptyList() }
        _ollamaMarkdownBlocks.update { emptyList() }
        _geminiNanoMarkdownBlocks.update { emptyList() }
    }

    private fun completeChat() {
        enabledPlatformsInChat.forEach { apiType -> updateLoadingState(apiType, LoadingState.Loading) }
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            resetStreamState(ApiType.OPENAI)
            completeOpenAIChat()
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            resetStreamState(ApiType.ANTHROPIC)
            completeAnthropicChat()
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            resetStreamState(ApiType.GOOGLE)
            completeGoogleChat()
        }

        if (ApiType.GROQ in enabledPlatforms) {
            resetStreamState(ApiType.GROQ)
            completeGroqChat()
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            resetStreamState(ApiType.OLLAMA)
            completeOllamaChat()
        }
    }

    private fun completeAnthropicChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeAnthropicChat(
                question = _userMessage.value,
                history = _messages.value,
                systemPrompt = _chatRoom.value.systemPrompt
            )
            chatFlow.collect { chunk -> anthropicFlow.emit(chunk) }
        }
    }

    private fun completeGoogleChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGoogleChat(
                question = _userMessage.value,
                history = _messages.value,
                systemPrompt = _chatRoom.value.systemPrompt
            )
            chatFlow.collect { chunk -> googleFlow.emit(chunk) }
        }
    }

    private fun completeGroqChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGroqChat(
                question = _userMessage.value,
                history = _messages.value,
                systemPrompt = _chatRoom.value.systemPrompt
            )
            chatFlow.collect { chunk -> groqFlow.emit(chunk) }
        }
    }

    private fun completeOllamaChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOllamaChat(
                question = _userMessage.value,
                history = _messages.value,
                systemPrompt = _chatRoom.value.systemPrompt
            )
            chatFlow.collect { chunk -> ollamaFlow.emit(chunk) }
        }
    }

    private fun completeOpenAIChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOpenAIChat(
                question = _userMessage.value,
                history = _messages.value,
                systemPrompt = _chatRoom.value.systemPrompt
            )
            chatFlow.collect { chunk -> openAIFlow.emit(chunk) }
        }
    }

    private suspend fun fetchMessages() {
        // If the room isn't new
        if (chatRoomId != 0) {
            _messages.update { chatRepository.fetchMessages(chatRoomId) }
            _isLoaded.update { true } // Finish fetching
            return
        }

        // When message id should sync after saving chats
        if (_chatRoom.value.id != 0) {
            _messages.update { chatRepository.fetchMessages(_chatRoom.value.id) }
            return
        }
    }

    private fun fetchChatRoom() {
        viewModelScope.launch {
            if (chatRoomId == 0) {
                var room = ChatRoom(id = 0, title = "Untitled Chat", enabledPlatform = enabledPlatformsInChat)
                if (maskId > 0) {
                    val mask = aiMaskRepository.fetchById(maskId)
                    if (mask != null) {
                        room = room.copy(
                            maskId = mask.id,
                            maskName = mask.name,
                            systemPrompt = mask.systemPrompt
                        )
                        aiMaskRepository.touch(mask.id)
                    }
                }
                _chatRoom.update { room }
            } else {
                _chatRoom.update { chatRepository.fetchChatList().first { it.id == chatRoomId } }
            }
            Log.d("ViewModel", "chatroom: $chatRoom")
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val enabled = settingRepository.fetchPlatforms().filter { it.enabled }.map { it.name }
            _enabledPlatformsInApp.update { enabled }
        }
    }

    private fun fetchStreamingStyle() {
        viewModelScope.launch {
            val style = settingRepository.fetchStreamingStyle()
            _streamingStyle.update { style }
        }
    }

    private fun observeFlow() {
        viewModelScope.launch {
            openAIFlow.handleStates(
                messageFlow = _openAIMessage,
                onLoadingComplete = { updateLoadingState(ApiType.OPENAI, LoadingState.Idle) },
                onTextChunk = { chunk -> _openAIMarkdownBlocks.update { openAIAccumulator.append(chunk) } },
                onDone = {
                    _openAIMessage.update { it.copy(content = openAIAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _openAIMarkdownBlocks.update { emptyList() }
                    openAIAccumulator.reset()
                    _openAIMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            anthropicFlow.handleStates(
                messageFlow = _anthropicMessage,
                onLoadingComplete = { updateLoadingState(ApiType.ANTHROPIC, LoadingState.Idle) },
                onTextChunk = { chunk -> _anthropicMarkdownBlocks.update { anthropicAccumulator.append(chunk) } },
                onDone = {
                    _anthropicMessage.update { it.copy(content = anthropicAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _anthropicMarkdownBlocks.update { emptyList() }
                    anthropicAccumulator.reset()
                    _anthropicMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            googleFlow.handleStates(
                messageFlow = _googleMessage,
                onLoadingComplete = { updateLoadingState(ApiType.GOOGLE, LoadingState.Idle) },
                onTextChunk = { chunk -> _googleMarkdownBlocks.update { googleAccumulator.append(chunk) } },
                onDone = {
                    _googleMessage.update { it.copy(content = googleAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _googleMarkdownBlocks.update { emptyList() }
                    googleAccumulator.reset()
                    _googleMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            groqFlow.handleStates(
                messageFlow = _groqMessage,
                onLoadingComplete = { updateLoadingState(ApiType.GROQ, LoadingState.Idle) },
                onTextChunk = { chunk -> _groqMarkdownBlocks.update { groqAccumulator.append(chunk) } },
                onDone = {
                    _groqMessage.update { it.copy(content = groqAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _groqMarkdownBlocks.update { emptyList() }
                    groqAccumulator.reset()
                    _groqMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            ollamaFlow.handleStates(
                messageFlow = _ollamaMessage,
                onLoadingComplete = { updateLoadingState(ApiType.OLLAMA, LoadingState.Idle) },
                onTextChunk = { chunk -> _ollamaMarkdownBlocks.update { ollamaAccumulator.append(chunk) } },
                onDone = {
                    _ollamaMessage.update { it.copy(content = ollamaAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _ollamaMarkdownBlocks.update { emptyList() }
                    ollamaAccumulator.reset()
                    _ollamaMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            geminiNanoFlow.handleStates(
                messageFlow = _geminiNanoMessage,
                onLoadingComplete = { _geminiNanoLoadingState.update { LoadingState.Idle } },
                onTextChunk = { chunk -> _geminiNanoMarkdownBlocks.update { geminiNanoAccumulator.append(chunk) } },
                onDone = {
                    _geminiNanoMessage.update { it.copy(content = geminiNanoAccumulator.buildFinalText(), createdAt = currentTimeStamp) }
                },
                onError = { error ->
                    _geminiNanoMessage.update { it.copy(content = "Error: $error", createdAt = currentTimeStamp) }
                }
            )
        }

        viewModelScope.launch {
            _isIdle.collect { status ->
                if (status) {
                    Log.d("status", "val: ${_userMessage.value}")
                    if (_chatRoom.value.id != -1 && _userMessage.value.content.isNotBlank()) {
                        syncQuestionAndAnswers()
                        Log.d("message", "${_messages.value}")
                        _chatRoom.update { chatRepository.saveChat(_chatRoom.value, _messages.value) }
                        fetchMessages() // For syncing message ids
                    }
                    clearQuestionAndAnswers()
                }
            }
        }
    }

    private fun restoreMessageState(apiType: ApiType, previousAnswers: List<Message>) {
        val message = previousAnswers.firstOrNull { it.platformType == apiType }
        val retryingState = when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState
            ApiType.ANTHROPIC -> _anthropicLoadingState
            ApiType.GOOGLE -> _googleLoadingState
            ApiType.GROQ -> _groqLoadingState
            ApiType.OLLAMA -> _ollamaLoadingState
        }

        if (retryingState.value == LoadingState.Loading) return
        if (message == null) return

        when (apiType) {
            ApiType.OPENAI -> _openAIMessage.update { message }
            ApiType.ANTHROPIC -> _anthropicMessage.update { message }
            ApiType.GOOGLE -> _googleMessage.update { message }
            ApiType.GROQ -> _groqMessage.update { message }
            ApiType.OLLAMA -> _ollamaMessage.update { message }
        }
    }

    private fun syncQuestionAndAnswers() {
        addMessage(_userMessage.value)
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            addMessage(_openAIMessage.value)
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            addMessage(_anthropicMessage.value)
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            addMessage(_googleMessage.value)
        }

        if (ApiType.GROQ in enabledPlatforms) {
            addMessage(_groqMessage.value)
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            addMessage(_ollamaMessage.value)
        }
    }

    private fun updateLoadingState(apiType: ApiType, loadingState: LoadingState) {
        when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState.update { loadingState }
            ApiType.ANTHROPIC -> _anthropicLoadingState.update { loadingState }
            ApiType.GOOGLE -> _googleLoadingState.update { loadingState }
            ApiType.GROQ -> _groqLoadingState.update { loadingState }
            ApiType.OLLAMA -> _ollamaLoadingState.update { loadingState }
        }

        var result = true
        enabledPlatformsInChat.forEach {
            val state = when (it) {
                ApiType.OPENAI -> _openaiLoadingState
                ApiType.ANTHROPIC -> _anthropicLoadingState
                ApiType.GOOGLE -> _googleLoadingState
                ApiType.GROQ -> _groqLoadingState
                ApiType.OLLAMA -> _ollamaLoadingState
            }

            result = result && (state.value is LoadingState.Idle)
        }

        _isIdle.update { result }
    }
}
