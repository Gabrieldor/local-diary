package com.localdiary.app.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localdiary.app.audio.AudioCapture
import com.localdiary.app.ml.WhisperEngineHolder
import com.localdiary.app.ml.WhisperModelState
import com.localdiary.app.ml.WhisperModelTier
import com.localdiary.coredata.DiaryEntry
import com.localdiary.coredata.DiaryEntryDao
import com.localdiary.coreml.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EntryEditorUiState(
    val text: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val modelState: WhisperModelState = WhisperModelState.Loading,
    val modelTier: WhisperModelTier = WhisperModelTier.FAST,
    val errorMessage: String? = null
)

/**
 * Controller coordinating audio capture, transcription, and persistence for creating a new
 * entry or editing an existing one — the two share almost all of the same behavior, so one
 * screen/ViewModel handles both rather than duplicating it, distinguished by [load]'s entryId.
 */
class EntryEditorViewModel(
    private val dao: DiaryEntryDao,
    private val whisperEngineHolder: WhisperEngineHolder
) : ViewModel() {

    private val audioCapture = AudioCapture()
    private val _uiState = MutableStateFlow(EntryEditorUiState())
    val uiState: StateFlow<EntryEditorUiState> = _uiState

    // Guards every call into the (not thread-safe) WhisperEngine, so a live streaming tick
    // and the final stop-time pass can never run concurrently on the same context.
    private val transcribeMutex = Mutex()
    private var streamingJob: Job? = null
    private var autosaveJob: Job? = null
    private var pinnedLanguage: String? = null
    private var textBeforeThisRecording: String = ""

    // Null until this session's text has been persisted at least once (a brand-new entry
    // with nothing autosaved yet); once set, further saves update that row instead of
    // inserting duplicates.
    private var persistedEntryId: Long? = null
    private var createdAtEpochMillis: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            whisperEngineHolder.state.collect { modelState ->
                _uiState.update { it.copy(modelState = modelState) }
            }
        }
        viewModelScope.launch {
            whisperEngineHolder.tier.collect { tier ->
                _uiState.update { it.copy(modelTier = tier) }
            }
        }
    }

    fun onTextChanged(newText: String) {
        setText(newText, scheduleAutosave = true)
    }

    fun selectModelTier(tier: WhisperModelTier) {
        if (_uiState.value.isRecording) return
        whisperEngineHolder.selectTier(viewModelScope, tier)
    }

    // The screen's viewModel() call reuses this instance across visits (keyed by class, not
    // by visit), so entering the screen — whether for a new entry or a different existing
    // one — must explicitly (re)target it rather than trusting leftover state.
    fun load(entryId: Long?) {
        streamingJob?.cancel()
        streamingJob = null
        autosaveJob?.cancel()
        autosaveJob = null
        pinnedLanguage = null
        textBeforeThisRecording = ""
        persistedEntryId = entryId
        createdAtEpochMillis = System.currentTimeMillis()

        _uiState.value = EntryEditorUiState(
            modelState = _uiState.value.modelState,
            modelTier = _uiState.value.modelTier
        )

        if (entryId != null) {
            viewModelScope.launch {
                val entry = withContext(Dispatchers.IO) { dao.getById(entryId) }
                if (entry != null) {
                    createdAtEpochMillis = entry.createdAtEpochMillis
                    setText(entry.transcript, scheduleAutosave = false)
                }
            }
        }
    }

    private fun setText(newText: String, scheduleAutosave: Boolean) {
        _uiState.update { it.copy(text = newText) }
        if (scheduleAutosave) scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            persistIfNotBlank(_uiState.value.text)
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        pinnedLanguage = null
        textBeforeThisRecording = _uiState.value.text
        audioCapture.start()
        _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        startLiveStreaming()
    }

    private fun startLiveStreaming() {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecording) {
                delay(STREAMING_TICK_MS)
                if (!_uiState.value.isRecording) break

                val engine = (whisperEngineHolder.state.value as? WhisperModelState.Ready)?.engine
                    ?: continue
                val samples = withContext(Dispatchers.IO) { audioCapture.peek() }
                if (samples.isEmpty()) continue

                val partial = runTranscribe(engine, samples, pinnedLanguage).trim()
                if (pinnedLanguage == null) {
                    pinnedLanguage = engine.lastDetectedLanguage()
                }
                if (_uiState.value.isRecording && partial.isNotEmpty()) {
                    setText(mergeText(textBeforeThisRecording, partial), scheduleAutosave = true)
                }
            }
        }
    }

    fun stopRecordingAndTranscribe() {
        val state = _uiState.value
        if (!state.isRecording) return
        streamingJob?.cancel()
        streamingJob = null

        val engine = (whisperEngineHolder.state.value as? WhisperModelState.Ready)?.engine
        _uiState.update { it.copy(isRecording = false, isTranscribing = engine != null) }

        viewModelScope.launch {
            val samples = withContext(Dispatchers.IO) { audioCapture.stop() }
            if (engine == null) {
                _uiState.update {
                    it.copy(isTranscribing = false, errorMessage = "Voice model isn't ready yet")
                }
                return@launch
            }
            val transcript = runTranscribe(engine, samples, pinnedLanguage).trim()
            _uiState.update { current ->
                current.copy(
                    isTranscribing = false,
                    text = mergeText(textBeforeThisRecording, transcript),
                    errorMessage = if (transcript.isEmpty()) {
                        "Didn't catch that — try again or type instead"
                    } else {
                        null
                    }
                )
            }
            scheduleAutosave()
        }
    }

    private suspend fun runTranscribe(engine: WhisperEngine, samples: FloatArray, language: String?): String =
        transcribeMutex.withLock {
            withContext(Dispatchers.Default) { engine.transcribe(samples, language) }
        }

    private fun mergeText(prefix: String, addition: String): String =
        listOf(prefix, addition).filter { it.isNotBlank() }.joinToString(separator = "\n")

    private suspend fun persistIfNotBlank(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        withContext(Dispatchers.IO) {
            val id = persistedEntryId
            if (id == null) {
                persistedEntryId = dao.insert(
                    DiaryEntry(
                        createdAtEpochMillis = createdAtEpochMillis,
                        transcript = trimmed,
                        audioFilePath = null,
                        photoFilePath = null
                    )
                )
            } else {
                dao.update(
                    DiaryEntry(
                        id = id,
                        createdAtEpochMillis = createdAtEpochMillis,
                        transcript = trimmed,
                        audioFilePath = null,
                        photoFilePath = null
                    )
                )
            }
        }
        return true
    }

    /** Explicit Save button: validates non-empty text, then persists and closes. */
    fun save(onSaved: () -> Unit) {
        if (_uiState.value.text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Write or record something first") }
            return
        }
        autosaveJob?.cancel()
        viewModelScope.launch {
            persistIfNotBlank(_uiState.value.text)
            onSaved()
        }
    }

    /** Back navigation: silently persists any non-blank text (no validation error), then closes. */
    fun closeWithoutExplicitSave(onDone: () -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            persistIfNotBlank(_uiState.value.text)
            onDone()
        }
    }

    class Factory(
        private val dao: DiaryEntryDao,
        private val whisperEngineHolder: WhisperEngineHolder
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EntryEditorViewModel(dao, whisperEngineHolder) as T
        }
    }

    companion object {
        private const val STREAMING_TICK_MS = 2500L
        private const val AUTOSAVE_DEBOUNCE_MS = 2000L
    }
}
