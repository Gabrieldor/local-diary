package com.localdiary.coreml

/**
 * Thin Kotlin wrapper over the whisper.cpp JNI bridge (whisper_jni.cpp).
 * Not thread-safe: callers must serialize access to a given instance,
 * matching whisper.cpp's own "not thread safe for same context" contract.
 */
class WhisperEngine {

    private var contextHandle: Long = 0

    fun loadModel(modelPath: String): Boolean {
        contextHandle = nativeLoadModel(modelPath)
        return contextHandle != 0L
    }

    /**
     * [pcm16kMono] must be 16kHz, mono, float32 samples in [-1, 1]. [languageOverride], if
     * given, skips auto-detection and forces that language — used to pin the language
     * across repeated calls during live re-transcription instead of re-detecting each time.
     */
    fun transcribe(pcm16kMono: FloatArray, languageOverride: String? = null): String {
        check(contextHandle != 0L) { "Model not loaded" }
        return nativeTranscribe(contextHandle, pcm16kMono, languageOverride)
    }

    /** The language code used by the most recent [transcribe] call, or null if none yet. */
    fun lastDetectedLanguage(): String? {
        check(contextHandle != 0L) { "Model not loaded" }
        return nativeLastDetectedLanguage(contextHandle).takeIf { it.isNotEmpty() }
    }

    fun release() {
        if (contextHandle != 0L) {
            nativeFreeModel(contextHandle)
            contextHandle = 0
        }
    }

    private external fun nativeLoadModel(modelPath: String): Long
    private external fun nativeTranscribe(
        contextHandle: Long,
        pcm16kMono: FloatArray,
        languageOverride: String?
    ): String
    private external fun nativeLastDetectedLanguage(contextHandle: Long): String
    private external fun nativeFreeModel(contextHandle: Long)

    companion object {
        init {
            System.loadLibrary("diary_whisper")
        }
    }
}
