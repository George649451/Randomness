package com.example.acremote

/**
 * Mark/space timings of one A/C protocol, in microseconds, plus how many copies
 * of the frame a single keypress puts on the air.
 */
data class IrTiming(
    val headerMark: Int,
    val headerSpace: Int,
    val bitMark: Int,
    val oneSpace: Int,
    val zeroSpace: Int,
    val footerMark: Int,
    val gap: Int,
    val frames: Int,
)

/** Turns protocol frames into something `ConsumerIrManager.transmit` accepts. */
object IrFrames {

    /** Every Mitsubishi A/C protocol here uses a 38 kHz carrier. */
    const val CARRIER_FREQUENCY_HZ = 38_000

    /**
     * Alternating on/off durations in microseconds, starting with an "on" mark.
     * Bytes go out least significant bit first, which is what all of these
     * protocols expect.
     */
    fun toPattern(frame: IntArray, timing: IrTiming): IntArray {
        // Per frame: header pair + a mark/space pair per bit + footer pair.
        val perFrame = 2 + frame.size * 8 * 2 + 2
        val pattern = IntArray(perFrame * timing.frames)
        var i = 0
        repeat(timing.frames) {
            pattern[i++] = timing.headerMark
            pattern[i++] = timing.headerSpace
            for (byte in frame) {
                for (bit in 0 until 8) {
                    pattern[i++] = timing.bitMark
                    pattern[i++] = if ((byte shr bit) and 1 == 1) timing.oneSpace else timing.zeroSpace
                }
            }
            pattern[i++] = timing.footerMark
            pattern[i++] = timing.gap
        }
        return pattern
    }

    /** Low byte of the sum of the first [count] bytes — the usual checksum here. */
    fun sumChecksum(frame: IntArray, count: Int): Int =
        frame.take(count).sum() and 0xFF

    /** Hex dump, for comparing against a capture of a real handset. */
    fun hex(frame: IntArray): String = frame.joinToString(" ") { "%02X".format(it) }
}
