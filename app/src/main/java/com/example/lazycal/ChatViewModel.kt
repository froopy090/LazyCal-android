package com.example.lazycal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())

sealed class ChatState {
    object CheckingModel : ChatState()
    object ModelMissing : ChatState()
    object Downloading : ChatState()
    object Initializing : ChatState()
    object Ready : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    private val db = ChatDatabase.getDatabase(application)
    private val messageDao = db.messageDao()
    
    private val _uiState = MutableStateFlow<ChatState>(ChatState.CheckingModel)
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayId = dateFormat.format(Date())

    private val _selectedDay = MutableStateFlow(todayId)
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    val isReadOnly: StateFlow<Boolean> = _selectedDay.map { it != todayId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val archivedDays: StateFlow<List<String>> = messageDao.getAllDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    init {
        checkModel()
        observeMessages()
    }

    private fun observeMessages() {
        _selectedDay.flatMapLatest { dayId ->
            messageDao.getMessagesForDay(dayId)
        }.onEach { entities ->
            _messages.value = entities.map { ChatMessage(it.text, it.isUser, it.timestamp) }
        }.launchIn(viewModelScope)
    }

    fun selectDay(dayId: String) {
        _selectedDay.value = dayId
    }

    private fun checkModel() {
        if (modelManager.isModelDownloaded()) {
            initializeEngine()
        } else {
            _uiState.value = ChatState.ModelMissing
        }
    }

    fun startDownload() {
        _uiState.value = ChatState.Downloading
        modelManager.downloadModel()
    }

    fun onDownloadComplete() {
        initializeEngine()
    }

    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            conversation?.close()
            engine?.close()
            conversation = null
            engine = null
            modelManager.deleteModel()
            messageDao.deleteAll()
            withContext(Dispatchers.Main) {
                _uiState.value = ChatState.ModelMissing
            }
        }
    }

    private fun initializeEngine() {
        _uiState.value = ChatState.Initializing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val engineConfig = EngineConfig(
                    modelPath = modelManager.modelFile.absolutePath,
                    backend = Backend.GPU(),
                )
                
                val engineInstance = Engine(engineConfig)
                engineInstance.initialize()
                engine = engineInstance
                
                conversation = engineInstance.createConversation()
                
                withContext(Dispatchers.Main) {
                    _uiState.value = ChatState.Ready
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = ChatState.Error("Failed to initialize: ${e.message}")
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (_selectedDay.value != todayId) return

        viewModelScope.launch(Dispatchers.IO) {
            // Save user message
            messageDao.insert(MessageEntity(text = text, isUser = true, dayId = todayId))
            
            try {
                var fullResponse = ""
                // We add a temporary message to the local list for streaming UI, 
                // but we only save to DB once finished or in chunks if preferred.
                // To keep it simple, we'll stream in UI and save at the end.
                
                conversation?.sendMessageAsync(text)
                    ?.catch { e ->
                        withContext(Dispatchers.Main) {
                            // In a real app, handle streaming errors better
                        }
                    }
                    ?.collect { chunk ->
                        fullResponse += chunk.toString()
                        withContext(Dispatchers.Main) {
                            // Update UI state for streaming (optional, since we are observing DB,
                            // we might need a separate flow for the "in-progress" response)
                            updateIncompleteResponse(fullResponse)
                        }
                    }
                
                // Save model response once complete
                messageDao.insert(MessageEntity(text = fullResponse, isUser = false, dayId = todayId))
                _incompleteResponse.value = null
            } catch (e: Exception) {
                messageDao.insert(MessageEntity(text = "Error: ${e.message}", isUser = false, dayId = todayId))
            }
        }
    }

    private val _incompleteResponse = MutableStateFlow<String?>(null)
    val incompleteResponse: StateFlow<String?> = _incompleteResponse.asStateFlow()

    private fun updateIncompleteResponse(text: String) {
        _incompleteResponse.value = text
    }

    override fun onCleared() {
        super.onCleared()
        conversation?.close()
        engine?.close()
    }
}
