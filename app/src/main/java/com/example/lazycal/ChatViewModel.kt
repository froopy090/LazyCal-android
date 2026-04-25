package com.example.lazycal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    private val foodDao = db.foodDao()
    private val userConfigDao = db.userConfigDao()
    
    private val _uiState = MutableStateFlow<ChatState>(ChatState.CheckingModel)
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayId = dateFormat.format(Date())

    private val _selectedDay = MutableStateFlow(todayId)
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    val isReadOnly: StateFlow<Boolean> = _selectedDay.map { it != todayId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val archivedDays: StateFlow<List<String>> = foodDao.getAllDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklySummaries: StateFlow<List<DaySummary>> = foodDao.getAllDaySummaries()
        .map { summaries ->
            val summaryMap = summaries.associateBy { it.dayId }
            val calendar = Calendar.getInstance()
            // Set to Saturday of the current week to match the UI "S S M T W T F" (Sat, Sun, Mon, Tue, Wed, Thu, Fri)
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            
            val week = mutableListOf<DaySummary>()
            for (i in 0 until 7) {
                val dateStr = dateFormat.format(calendar.time)
                week.add(summaryMap[dateStr] ?: DaySummary(dateStr, 0, 0, 0, 0))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            week
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val foodEntries: StateFlow<List<FoodEntry>> = _selectedDay.flatMapLatest { dayId ->
        foodDao.getEntriesForDay(dayId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyTotal: StateFlow<Int> = _selectedDay.flatMapLatest { dayId ->
        foodDao.getDailyTotal(dayId)
    }.map { it ?: 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userConfig: StateFlow<UserConfig> = userConfigDao.getUserConfig()
        .map { it ?: UserConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserConfig())

    private val _inputErrorMessage = MutableStateFlow<String?>(null)
    val inputErrorMessage: StateFlow<String?> = _inputErrorMessage.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val systemInstruction = """
        You are a calorie and macro estimation assistant. Convert the user's food description into a JSON object with the following fields: 
        'food_item' (String), 'amount' (String), 'calories' (Integer), 'protein' (Integer grams), 'carbs' (Integer grams), 'fats' (Integer grams). 
        If the user describes multiple items, return a JSON array of such objects. 
        If the input is not food or is nonsensical, return '{"error": "invalid"}'. 
        Return ONLY JSON.
    """.trimIndent()

    init {
        checkModel()
    }

    fun selectDay(dayId: String) {
        _selectedDay.value = dayId
        _inputErrorMessage.value = null
    }

    fun resetToToday() {
        _selectedDay.value = todayId
        _inputErrorMessage.value = null
    }

    fun saveUserConfig(name: String, goal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userConfigDao.saveUserConfig(UserConfig(name = name, dailyCalorieGoal = goal))
        }
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
            cachedConversation = null
            cachedEngine = null
            modelManager.deleteModel()
            foodDao.deleteAll()
            withContext(Dispatchers.Main) {
                _uiState.value = ChatState.ModelMissing
                _inputErrorMessage.value = null
            }
        }
    }

    private fun initializeEngine() {
        if (cachedEngine != null) {
            engine = cachedEngine
            conversation = cachedConversation
            _uiState.value = ChatState.Ready
            return
        }

        if (initializationJob?.isActive == true) {
            _uiState.value = ChatState.Initializing
            return
        }

        _uiState.value = ChatState.Initializing
        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val engineConfig = EngineConfig(
                    modelPath = modelManager.modelFile.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                )
                
                val engineInstance = Engine(engineConfig)
                engineInstance.initialize()
                engine = engineInstance
                
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
                conversation = engineInstance.createConversation(conversationConfig)
                
                cachedEngine = engine
                cachedConversation = conversation
                
                withContext(Dispatchers.Main) {
                    _uiState.value = ChatState.Ready
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Engine init failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = ChatState.Error("Failed to initialize: ${e.message}")
                }
            }
        }
    }

    companion object {
        @Volatile
        private var cachedEngine: Engine? = null
        @Volatile
        private var cachedConversation: Conversation? = null
        private var initializationJob: Job? = null
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun sendImage(imagePath: String) {
        if (_selectedDay.value != todayId || _isProcessing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _inputErrorMessage.value = null
            try {
                var fullResponse = ""
                val flow = conversation?.sendMessageAsync(
                    Contents.of(
                        Content.ImageFile(imagePath),
                        Content.Text("Identify the food in this image and convert it into the JSON format specified in your instructions.")
                    )
                )

                if (flow == null) {
                    Log.e("ChatViewModel", "Conversation is null or flow is null")
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Conversation not initialized. Please restart the app."
                    }
                    return@launch
                }

                flow.catch { e ->
                    Log.e("ChatViewModel", "Inference stream error", e)
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Inference error: ${e.message}"
                    }
                }.collect { chunk ->
                    Log.d("ChatViewModel", "Chunk received: $chunk")
                    fullResponse += chunk.toString()
                }

                Log.d("ChatViewModel", "Full response: $fullResponse")
                if (fullResponse.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Empty response from AI. Try again."
                    }
                } else {
                    parseAndSave(fullResponse, "Image Analysis")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "General error in sendImage", e)
                withContext(Dispatchers.Main) {
                    _inputErrorMessage.value = "Error: ${e.message}"
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (_selectedDay.value != todayId || _isProcessing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _inputErrorMessage.value = null
            try {
                var fullResponse = ""
                val flow = conversation?.sendMessageAsync(text)
                
                if (flow == null) {
                    Log.e("ChatViewModel", "Conversation is null or flow is null")
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Conversation not initialized. Please restart the app."
                    }
                    return@launch
                }

                flow.catch { e ->
                    Log.e("ChatViewModel", "Inference stream error", e)
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Inference error: ${e.message}"
                    }
                }.collect { chunk ->
                    Log.d("ChatViewModel", "Chunk received: $chunk")
                    fullResponse += chunk.toString()
                }
                
                Log.d("ChatViewModel", "Full response: $fullResponse")
                if (fullResponse.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Empty response from AI. Try again."
                    }
                } else {
                    parseAndSave(fullResponse, text)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "General error in sendMessage", e)
                withContext(Dispatchers.Main) {
                    _inputErrorMessage.value = "Error: ${e.message}"
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun parseAndSave(jsonString: String, originalInput: String) {
        try {
            val cleanJson = jsonString.trim().removeSurrounding("```json", "```").trim()
            
            if (cleanJson.startsWith("[")) {
                val array = JSONArray(cleanJson)
                var savedCount = 0
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    if (saveEntry(item, originalInput)) {
                        savedCount++
                    }
                }
                if (savedCount == 0 && array.length() > 0) {
                     showInputError("AI returned invalid food data.")
                }
            } else {
                val json = JSONObject(cleanJson)
                if (!saveEntry(json, originalInput)) {
                    if (json.has("error")) {
                        showInputError("AI couldn't parse that. Try being more specific.")
                    } else {
                        showInputError("Failed to interpret AI response.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Parsing error. Raw string: $jsonString", e)
            showInputError("Failed to parse AI response. Please try again.")
        }
    }

    private suspend fun saveEntry(json: JSONObject, originalInput: String): Boolean {
        if (json.has("error")) return false
        
        return try {
            val entry = FoodEntry(
                foodName = json.getString("food_item"),
                amount = json.getString("amount"),
                calories = json.getInt("calories"),
                protein = json.optInt("protein", 0),
                carbs = json.optInt("carbs", 0),
                fats = json.optInt("fats", 0),
                dayId = todayId,
                originalInput = originalInput
            )
            foodDao.insert(entry)
            true
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Individual item parse error", e)
            false
        }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            foodDao.delete(entry)
        }
    }

    private suspend fun showInputError(message: String) {
        withContext(Dispatchers.Main) {
            _inputErrorMessage.value = message
        }
    }

    fun clearInputError() {
        _inputErrorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // We don't close the engine here because we cache it in the companion object
        // for the process lifetime to avoid heavy re-initialization.
    }
}
