package app.deutschlern.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class OnboardingExit { TRANSLATE, SETTINGS }

data class OnboardingUiState(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    val exit: OnboardingExit? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var isFinishing = false

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        appLanguage = settings.selectedAppLanguage,
                        partnerLanguage = settings.selectedPartnerLanguage
                    )
                }
            }
        }
    }

    fun onSelectAppLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setAppLanguage(language.code) }
    }

    fun onSelectPartnerLanguage(language: PartnerLanguage) {
        viewModelScope.launch { settingsRepository.setSelectedPartnerLanguage(language.code) }
    }

    /**
     * The exit is published only after the flag is written: navigating away clears this
     * ViewModel, and with it a write still in progress.
     */
    fun finish(exit: OnboardingExit) {
        if (isFinishing) return
        isFinishing = true
        viewModelScope.launch {
            try {
                settingsRepository.setHasCompletedOnboarding(true)
            } catch (e: IOException) {
                // Seeing the welcome flow once more beats being stuck in it
                Log.e(TAG, "Could not store onboarding completion", e)
            }
            _uiState.update { it.copy(exit = exit) }
        }
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}
