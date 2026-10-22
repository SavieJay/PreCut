package com.savie.precut.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.ai.Transcriber
import com.savie.precut.ai.UploadConsent
import com.savie.precut.ai.cloud.CloudClient
import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.ai.cloud.NetworkState
import com.savie.precut.data.AppSettings
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.MediaProbe
import com.savie.precut.media.TtsSynthesizer
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.model.ai.AiFeature
import com.savie.precut.model.ai.AiResult
import com.savie.precut.model.ai.CloudStatus
import com.savie.precut.model.ai.Transcript
import com.savie.precut.model.ai.UploadSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.inject.Inject

/** Settings → Cloud AI (Phase 20). */
data class CloudAiState(
    val enabled: Boolean,
    val setUp: Boolean,
    val minutes: CloudMinutes = CloudMinutes.Checking,
    val check: CloudCheck = CloudCheck.Idle,
    /** The consent dialog, open for this upload. */
    val consent: UploadSummary? = null,
    val deletion: CloudDeletion = CloudDeletion.Idle,
)

sealed interface CloudMinutes {
    data object Checking : CloudMinutes
    /** Nothing sent from this phone yet, so the server hasn't been asked. */
    data object NotUsed : CloudMinutes
    data class Known(val status: CloudStatus) : CloudMinutes
    data class Unknown(val failure: AiFailure) : CloudMinutes
}

sealed interface CloudCheck {
    data object Idle : CloudCheck
    data object Preparing : CloudCheck
    data object Sending : CloudCheck
    data class Heard(val transcript: Transcript) : CloudCheck
    data class Failed(val failure: AiFailure) : CloudCheck
    data object Cancelled : CloudCheck
    /** The phone's text-to-speech couldn't make the test phrase. */
    data object NoTestSound : CloudCheck
}

sealed interface CloudDeletion {
    data object Idle : CloudDeletion
    data object Deleting : CloudDeletion
    data object Done : CloudDeletion
    data class Failed(val failure: AiFailure) : CloudDeletion
}

@HiltViewModel
class CloudAiViewModel @Inject constructor(
    private val settings: AppSettings,
    private val client: CloudClient,
    private val transcriber: Transcriber,
    private val tts: TtsSynthesizer,
    private val probe: MediaProbe,
    private val network: NetworkState,
    config: CloudConfig,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudAiState(enabled = settings.values.value.cloudAiEnabled, setUp = config.isSetUp))
    val state: StateFlow<CloudAiState> = _state.asStateFlow()

    /** The test phrase, from when it's made until it's sent or cancelled (or the screen closes). */
    private var testFile: File? = null

    init {
        refreshMinutes()
    }

    fun onEnabled(enabled: Boolean) {
        settings.update { it.copy(cloudAiEnabled = enabled) }
        _state.update { it.copy(enabled = enabled, check = CloudCheck.Idle) }
        refreshMinutes()
    }

    private fun refreshMinutes() {
        viewModelScope.launch {
            _state.update { it.copy(minutes = CloudMinutes.Checking) }
            val minutes = when (val result = client.status()) {
                is AiResult.Ok -> result.value?.let(CloudMinutes::Known) ?: CloudMinutes.NotUsed
                is AiResult.Failed -> CloudMinutes.Unknown(result.failure)
            }
            _state.update { it.copy(minutes = minutes) }
        }
    }

    /** Check cloud AI: the phone speaks a test phrase, then the consent dialog asks before anything is sent. */
    fun onCheck() {
        val current = _state.value
        if (current.check == CloudCheck.Preparing || current.check == CloudCheck.Sending || current.consent != null) return
        when {
            !current.setUp -> return _state.update { it.copy(check = CloudCheck.Failed(AiFailure.NotSetUp)) }
            !current.enabled -> return _state.update { it.copy(check = CloudCheck.Failed(AiFailure.CloudOff)) }
            // Say so now, rather than after making the test phrase and asking to send it.
            !network.isOnline() -> return _state.update { it.copy(check = CloudCheck.Failed(AiFailure.Offline)) }
        }
        viewModelScope.launch {
            _state.update { it.copy(check = CloudCheck.Preparing) }
            val uri = tts.synthesize(TEST_PHRASE, voiceName = null, speed = 1f, pitch = 1f)
            val file = uri?.let { File(URI(it)) }
            val durationMs = uri?.let { probe.probeAudioDurationMs(it) }
            if (file == null || durationMs == null || durationMs <= 0L) {
                file?.let { withContext(io) { it.delete() } }
                _state.update { it.copy(check = CloudCheck.NoTestSound) }
                return@launch
            }
            testFile = file
            _state.update { it.copy(check = CloudCheck.Idle, consent = UploadSummary(AiFeature.CHECK, durationMs, file.length())) }
        }
    }

    fun onConsentSend() {
        val summary = _state.value.consent ?: return
        val file = testFile ?: return
        // The only place a consent is given: the user just tapped Send for this summary.
        val consent = UploadConsent.givenFor(summary)
        _state.update { it.copy(consent = null, check = CloudCheck.Sending) }
        viewModelScope.launch {
            val result = transcriber.transcribe(file, TEST_MIME_TYPE, summary.durationMs, TEST_LANGUAGE, consent)
            // Kept in testFile until now, so onCleared deletes it if the screen closes mid-upload.
            testFile = null
            withContext(io) { file.delete() }
            _state.update {
                it.copy(
                    check = when (result) {
                        is AiResult.Ok -> CloudCheck.Heard(result.value)
                        is AiResult.Failed -> CloudCheck.Failed(result.failure)
                    },
                )
            }
            refreshMinutes()
        }
    }

    fun onConsentCancel() {
        val file = testFile
        testFile = null
        _state.update { it.copy(consent = null, check = CloudCheck.Cancelled) }
        file?.let { viewModelScope.launch { withContext(io) { it.delete() } } }
    }

    fun onDelete() {
        if (_state.value.deletion == CloudDeletion.Deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deletion = CloudDeletion.Deleting) }
            val deletion = when (val result = client.forget()) {
                is AiResult.Ok -> CloudDeletion.Done
                is AiResult.Failed -> CloudDeletion.Failed(result.failure)
            }
            _state.update { it.copy(deletion = deletion) }
            refreshMinutes()
        }
    }

    override fun onCleared() {
        testFile?.delete()
    }

    private companion object {
        const val TEST_PHRASE = "Hello from PreCut. This is a test of the captions."
        const val TEST_LANGUAGE = "en"
        const val TEST_MIME_TYPE = "audio/wav"
    }
}
