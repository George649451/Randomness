package com.example.noteguess

import android.content.Context

/**
 * Matches a spoken answer against the user's enrolled recordings: nearest
 * template by DTW distance over MFCC features wins.
 */
class VoiceMatcher private constructor(
    private val templates: List<Pair<Int, Array<DoubleArray>>>,
) {

    data class Result(val note: Int, val ambiguous: Boolean)

    /**
     * Best-matching note for the utterance. [Result.ambiguous] is set when the
     * runner-up is nearly as close, so the caller should ask the user to
     * repeat rather than trust the pick.
     */
    fun match(pcm: ShortArray): Result? {
        val features = Dsp.mfcc(pcm, SpeechRecorder.SAMPLE_RATE)
        if (features.isEmpty()) return null
        val ranked = templates
            .map { (note, template) -> note to Dsp.dtw(features, template) }
            .sortedBy { it.second }
        val (bestNote, bestDistance) = ranked[0]
        val runnerUpDistance = ranked.getOrNull(1)?.second ?: Double.MAX_VALUE
        return Result(bestNote, ambiguous = bestDistance > 0.88 * runnerUpDistance)
    }

    companion object {
        /** Loads all enrolled recordings; null unless enrollment is complete. */
        fun load(context: Context): VoiceMatcher? {
            if (!EnrollmentStore.isComplete(context)) return null
            val templates = (0 until Notes.COUNT).map { note ->
                val pcm = EnrollmentStore.load(context, note) ?: return null
                note to Dsp.mfcc(pcm, SpeechRecorder.SAMPLE_RATE)
            }
            if (templates.any { it.second.isEmpty() }) return null
            return VoiceMatcher(templates)
        }
    }
}
