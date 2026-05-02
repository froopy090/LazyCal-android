package com.francescocanossi.lazycal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        
        // If today is logged and we are OVER the goal, streak is broken immediately.
        if (todaySummary != null && todaySummary.totalCalories > config.dailyCalorieGoal) {
            return@combine 0
        }

        // If today is not logged, we check starting from yesterday.
        // If today is logged and met, we check starting from today.
        if (todaySummary == null) {
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
