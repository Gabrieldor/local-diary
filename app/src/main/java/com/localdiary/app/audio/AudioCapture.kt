package com.localdiary.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * Captures microphone audio as 16kHz mono float32 samples in [-1, 1], the format
 * WhisperEngine.transcribe expects. Not thread-safe: one recording session at a time.
 */
class AudioCapture {

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null
    private val buffer = ByteArrayOutputStream()

    // Caller must verify RECORD_AUDIO is granted before calling start().
    @SuppressLint("MissingPermission")
    fun start() {
        check(audioRecord == null) { "Already recording" }
        buffer.reset()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2
        )
        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            val readBuffer = ByteArray(minBufferSize)
            while (isRecording) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    synchronized(buffer) { buffer.write(readBuffer, 0, read) }
                }
            }
        }.also { it.start() }
    }

    /** Stops recording and returns the captured audio as 16kHz mono float32 samples. */
    fun stop(): FloatArray {
        isRecording = false
        recordingThread?.join()
        recordingThread = null

        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        return peek()
    }

    /** Snapshot of the audio captured so far, without stopping recording. */
    fun peek(): FloatArray {
        val pcm16 = synchronized(buffer) { buffer.toByteArray() }
        return pcm16ToFloat32(pcm16)
    }

    private fun pcm16ToFloat32(pcm16: ByteArray): FloatArray {
        val sampleCount = pcm16.size / 2
        val samples = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = pcm16[i * 2].toInt() and 0xFF
            val high = pcm16[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            samples[i] = sample / 32768f
        }
        return samples
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16000
    }
}
