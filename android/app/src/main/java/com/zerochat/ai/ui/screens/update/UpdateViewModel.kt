package com.zerochat.ai.ui.screens.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerochat.ai.data.api.ApiClient
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.model.AppVersionResponse
import com.zerochat.ai.data.repository.UpdateRepository
import com.zerochat.ai.util.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    data class UpdateAvailable(
        val currentVersionName: String,
        val latestVersionName: String,
        val targetVersionCode: Int,
        val releaseNotes: String,
        val apkUrl: String,
        val apkSha256: String,
        val apkSize: Long,
        val isMandatory: Boolean
    ) : UpdateState()
    data class Downloading(val progressPercent: Int) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
    object Dismissed : UpdateState()
}

class UpdateViewModel(private val repository: UpdateRepository) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var activeVersionInfo: AppVersionResponse? = null

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            val installedCode = UpdateManager.getInstalledVersionCode(context)
            val installedName = UpdateManager.getInstalledVersionName(context)

            when (val res = repository.getLatestVersion()) {
                is ApiResult.Success -> {
                    val latest = res.data
                    activeVersionInfo = latest
                    if (latest.versionCode > installedCode) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            currentVersionName = installedName,
                            latestVersionName = latest.versionName,
                            targetVersionCode = latest.versionCode,
                            releaseNotes = latest.releaseNotes,
                            apkUrl = latest.apkUrl,
                            apkSha256 = latest.apkSha256,
                            apkSize = latest.apkSize,
                            isMandatory = latest.isMandatory
                        )
                    }
                }
                is ApiResult.Error -> {
                    // Fail silently during background check so as not to interrupt user
                }
                else -> {}
            }
        }
    }

    fun startDownload(context: Context) {
        val version = activeVersionInfo ?: return
        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0)

                // Resolve absolute HTTPS URL if relative
                val downloadUrl = if (version.apkUrl.startsWith("http://") || version.apkUrl.startsWith("https://")) {
                    version.apkUrl
                } else {
                    val base = ApiClient.getBaseUrl().trimEnd('/')
                    val path = version.apkUrl.trimStart('/')
                    "$base/$path"
                }

                // 1. Download
                val file = UpdateManager.downloadApk(context, downloadUrl, version.versionCode) { percent ->
                    _updateState.value = UpdateState.Downloading(percent)
                }

                // 2. Verify SHA-256
                _updateState.value = UpdateState.Verifying
                UpdateManager.verifyChecksum(file, version.apkSha256)

                // 3. Verify Package & Signatures
                UpdateManager.verifyPackageAndSignatures(context, file)

                // 4. Launch PackageInstaller flow
                _updateState.value = UpdateState.ReadyToInstall(file)
                UpdateManager.startInstallIntent(context, file)

            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    e.message ?: "Failed to download and verify update."
                )
            }
        }
    }

    fun dismiss() {
        val current = _updateState.value
        if (current is UpdateState.UpdateAvailable && current.isMandatory) {
            // Cannot dismiss mandatory update
            return
        }
        _updateState.value = UpdateState.Dismissed
    }

    fun retry(context: Context) {
        startDownload(context)
    }
}

