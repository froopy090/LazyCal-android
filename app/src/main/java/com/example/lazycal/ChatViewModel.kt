package com.example.lazycal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val text: String, val isUser: Boolean)

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
    
    private val _uiState = MutableStateFlow<ChatState>(ChatState.CheckingModel)
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    init {
        checkModel()
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
        val userMsg = ChatMessage(text, true)
        _messages.value = _messages.value + userMsg
        
        // Add a placeholder for the model response to support streaming
        _messages.value = _messages.value + ChatMessage("", false)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fullResponse = ""
                conversation?.sendMessageAsync(text)
                    ?.catch { e ->
                        withContext(Dispatchers.Main) {
                            updateLastMessage("Error: ${e.message}")
                        }
                    }
                    ?.collect { chunk ->
                        fullResponse += chunk.toString()
                        withContext(Dispatchers.Main) {
                            updateLastMessage(fullResponse)
                        }
                    }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateLastMessage("Error: ${e.message}")
                }
            }
        }
    }

    private fun updateLastMessage(text: String) {
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty() && !currentMessages.last().isUser) {
            currentMessages[currentMessages.size - 1] = ChatMessage(text, false)
            _messages.value = currentMessages
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversation?.close()
        engine?.close()
    }
}
