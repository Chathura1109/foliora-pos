package com.foliora.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.repository.SettingRepository
import com.foliora.pos.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state representation for the global application context in Foliora POS.
 *
 * @property isUserLoggedIn Indicates if a user session is active.
 * @property shopName Global shop name retrieved from settings.
 * @property isLoading Indicates if core settings are currently being loaded.
 */
data class MainUiState(
    val isUserLoggedIn: Boolean = false,
    val shopName: String = "Foliora",
    val isLoading: Boolean = true
)

/**
 * Hilt-injected ViewModel for handling root application state and initialization.
 *
 * @property userRepository Repository for user authentication and user profile operations.
 * @property settingRepository Repository for POS application settings and shop information.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Loads shop settings and updates the global [MainUiState].
     */
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val settings = settingRepository.initializeSettings()
            _uiState.update {
                it.copy(
                    shopName = settings.shopName,
                    isLoading = false
                )
            }
        }
    }
}
