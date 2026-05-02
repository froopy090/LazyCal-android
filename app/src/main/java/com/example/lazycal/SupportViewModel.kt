package com.example.lazycal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupportViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ChatDatabase.getDatabase(application)
    private val userConfigDao = db.userConfigDao()
    private val foodDao = db.foodDao()

    val userConfig: StateFlow<UserConfig> = userConfigDao.getUserConfig()
        .map { it ?: UserConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserConfig())

    val totalEntriesCount: StateFlow<Int> = foodDao.getTotalEntriesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Increment launch count on app start
        viewModelScope.launch(Dispatchers.IO) {
            val current = userConfigDao.getUserConfigSync() ?: UserConfig()
            userConfigDao.saveUserConfig(current.copy(launchCount = current.launchCount + 1))
        }
    }

    fun dismissDonationPrompt() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userConfigDao.getUserConfigSync() ?: UserConfig()
            userConfigDao.saveUserConfig(current.copy(hasDonatedOrDismissed = true))
        }
    }
}
