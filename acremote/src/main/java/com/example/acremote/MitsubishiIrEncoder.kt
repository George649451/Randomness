package com.example.acremote

/**
 * Encodes an [AcState] as a Mitsubishi Electric "144 bit" A/C frame (the
 * protocol used by the MSZ/MSC split units and their KM09A-style remotes) and
 * flattens it into the mark/space microsecond pattern that
 * `ConsumerIrManager.transmit` expects.
 *
 * Byte layout, following the protocol write-up in IRremoteESP8266's
 * `ir_Mitsubishi.cpp`:
 *
 * ```
 * 0..4   fixed vendor prologue
 * 5      power (bit 5)
 * 6      mode (bits 0..2) over a fixed 0x08
 * 7      target temperature, offset by 16 °C
 * 8      unused by this remote
 * 9      fan speed (bits 0..2), vane position (bits 3..5), vane-set flag (bit 6)
 * 10..16 timers and extras this remote does not drive
 * 17     checksum: low byte of the sum of bytes 0..16
 * ```
 *
 * The frame is sent twice, which is what the original remote does.
 */
object MitsubishiIrEncoder {

    const val CARRIER_FREQUENCY_HZ = 38_000
    const val STATE_BYTES = 18

    private const val HEADER_MARK = 3400
    private const val HEADER_SPACE = 1750
    private const val BIT_MARK = 450
    private const val ONE_SPACE = 1300
    private const val ZERO_SPACE = 420
    private const val FOOTER_MARK = 440
    private const val FOOTER_SPACE = 17100

    private const val FRAME_REPEATS = 2

    private const val POWER_ON = 0x20
    private const val MODE_BASE = 0x08
    private const val TEMP_OFFSET = 16
    private const val VANE_SET = 0x40
    private const val VANE_AUTO = 0
    private const val VANE_SWING = 7

    /** Vendor prologue: bytes 0..4 are identical in every frame. */
    private val PROLOGUE = intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00)

    /** The 18 state bytes for [state], checksum included. */
    fun encodeState(state: AcState): IntArray {
        val frame = IntArray(STATE_BYTES)
        PROLOGUE.copyInto(frame)
        frame[5] = if (state.power) POWER_ON else 0x00
        frame[6] = MODE_BASE or state.mode.protocolValue
        frame[7] = state.temperatureC.coerceIn(AcState.MIN_TEMP_C, AcState.MAX_TEMP_C) - TEMP_OFFSET
        val vane = if (state.swing) VANE_SWING else VANE_AUTO
        frame[9] = state.fan.protocolValue or (vane shl 3) or VANE_SET
        frame[STATE_BYTES - 1] = checksum(frame)
        return frame
    }

    /** Low byte of the sum of every byte before the checksum itself. */
    fun checksum(frame: IntArray): Int =
        frame.take(STATE_BYTES - 1).sum() and 0xFF

    /**
     * Alternating on/off durations in microseconds, starting with an "on"
     * mark — the shape `ConsumerIrManager.transmit` takes.
     */
    fun toPattern(frame: IntArray): IntArray {
        // Per frame: header (2) + 8 bits per byte as mark/space pairs + footer (2).
        val perFrame = 2 + frame.size * 8 * 2 + 2
        val pattern = IntArray(perFrame * FRAME_REPEATS)
        var i = 0
        repeat(FRAME_REPEATS) {
            pattern[i++] = HEADER_MARK
            pattern[i++] = HEADER_SPACE
            for (byte in frame) {
                // Least significant bit first.
                for (bit in 0 until 8) {
                    pattern[i++] = BIT_MARK
                    pattern[i++] = if ((byte shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE
                }
            }
            pattern[i++] = FOOTER_MARK
            pattern[i++] = FOOTER_SPACE
        }
        return pattern
    }

    fun toPattern(state: AcState): IntArray = toPattern(encodeState(state))

    /** Hex dump of a frame, handy in logcat when comparing against a real remote. */
    fun toHex(frame: IntArray): String =
        frame.joinToString(" ") { "%02X".format(it) }
}
