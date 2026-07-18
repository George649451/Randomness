package com.example.noteguess

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthesizes single notes as short tones and plays them through AudioTrack,
 * so the app needs no bundled audio files.
 */
class NotePlayer {

    companion object {
        const val NOTE_DURATION_MS = 800
        private const val SAMPLE_RATE = 44100

        /** Frequency in Hz for a MIDI note number (A4 = 69 = 440 Hz). */
        fun frequencyForMidi(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    private var track: AudioTrack? = null

    /** Stops any current tone and plays the given MIDI note. */
    fun play(midi: Int) {
        stop()
        val samples = synthesize(frequencyForMidi(midi))
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()
        track = audioTrack
    }

    fun stop() {
        track?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // Track was already stopped or never played; safe to ignore.
            }
            it.release()
        }
        track = null
    }

    private fun synthesize(frequency: Double): ShortArray {
        val totalSamples = SAMPLE_RATE * NOTE_DURATION_MS / 1000
        val attackSamples = SAMPLE_RATE * 15 / 1000
        val releaseSamples = SAMPLE_RATE * 120 / 1000
        val samples = ShortArray(totalSamples)
        for (i in samples.indices) {
            // Fundamental plus two soft overtones so it sounds less like a bare beep.
            val phase = 2.0 * PI * frequency * i / SAMPLE_RATE
            val value = (sin(phase) + 0.35 * sin(2 * phase) + 0.15 * sin(3 * phase)) / 1.5
            // Short attack and release ramps prevent clicks at the tone edges.
            val attack = min(1.0, i.toDouble() / attackSamples)
            val release = min(1.0, (totalSamples - 1 - i).toDouble() / releaseSamples)
            samples[i] = (value * attack * release * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return samples
    }
}
