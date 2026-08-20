#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

namespace {

// whisper's built-in auto-detect looks at a single ~30s window, and every encoder pass
// costs the same fixed amount regardless of how short the real audio is — so extra
// samples aren't free. The dominant failure mode in testing was the first second or two
// after tapping the mic (reaction-time silence/breath) getting classified instead of
// actual speech, so skip ahead ~3s before detecting rather than sampling multiple points.
// Falls back toward the middle of clips too short to have 3s to skip. The winner is
// restricted to the two languages this app currently supports (Portuguese/English)
// rather than whisper's full ~99-language table — revisit if more languages are added.
const char *detectLanguage(whisper_context *ctx, jsize numSamples, int nThreads) {
    const int durationMs = static_cast<int>((static_cast<int64_t>(numSamples) * 1000) / WHISPER_SAMPLE_RATE);
    if (durationMs <= 0) {
        return "auto";
    }

    const int offsetMs = durationMs > 3000 ? 3000 : durationMs / 2;

    const int numLangs = whisper_lang_max_id() + 1;
    std::vector<float> probs(numLangs, 0.0f);
    if (whisper_lang_auto_detect(ctx, offsetMs, nThreads, probs.data()) < 0) {
        return "auto";
    }

    const int enId = whisper_lang_id("en");
    const int ptId = whisper_lang_id("pt");
    return probs[enId] >= probs[ptId] ? "en" : "pt";
}

} // namespace

extern "C" {

// jlong doubles as the native whisper_context* handle held on the Kotlin side.
JNIEXPORT jlong JNICALL
Java_com_localdiary_coreml_WhisperEngine_nativeLoadModel(JNIEnv *env, jobject /* this */, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_localdiary_coreml_WhisperEngine_nativeTranscribe(JNIEnv *env, jobject /* this */,
                                                            jlong contextHandle,
                                                            jfloatArray pcm16kMono,
                                                            jstring languageOverride) {
    auto *ctx = reinterpret_cast<whisper_context *>(contextHandle);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jsize numSamples = env->GetArrayLength(pcm16kMono);
    jfloat *samples = env->GetFloatArrayElements(pcm16kMono, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.single_segment = false;
    params.n_threads = 4;

    // languageOverride lets a caller pin a language already detected on an earlier call
    // (e.g. live re-transcription ticks during recording), skipping the extra encoder
    // pass detection costs and avoiding the language flip-flopping between ticks.
    const char *overrideChars = nullptr;
    if (languageOverride != nullptr) {
        overrideChars = env->GetStringUTFChars(languageOverride, nullptr);
    }

    if (overrideChars != nullptr && overrideChars[0] != '\0') {
        params.language = overrideChars;
    } else if (whisper_pcm_to_mel(ctx, samples, numSamples, params.n_threads) == 0) {
        params.language = detectLanguage(ctx, numSamples, params.n_threads);
    } else {
        params.language = "auto";
    }

    std::string result;
    if (whisper_full(ctx, params, samples, numSamples) == 0) {
        const int numSegments = whisper_full_n_segments(ctx);
        for (int i = 0; i < numSegments; i++) {
            result += whisper_full_get_segment_text(ctx, i);
        }
    }

    if (overrideChars != nullptr) {
        env->ReleaseStringUTFChars(languageOverride, overrideChars);
    }
    env->ReleaseFloatArrayElements(pcm16kMono, samples, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_localdiary_coreml_WhisperEngine_nativeLastDetectedLanguage(JNIEnv *env, jobject /* this */,
                                                                      jlong contextHandle) {
    auto *ctx = reinterpret_cast<whisper_context *>(contextHandle);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }
    const char *langStr = whisper_lang_str(whisper_full_lang_id(ctx));
    return env->NewStringUTF(langStr != nullptr ? langStr : "");
}

JNIEXPORT void JNICALL
Java_com_localdiary_coreml_WhisperEngine_nativeFreeModel(JNIEnv *env, jobject /* this */, jlong contextHandle) {
    auto *ctx = reinterpret_cast<whisper_context *>(contextHandle);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

} // extern "C"
