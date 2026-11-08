package app.deutschlern.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides once per activity lifetime whether the app opens on the welcome flow.
 * The answer never changes afterwards: it picks the navigation graph's start destination,
 * which has to stay the same when finishing the flow flips the stored flag.
 */
@HiltViewModel
class OnboardingGateViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wordDao: WordDao,
    private val secureKeyStorage: SecureKeyStorage
) : ViewModel() {

    private val _showOnboarding = MutableStateFlow<Boolean?>(null)

    /** Null until the decision is known. */
    val showOnboarding: StateFlow<Boolean?> = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            _showOnboarding.value = try {
                resolve()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Must not leave the app blank; nothing is stored, so the next launch decides again
                Log.e(TAG, "Could not decide on onboarding", e)
                false
            }
        }
    }

    private suspend fun resolve(): Boolean {
        if (settingsRepository.getSettings().hasCompletedOnboarding) return false

        // The flag is new, so people updating the app have it unset along with their words and keys
        val isExistingUser = wordDao.hasAnyWord() || secureKeyStorage.hasAnyKey()
        if (isExistingUser) {
            settingsRepository.setHasCompletedOnboarding(true)
        }
        return !isExistingUser
    }

    private companion object {
        const val TAG = "OnboardingGate"
    }
}
