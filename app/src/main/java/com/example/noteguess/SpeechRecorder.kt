package com.example.noteguess

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records one spoken utterance from the microphone, using a simple
 * loudness-based start/stop detector so the caller gets just the speech.
 * [record] blocks and must be called from a background thread; RECORD_AUDIO
 * permission must already be granted.
 */
class SpeechRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHUNK_MS = 20
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
        private const val NOISE_CALIBRATION_CHUNKS = 6
        private const val PRE_ROLL_CHUNKS = 10
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    /**
     * Waits for speech and returns it as 16 kHz mono PCM, or null if nothing
     * was heard within [timeoutMs] (or recording was cancelled/failed).
     */
    @SuppressLint("MissingPermission")
    fun record(
        maxUtteranceMs: Int = 3500,
        silenceTailMs: Int = 700,
        timeoutMs: Int = 6000,
    ): ShortArray? {
        cancelled = false
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, CHUNK_SAMPLES * 8 * 2),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }

        try {
            audioRecord.startRecording()
            val preRoll = ArrayDeque<ShortArray>()
            val voiced = mutableListOf<ShortArray>()
            var noiseFloor = 0.0
            var calibrated = 0
            var speechStarted = false
            var silentMs = 0
            var elapsedMs = 0

            while (!cancelled) {
                val chunk = ShortArray(CHUNK_SAMPLES)
                var offset = 0
                while (offset < CHUNK_SAMPLES) {
                    val read = audioRecord.read(chunk, offset, CHUNK_SAMPLES - offset)
                    if (read <= 0) return null
                    offset += read
                }
                elapsedMs += CHUNK_MS
                val level = rms(chunk)

                if (!speechStarted) {
                    if (calibrated < NOISE_CALIBRATION_CHUNKS) {
                        noiseFloor += level / NOISE_CALIBRATION_CHUNKS
                        calibrated++
                    } else {
                        val startThreshold = max(noiseFloor * 3.5, 300.0)
                        if (level > startThreshold) {
                            speechStarted = true
                            voiced.addAll(preRoll)
                            voiced.add(chunk)
                        } else {
                            preRoll.addLast(chunk)
                            if (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
                        }
                    }
                    if (elapsedMs >= timeoutMs) return null
                } else {
                    voiced.add(chunk)
                    val endThreshold = max(noiseFloor * 2.0, 200.0)
                    silentMs = if (level < endThreshold) silentMs + CHUNK_MS else 0
                    if (silentMs >= silenceTailMs || voiced.size * CHUNK_MS >= maxUtteranceMs) break
                }
            }

            if (cancelled || !speechStarted) return null
            val result = ShortArray(voiced.size * CHUNK_SAMPLES)
            voiced.forEachIndexed { i, chunk ->
                chunk.copyInto(result, i * CHUNK_SAMPLES)
            }
            return result
        } finally {
            try {
                audioRecord.stop()
            } catch (_: IllegalStateException) {
                // Never started recording; nothing to stop.
            }
            audioRecord.release()
        }
    }

    private fun rms(chunk: ShortArray): Double {
        var sum = 0.0
        for (sample in chunk) sum += sample.toDouble() * sample
        return sqrt(sum / chunk.size)
    }
}
