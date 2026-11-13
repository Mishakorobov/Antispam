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

            // 2. Load server groups from API (with fallback if offline)
            val groups = try {
                api.getWhitelistGroups()
            } catch (_: Exception) {
                // Fallback default list if backend is currently unreachable
                getDefaultFallbackGroups()
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
            // Optimistic UI update
            _uiState.update { current ->
                val updated = current.serverGroups.map { g ->
                    if (g.groupKey.equals(groupKey, ignoreCase = true)) g.copy(isEnabled = enable) else g
                }
                current.copy(serverGroups = updated)
            }

            try {
                api.toggleWhitelistGroup(groupKey, WhitelistToggleRequestDto(enable = enable))
            } catch (e: Exception) {
                // Revert on failure
                loadData()
            }
        }
    }

    fun addLocalNumber(rawNumber: String, label: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = PhoneNormalizer.normalize(rawNumber) ?: return@launch
            db.localWhitelistDao().add(
                LocalWhitelistEntity(
                    phoneNumber = normalized,
                    label = label ?: "Доверенный номер",
                    addedAt = System.currentTimeMillis()
                )
            )
            loadData()
        }
    }

    fun removeLocalNumber(phoneNumber: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.localWhitelistDao().remove(phoneNumber)
            loadData()
        }
    }

    private fun getDefaultFallbackGroups(): List<WhitelistGroupDto> {
        return listOf(
            WhitelistGroupDto(
                groupKey = "EMERGENCY",
                category = "EMERGENCY",
                isEnabled = true,
                rangeCount = 2,
                description = "Единая служба спасения 112, 101, 102, 103, 104",
                ranges = emptyList()
            ),
            WhitelistGroupDto(
                groupKey = "GOV",
                category = "GOV",
                isEnabled = true,
                rangeCount = 3,
                description = "Госуслуги (0919, 8-800-100-70-10), Социальный фонд",
                ranges = emptyList()
            ),
            WhitelistGroupDto(
                groupKey = "SBERBANK",
                category = "BANK",
                isEnabled = true,
                rangeCount = 3,
                description = "Сбербанк (900, 8-800-555-55-50)",
                ranges = emptyList()
            ),
            WhitelistGroupDto(
                groupKey = "TBANK",
                category = "BANK",
                isEnabled = true,
                rangeCount = 4,
                description = "Т-Банк (Линии 8-800, Московский офис)",
                ranges = emptyList()
            ),
            WhitelistGroupDto(
                groupKey = "VTB",
                category = "BANK",
                isEnabled = true,
                rangeCount = 2,
                description = "ВТБ (1000, 8-800-100-24-24)",
                ranges = emptyList()
            ),
            WhitelistGroupDto(
                groupKey = "ALFABANK",
                category = "BANK",
                isEnabled = true,
                rangeCount = 2,
                description = "Альфа-Банк (8-800-200-00-00)",
                ranges = emptyList()
            )
        )
    }
}
