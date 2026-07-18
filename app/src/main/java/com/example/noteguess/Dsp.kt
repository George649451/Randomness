package com.example.noteguess

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal speech DSP: converts raw PCM into MFCC feature frames, the compact
 * acoustic fingerprint used to compare the driver's answer against their own
 * enrolled recordings.
 */
object Dsp {

    private const val FRAME_SIZE = 400 // 25 ms at 16 kHz
    private const val HOP_SIZE = 160 // 10 ms at 16 kHz
    private const val FFT_SIZE = 512
    private const val NUM_FILTERS = 26
    private const val NUM_COEFFS = 13

    /** MFCC frames (one DoubleArray of [NUM_COEFFS] per 10 ms), mean-normalized. */
    fun mfcc(pcm: ShortArray, sampleRate: Int): Array<DoubleArray> {
        if (pcm.size < FRAME_SIZE) return emptyArray()

        // Pre-emphasis boosts the higher frequencies that consonants live in.
        val signal = DoubleArray(pcm.size)
        signal[0] = pcm[0].toDouble()
        for (i in 1 until pcm.size) {
            signal[i] = pcm[i] - 0.97 * pcm[i - 1]
        }

        val window = DoubleArray(FRAME_SIZE) { 0.54 - 0.46 * cos(2.0 * PI * it / (FRAME_SIZE - 1)) }
        val filterBank = melFilterBank(sampleRate)
        val frameCount = (pcm.size - FRAME_SIZE) / HOP_SIZE + 1
        val frames = Array(frameCount) { frame ->
            val start = frame * HOP_SIZE
            val re = DoubleArray(FFT_SIZE)
            val im = DoubleArray(FFT_SIZE)
            for (i in 0 until FRAME_SIZE) {
                re[i] = signal[start + i] * window[i]
            }
            fft(re, im)

            val power = DoubleArray(FFT_SIZE / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
            val logEnergies = DoubleArray(NUM_FILTERS) { f ->
                var energy = 0.0
                for (bin in filterBank[f].indices) {
                    energy += power[bin] * filterBank[f][bin]
                }
                ln(energy + 1e-10)
            }
            DoubleArray(NUM_COEFFS) { k ->
                var sum = 0.0
                for (m in 0 until NUM_FILTERS) {
                    sum += logEnergies[m] * cos(PI * k * (m + 0.5) / NUM_FILTERS)
                }
                sum
            }
        }

        // Cepstral mean normalization removes channel/mic coloration.
        val mean = DoubleArray(NUM_COEFFS)
        for (frame in frames) for (k in 0 until NUM_COEFFS) mean[k] += frame[k]
        for (k in 0 until NUM_COEFFS) mean[k] /= frameCount
        for (frame in frames) for (k in 0 until NUM_COEFFS) frame[k] -= mean[k]
        return frames
    }

    /**
     * Dynamic-time-warping distance between two MFCC sequences, normalized by
     * path length so short and long utterances are comparable.
     */
    fun dtw(a: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
        val inf = Double.MAX_VALUE / 4
        var prev = DoubleArray(b.size + 1) { if (it == 0) 0.0 else inf }
        var cur = DoubleArray(b.size + 1)
        for (i in 1..a.size) {
            cur[0] = inf
            for (j in 1..b.size) {
                cur[j] = frameDistance(a[i - 1], b[j - 1]) +
                    minOf(prev[j], cur[j - 1], prev[j - 1])
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.size] / (a.size + b.size)
    }

    private fun frameDistance(x: DoubleArray, y: DoubleArray): Double {
        var sum = 0.0
        for (k in x.indices) {
            val d = x[k] - y[k]
            sum += d * d
        }
        return sqrt(sum)
    }

    private fun melFilterBank(sampleRate: Int): Array<DoubleArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val lowMel = hzToMel(80.0)
        val highMel = hzToMel(sampleRate / 2.0)
        val binOf = DoubleArray(NUM_FILTERS + 2) { p ->
            val hz = melToHz(lowMel + (highMel - lowMel) * p / (NUM_FILTERS + 1))
            floor((FFT_SIZE + 1) * hz / sampleRate)
        }
        return Array(NUM_FILTERS) { f ->
            val filter = DoubleArray(FFT_SIZE / 2 + 1)
            val left = binOf[f].toInt()
            val center = binOf[f + 1].toInt()
            val right = binOf[f + 2].toInt()
            for (bin in left until center) {
                if (center > left) filter[bin] = (bin - left).toDouble() / (center - left)
            }
            for (bin in center until right) {
                if (right > center) filter[bin] = (right - bin).toDouble() / (right - center)
            }
            filter
        }
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe = re[start + k + len / 2] * curRe - im[start + k + len / 2] * curIm
                    val oddIm = re[start + k + len / 2] * curIm + im[start + k + len / 2] * curRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + len / 2] = evenRe - oddRe
                    im[start + k + len / 2] = evenIm - oddIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }
    }
}
