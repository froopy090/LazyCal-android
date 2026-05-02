package com.francescocanossi.lazycal

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ChatState {
    object CheckingModel : ChatState()
    object ModelMissing : ChatState()
    object Downloading : ChatState()
    object Ready : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    private val db = ChatDatabase.getDatabase(application)
    private val foodDao = db.foodDao()
    private val userConfigDao = db.userConfigDao()
    private val csvManager = CsvManager(foodDao)

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }
    }

    private fun checkInitialOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _uiState = MutableStateFlow<ChatState>(ChatState.CheckingModel)
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayId = dateFormat.format(Date())

    private val _selectedDay = MutableStateFlow(todayId)
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    val isReadOnly: StateFlow<Boolean> = _selectedDay.map { it != todayId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val archivedDays: StateFlow<List<DaySummary>> = foodDao.getAllDaySummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklySummaries: StateFlow<List<DaySummary>> = foodDao.getAllDaySummaries()
        .map { summaries ->
            val summaryMap = summaries.associateBy { it.dayId }
            val calendar = Calendar.getInstance()
            // Reliably find the Sunday of the current week by backing up from today
            while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

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

    private val _detailEntry = MutableStateFlow<FoodEntry?>(null)
    val detailEntry: StateFlow<FoodEntry?> = _detailEntry.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val systemInstruction = """
        You are an expert nutritionist AI. Your task is to convert food descriptions into a JSON array of objects.
        
        Required fields for each food item:
        "food_item": (String) name of the item
        "amount": (String) quantity/size
        "calories": (Int) total calories
        "protein": (Int) grams of protein
        "carbs": (Int) grams of carbs
        "fats": (Int) grams of fats
        
        Rules:
        1. Output ONLY a valid JSON array. Do not include any other text, reasoning, or markdown formatting.
        2. If multiple foods are mentioned, list each as an object in the same array.
        3. If input is not food or is nonsense, return: {"error": "invalid"}
        4. STOP immediately after the final closing bracket ']'. Do not repeat content.
        
        Estimation Method (Internal):
        Identify components -> Estimate mass in grams -> Apply 9/4/4 kcal per gram -> Sum results.
        Only output the final JSON.
    """.trimIndent()

    private var downloadId: Long = -1

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId && id != -1L) {
                onDownloadComplete()
            }
        }
    }

    init {
        checkModel()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            application,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun selectDay(dayId: String) {
        _selectedDay.value = dayId
        _inputErrorMessage.value = null
        _detailEntry.value = null
    }

    fun resetToToday() {
        _selectedDay.value = todayId
        _inputErrorMessage.value = null
        _detailEntry.value = null
    }

    fun showDetail(entry: FoodEntry) {
        _detailEntry.value = entry
    }

    fun dismissDetail() {
        _detailEntry.value = null
    }

    fun saveUserConfig(goal: Int, themeMode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userConfigDao.getUserConfigSync() ?: UserConfig()
            userConfigDao.saveUserConfig(current.copy(dailyCalorieGoal = goal, themeMode = themeMode))
        }
    }

    private fun checkModel() {
        if (modelManager.isModelDownloaded()) {
            _uiState.value = ChatState.Ready
        } else {
            _uiState.value = ChatState.ModelMissing
        }
    }

    fun startDownload() {
        if (_uiState.value == ChatState.Downloading || _uiState.value == ChatState.Ready) return

        if (!_isOnline.value) {
            _inputErrorMessage.value = "No internet connection. Please connect and try again."
            return
        }
        _uiState.value = ChatState.Downloading
        val newId = modelManager.downloadModel()
        if (newId == -1L) {
            // If it returned -1, check if it's because the file is already there
            if (modelManager.isModelDownloaded()) {
                _uiState.value = ChatState.Ready
            } else {
                _uiState.value = ChatState.Error("Failed to start download")
            }
        } else {
            downloadId = newId
        }
    }

    fun retry() {
        if (modelManager.isModelDownloaded()) {
            initializeEngine()
        } else {
            startDownload()
        }
    }

    fun onDownloadComplete() {
        viewModelScope.launch {
            // Give the system a moment to finalize the file
            var retries = 5
            while (retries > 0 && !modelManager.isModelDownloaded()) {
                kotlinx.coroutines.delay(1000)
                retries--
            }
            if (modelManager.isModelDownloaded()) {
                _uiState.value = ChatState.Ready
            } else {
                _uiState.value = ChatState.Error("Model file not found after download.")
            }
        }
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

    private suspend fun ensureEngineInitialized() {
        if (engine != null && conversation != null) return

        initializationJob?.join()
        if (engine != null && conversation != null) return

        initializationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure all initialization work is off-thread
                // Keep the backend on CPU, app simply performs better this way
                // TODO: check if device has an NPU, if yes, use that as the backend because it'll be way more efficient
                val engineInstance = withContext(Dispatchers.IO) {
                    val engineConfig = EngineConfig(
                        modelPath = modelManager.modelFile.absolutePath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                    )
                    Engine(engineConfig).also { it.initialize() }
                }
                
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
                val conversationInstance = engineInstance.createConversation(conversationConfig)
                
                engine = engineInstance
                conversation = conversationInstance
                cachedEngine = engineInstance
                cachedConversation = conversationInstance
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = ChatState.Error("Failed to initialize: ${e.message}")
                }
            }
        }
        initializationJob?.join()
    }

    private fun initializeEngine() {
        // No longer used for immediate initialization
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

    private fun isPromptSafe(text: String): Boolean {
        val suspiciousKeywords = listOf(
            "ignore", "forget", "system instruction", "jailbreak", "bypass", "override",
            "as a developer", "do not follow", "stop being", "you are now"
        )
        val normalizedText = text.lowercase()
        return suspiciousKeywords.none { normalizedText.contains(it) }
    }

    fun sendImage(imagePath: String) {
        if (_selectedDay.value != todayId || _isProcessing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _inputErrorMessage.value = null
            try {
                ensureEngineInitialized()
                if (conversation == null) return@launch

                // Pre-process and resize image to target resolution (720x880) to reduce GPU preprocessing lag
                val processedImagePath = withContext(Dispatchers.Default) {
                    try {
                        val originalFile = File(imagePath)
                        val options = BitmapFactory.Options()
                        val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, options)
                        if (bitmap != null) {
                            // Model target is approx 720x880 as seen in logs
                            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 720, 880, true)
                            val outFile = File(getApplication<Application>().externalCacheDir, "processed_inference.jpg")
                            FileOutputStream(outFile).use { out ->
                                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            if (bitmap != resizedBitmap) bitmap.recycle()
                            resizedBitmap.recycle()
                            outFile.absolutePath
                        } else {
                            imagePath
                        }
                    } catch (_: Exception) {
                        imagePath
                    }
                }

                val fullResponse = StringBuilder()
                val flow = conversation?.sendMessageAsync(
                    Contents.of(
                        Content.ImageFile(processedImagePath),
                        Content.Text("Identify the food in this image and convert it into the JSON format specified in your instructions.")
                    )
                )

                if (flow == null) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Conversation not initialized. Please restart the app."
                    }
                    return@launch
                }

                flow.catch { e ->
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Inference error: ${e.message}"
                    }
                }.collect { chunk ->
                    fullResponse.append(chunk.toString())
                    // Safety circuit breaker for loops
                    if (fullResponse.length > 5000) {
                        throw Exception("Response too long - possible loop detected")
                    }
                }

                val finalResponse = fullResponse.toString()
                if (finalResponse.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Empty response from AI. Try again."
                    }
                } else {
                    parseAndSave(finalResponse, "Image Analysis")
                }
            } catch (e: Exception) {
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

        if (!isPromptSafe(text)) {
            _inputErrorMessage.value = "Suspicious input detected. Please keep it food-related."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _inputErrorMessage.value = null
            try {
                ensureEngineInitialized()
                if (conversation == null) return@launch

                val fullResponse = StringBuilder()
                val flow = conversation?.sendMessageAsync(text)
                
                if (flow == null) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Conversation not initialized. Please restart the app."
                    }
                    return@launch
                }

                flow.catch { e ->
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Inference error: ${e.message}"
                    }
                }.collect { chunk ->
                    fullResponse.append(chunk.toString())
                    // Safety circuit breaker for loops
                    if (fullResponse.length > 5000) {
                        throw Exception("Response too long - possible loop detected")
                    }
                }
                
                val finalResponse = fullResponse.toString()
                if (finalResponse.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _inputErrorMessage.value = "Empty response from AI. Try again."
                    }
                } else {
                    parseAndSave(finalResponse, text)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _inputErrorMessage.value = "Error: ${e.message}"
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun parseAndSave(jsonString: String, originalInput: String) = withContext(Dispatchers.Default) {
        try {
            // Find the bounds of the JSON content to ignore noise or markdown
            val startIndex = jsonString.indexOfFirst { it == '[' || it == '{' }
            val endIndex = jsonString.indexOfLast { it == ']' || it == '}' }

            val cleanJson = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                jsonString.substring(startIndex, endIndex + 1)
            } else {
                jsonString.trim().removeSurrounding("```json", "```").trim()
            }
            
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
        } catch (_: Exception) {
            showInputError("Failed to parse AI response. Please try again.")
        }
    }

    private suspend fun saveEntry(json: JSONObject, originalInput: String): Boolean = withContext(Dispatchers.IO) {
        if (json.has("error")) return@withContext false
        
        return@withContext try {
            val calories = json.getInt("calories")
            val protein = json.optInt("protein", 0)
            val carbs = json.optInt("carbs", 0)
            val fats = json.optInt("fats", 0)

            // Guardrails: No negative values and sane limits
            if (calories < 0 || protein < 0 || carbs < 0 || fats < 0) {
                // Log.w("ChatViewModel", "Refusing to save entry with negative values: $json")
                return@withContext false
            }
            if (calories > 10000) {
                // Log.w("ChatViewModel", "Refusing to save entry with excessive calories: $calories")
                return@withContext false
            }

            val entry = FoodEntry(
                foodName = json.getString("food_item"),
                amount = json.getString("amount"),
                calories = calories,
                protein = protein,
                carbs = carbs,
                fats = fats,
                dayId = todayId,
                originalInput = originalInput
            )
            foodDao.insert(entry)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            foodDao.delete(entry)
            withContext(Dispatchers.Main) {
                if (_detailEntry.value?.id == entry.id) {
                    _detailEntry.value = null
                }
            }
        }
    }

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            foodDao.update(entry)
            withContext(Dispatchers.Main) {
                if (_detailEntry.value?.id == entry.id) {
                    _detailEntry.value = entry
                }
            }
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

    suspend fun getExportCSV(): String = csvManager.getExportCSV()

    suspend fun importFromCSV(csvData: String): Boolean = csvManager.importFromCSV(csvData)

    fun resetEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                conversation?.close()
                engine?.close()
                conversation = null
                engine = null
                cachedConversation = null
                cachedEngine = null
                initializationJob?.cancel()
                initializationJob = null
                
                withContext(Dispatchers.Main) {
                    _inputErrorMessage.value = "AI Engine Reset successfully."
                }
                ensureEngineInitialized()
            } catch (_: Exception) {
                // Ignore reset failures
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback might not be registered
        }
        try {
            getApplication<Application>().unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        // We don't close the engine here because we cache it in the companion object
        // for the process lifetime to avoid heavy re-initialization.
    }
}
