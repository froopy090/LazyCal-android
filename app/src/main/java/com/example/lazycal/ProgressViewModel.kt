package com.example.lazycal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ChatDatabase.getDatabase(application)
    private val foodDao = db.foodDao()
    private val userConfigDao = db.userConfigDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val daySummaries: StateFlow<List<DaySummary>> = foodDao.getAllDaySummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userConfig: StateFlow<UserConfig> = userConfigDao.getUserConfig()
        .map { it ?: UserConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserConfig())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentStreak: StateFlow<Int> = combine(daySummaries, userConfig) { summaries, config ->
        var streak = 0
        val summaryMap = summaries.associateBy { it.dayId }
        val calendar = Calendar.getInstance()
        
        // Start from today or yesterday depending on if goal is met
        var checkDate = dateFormat.format(calendar.time)
        val todaySummary = summaryMap[checkDate]
        
        if (todaySummary == null || todaySummary.totalCalories > config.dailyCalorieGoal) {
            // If today doesn't exist or goal isn't met, check starting from yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            checkDate = dateFormat.format(calendar.time)
        }

        while (true) {
            val summary = summaryMap[checkDate]
            if (summary != null && summary.totalCalories <= config.dailyCalorieGoal) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                checkDate = dateFormat.format(calendar.time)
            } else {
                break
            }
        }
        streak
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
