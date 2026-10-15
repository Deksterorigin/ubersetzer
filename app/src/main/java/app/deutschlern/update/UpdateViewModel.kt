package app.deutschlern.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.BuildConfig
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.update.data.UpdateRepository
import app.deutschlern.update.model.ReleaseInfo
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.model.UpdateState
import app.deutschlern.update.receiver.InstallEvent
import app.deutschlern.util.CsvExporter
import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val updateState: UpdateState = UpdateState.Idle,
    val isWifiOnly: Boolean = true,
    val currentVersionName: String = "",
    val currentVersionCode: Long = 0L,
    val isFeatureEnabled: Boolean = BuildConfig.ENABLE_SELF_UPDATE,
    val confirmationIntentToLaunch: Intent? = null
)

@HiltViewModel
class UpdateViewModel(
    private val updateRepository: UpdateRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val savedStateHandle: SavedStateHandle,
    private val isFeatureEnabled: Boolean = BuildConfig.ENABLE_SELF_UPDATE
) : ViewModel() {

    @Inject
    constructor(
        updateRepository: UpdateRepository,
        vocabularyRepository: VocabularyRepository,
        savedStateHandle: SavedStateHandle
    ) : this(
        updateRepository = updateRepository,
        vocabularyRepository = vocabularyRepository,
        savedStateHandle = savedStateHandle,
        isFeatureEnabled = BuildConfig.ENABLE_SELF_UPDATE
    )

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersionName = updateRepository.getCurrentVersionName(),
            currentVersionCode = updateRepository.getCurrentVersionCode(),
            isWifiOnly = savedStateHandle["wifi_only"] ?: true,
            isFeatureEnabled = isFeatureEnabled
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var verifiedApkFile: File? = null

    init {
        observeInstallEvents()
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            updateRepository.installEvents.collect { event ->
                when (event) {
                    is InstallEvent.PendingUserAction -> {
                        _uiState.update { current ->
                            current.copy(
                                confirmationIntentToLaunch = event.confirmationIntent
                            )
                        }
                    }

                    is InstallEvent.Success -> {
                        updateRepository.cleanupTempFiles()
                        _uiState.update { it.copy(updateState = UpdateState.Idle, confirmationIntentToLaunch = null) }
                    }

                    is InstallEvent.Failure -> {
                        updateRepository.cleanupTempFiles()
                        _uiState.update {
                            it.copy(
                                updateState = UpdateState.Error(
                                    UpdateError.InstallFailed(event.statusCode, event.message)
                                ),
                                confirmationIntentToLaunch = null
                            )
                        }
                    }
                }
            }
        }
    }

    fun onCheckForUpdates() {
        if (!_uiState.value.isFeatureEnabled) return

        _uiState.update { it.copy(updateState = UpdateState.Checking) }

        viewModelScope.launch {
            val result = updateRepository.checkForUpdates()
            result.fold(
                onSuccess = { release ->
                    _uiState.update {
                        it.copy(
                            updateState = if (release != null) {
                                UpdateState.UpdateAvailable(release)
                            } else {
                                UpdateState.UpToDate(_uiState.value.currentVersionName)
                            }
                        )
                    }
                },
                onFailure = { throwable ->
                    val error = (throwable as? UpdateError) ?: UpdateError.GenericError(throwable.message ?: "Unknown error")
                    _uiState.update { it.copy(updateState = UpdateState.Error(error)) }
                }
            )
        }
    }

    fun onDownloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateState = UpdateState.Downloading(
                        release = release,
                        progressPercent = 0,
                        bytesDownloaded = 0L,
                        totalBytes = release.apkSizeBytes
                    )
                )
            }

            val downloadResult = updateRepository.downloadApk(
                release = release,
                requireWifi = _uiState.value.isWifiOnly
            ) { percent, bytesRead, totalBytes ->
                _uiState.update { current ->
                    if (current.updateState is UpdateState.Downloading) {
                        current.copy(
                            updateState = UpdateState.Downloading(
                                release = release,
                                progressPercent = percent,
                                bytesDownloaded = bytesRead,
                                totalBytes = totalBytes
                            )
                        )
                    } else current
                }
            }

            downloadResult.fold(
                onSuccess = { apkFile ->
                    _uiState.update { it.copy(updateState = UpdateState.Verifying(release)) }

                    val verifyResult = updateRepository.verifyApk(apkFile, release.expectedDigest)
                    verifyResult.fold(
                        onSuccess = {
                            verifiedApkFile = apkFile
                            if (!updateRepository.canRequestPackageInstalls()) {
                                _uiState.update {
                                    it.copy(updateState = UpdateState.PermissionRequired(release, apkFile))
                                }
                            } else {
                                proceedWithInstall(release, apkFile)
                            }
                        },
                        onFailure = { throwable ->
                            val error = (throwable as? UpdateError) ?: UpdateError.GenericError(throwable.message ?: "Verification failed")
                            _uiState.update { it.copy(updateState = UpdateState.Error(error)) }
                        }
                    )
                },
                onFailure = { throwable ->
                    val error = (throwable as? UpdateError) ?: UpdateError.GenericError(throwable.message ?: "Download failed")
                    _uiState.update { it.copy(updateState = UpdateState.Error(error)) }
                }
            )
        }
    }

    private fun proceedWithInstall(release: ReleaseInfo, apkFile: File) {
        _uiState.update { it.copy(updateState = UpdateState.Installing(release)) }
        val installResult = updateRepository.installApk(apkFile)
        installResult.onFailure { throwable ->
            val error = (throwable as? UpdateError) ?: UpdateError.GenericError(throwable.message ?: "Install session failed")
            _uiState.update { it.copy(updateState = UpdateState.Error(error)) }
        }
    }

    fun onResumeCheckPermissions(release: ReleaseInfo) {
        val apkFile = verifiedApkFile
        if (apkFile != null && apkFile.exists()) {
            if (updateRepository.canRequestPackageInstalls()) {
                proceedWithInstall(release, apkFile)
            }
        }
    }

    fun onCancelDownload() {
        updateRepository.cancelDownload()
        _uiState.update { it.copy(updateState = UpdateState.Idle) }
    }

    fun onToggleWifiOnly(enabled: Boolean) {
        savedStateHandle["wifi_only"] = enabled
        _uiState.update { it.copy(isWifiOnly = enabled) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(updateState = UpdateState.Idle) }
    }

    fun onConfirmationIntentLaunched() {
        _uiState.update { it.copy(confirmationIntentToLaunch = null) }
    }

    fun onExportVocabularyBackup(context: Context, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val allWords = vocabularyRepository.getAllWords().first()
            if (allWords.isEmpty()) {
                onEmpty()
            } else {
                CsvExporter.shareAnkiCsv(context, allWords)
            }
        }
    }
}
