package com.localdiary.app.ml

import android.content.Context
import com.localdiary.coreml.WhisperEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WhisperModelTier(val assetName: String, val label: String) {
    FAST("ggml-base-q5_1.bin", "Fast"),
    PRECISE("ggml-small-q5_1.bin", "Precise")
}

sealed interface WhisperModelState {
    data object Loading : WhisperModelState
    data class Ready(val engine: WhisperEngine) : WhisperModelState
    data class Failed(val reason: String) : WhisperModelState
}

/**
 * Loads a bundled multilingual Whisper model tier off the main thread and exposes
 * readiness so any screen needing transcription can observe it. Only one tier's model is
 * kept resident at a time — switching releases the current engine before loading the
 * other, trading a brief pause for bounded memory use on the GDD's mid-range-phone target.
 */
class WhisperEngineHolder(private val context: Context) {

    private val _tier = MutableStateFlow(WhisperModelTier.FAST)
    val tier: StateFlow<WhisperModelTier> = _tier

    private val _state = MutableStateFlow<WhisperModelState>(WhisperModelState.Loading)
    val state: StateFlow<WhisperModelState> = _state

    private val loadMutex = Mutex()
    private var loadedEngine: WhisperEngine? = null

    fun loadInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { loadTier(_tier.value) }
    }

    /** No-op if [tier] is already selected and ready. */
    fun selectTier(scope: CoroutineScope, tier: WhisperModelTier) {
        if (_tier.value == tier && _state.value is WhisperModelState.Ready) return
        _tier.value = tier
        scope.launch(Dispatchers.IO) { loadTier(tier) }
    }

    private suspend fun loadTier(tier: WhisperModelTier) {
        loadMutex.withLock {
            _state.value = WhisperModelState.Loading
            loadedEngine?.release()
            loadedEngine = null

            _state.value = try {
                val modelFile = provisionModelFile(tier)
                val engine = WhisperEngine()
                if (engine.loadModel(modelFile.absolutePath)) {
                    loadedEngine = engine
                    WhisperModelState.Ready(engine)
                } else {
                    WhisperModelState.Failed("Model failed to initialize")
                }
            } catch (e: Exception) {
                WhisperModelState.Failed(e.message ?: "Unknown error loading voice model")
            }
        }
    }

    // Assets live inside the APK (possibly compressed); whisper.cpp needs a real filesystem
    // path, so copy to internal storage once and load from there on every subsequent start.
    private fun provisionModelFile(tier: WhisperModelTier): File {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val destFile = File(modelsDir, tier.assetName)
        if (!destFile.exists()) {
            context.assets.open("models/${tier.assetName}").use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destFile
    }
}
