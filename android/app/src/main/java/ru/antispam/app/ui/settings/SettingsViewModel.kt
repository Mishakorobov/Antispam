package ru.antispam.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.worker.SyncPoolsWorker

data class SettingsUiState(
    val blockSurveys: Boolean = true,
    val blockFinancial: Boolean = true,
    val blockSilentCalls: Boolean = true,
    val postCallEnabled: Boolean = true,
    val postCallThresholdSeconds: Int = 30, // < 30 seconds
    val pendingReportsCount: Int = 0,
    val currentDatabaseVersion: Long = 0L,
    val isSyncing: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences("antispam_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val pendingCount = db.pendingReportDao().getCount()
            val ver = db.syncMetadataDao().getValueLong("last_delta_version") ?: 0L

            _uiState.update {
                it.copy(
                    blockSurveys = prefs.getBoolean("block_surveys", true),
                    blockFinancial = prefs.getBoolean("block_financial", true),
                    blockSilentCalls = prefs.getBoolean("block_silent", true),
                    postCallEnabled = prefs.getBoolean("post_call_enabled", true),
                    postCallThresholdSeconds = prefs.getInt("post_call_threshold", 30),
                    pendingReportsCount = pendingCount,
                    currentDatabaseVersion = ver,
                    isSyncing = false
                )
            }
        }
    }

    fun setSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        loadSettings()
    }

    fun triggerSyncNow() {
        _uiState.update { it.copy(isSyncing = true) }
        val workRequest = OneTimeWorkRequestBuilder<SyncPoolsWorker>().build()
        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        // Refresh after short delay
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            loadSettings()
        }
    }
}
