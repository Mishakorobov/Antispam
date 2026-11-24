package ru.antispam.app.ui.whitelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.LocalWhitelistEntity
import ru.antispam.app.data.remote.NetworkModule
import ru.antispam.app.data.remote.dto.WhitelistGroupDto
import ru.antispam.app.data.remote.dto.WhitelistToggleRequestDto
import ru.antispam.app.util.OfficialWhitelistRegistry
import ru.antispam.app.util.PhoneNormalizer

data class WhitelistUiState(
    val selectedTab: Int = 0, // 0 = Server Groups, 1 = Local Whitelist
    val serverGroups: List<WhitelistGroupDto> = emptyList(),
    val localEntries: List<LocalWhitelistEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class WhitelistViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val api = NetworkModule.apiService

    private val _uiState = MutableStateFlow(WhitelistUiState())
    val uiState: StateFlow<WhitelistUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Load local whitelist
            val locals = db.localWhitelistDao().getAll()

            // 2. Load official bank/gov/emergency groups (serverless local prefs, or REST API)
            val app = getApplication<Application>()
            val groups = if (NetworkModule.isServerlessMode) {
                OfficialWhitelistRegistry.getGroupsWithLocalPrefs(app)
            } else {
                try {
                    api.getWhitelistGroups()
                } catch (_: Exception) {
                    OfficialWhitelistRegistry.getGroupsWithLocalPrefs(app)
                }
            }

            _uiState.update {
                it.copy(
                    serverGroups = groups,
                    localEntries = locals,
                    isLoading = false
                )
            }
        }
    }

    fun toggleServerGroup(groupKey: String, enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            OfficialWhitelistRegistry.setGroupEnabled(app, groupKey, enable)

            // Optimistic UI update
            _uiState.update { current ->
                val updated = current.serverGroups.map { g ->
                    if (g.groupKey.equals(groupKey, ignoreCase = true)) g.copy(isEnabled = enable) else g
                }
                current.copy(serverGroups = updated)
            }

            if (!NetworkModule.isServerlessMode) {
                try {
                    api.toggleWhitelistGroup(groupKey, WhitelistToggleRequestDto(enable = enable))
                } catch (_: Exception) {
                    // Keep local toggle active; server sync can catch up later
                }
            }
        }
    }

    fun addLocalNumber(rawNumber: String, label: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = PhoneNormalizer.normalize(rawNumber)
                ?: PhoneNormalizer.normalizeShortCode(rawNumber)
                ?: run {
                    _uiState.update {
                        it.copy(errorMessage = "Введите корректный номер телефона (например, +79991234567 или 900)")
                    }
                    return@launch
                }
            db.localWhitelistDao().add(
                LocalWhitelistEntity(
                    phoneNumber = normalized,
                    label = label?.takeIf { it.isNotBlank() } ?: "Доверенный номер",
                    addedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(errorMessage = null) }
            loadData()
        }
    }

    fun removeLocalNumber(phoneNumber: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.localWhitelistDao().remove(phoneNumber)
            loadData()
        }
    }
}
