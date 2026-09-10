package com.example.acremote

/**
 * The A/C code sets this remote can send.
 *
 * "Mitsubishi" is a brand, not an encoding: Mitsubishi Electric alone ships at
 * least three incompatible A/C protocols, and Mitsubishi Heavy Industries — a
 * different company behind the same word on the box — ships two more. Header
 * timings, frame length and checksum all differ, so a unit simply ignores a
 * frame from the wrong family. Which one a given indoor unit speaks cannot be
 * derived from the model name, so the remote lets you try them.
 *
 * Byte layouts follow the protocol write-ups in IRremoteESP8266's
 * `ir_Mitsubishi.cpp` (bit fields there are allocated from the least
 * significant bit up). Each code set below reproduces that project's
 * known-good reference frame byte for byte — see `AcProtocolTest`.
 */
enum class AcProtocol(
    val shortLabel: String,
    val description: String,
    val timing: IrTiming,
    /** Modes this code set can encode; the MODE button cycles only these. */
    val modes: Set<AcMode> = AcMode.entries.toSet(),
) {
    /** Mitsubishi Electric, 18-byte frame. The MSZ/MSC split units. */
    ELECTRIC_144("144-bit", "Mitsubishi Electric · 18-byte frame (MSZ, MSC)", TIMING_144) {
        override fun frame(state: AcState): IntArray {
            val frame = IntArray(18)
            PROLOGUE_144.copyInto(frame)
            if (state.power) frame[5] = 0x20 // bit 5
            val mode = when (state.mode) {
                AcMode.COOLING -> 0b011
                AcMode.HEATING -> 0b001
                AcMode.DRY -> 0b010
                AcMode.FAN -> 0b111
                AcMode.AUTO -> 0b100
            }
            frame[6] = mode shl 3 // the mode field sits in bits 3..5
            frame[7] = state.temperatureC.coerceIn(16, 31) - 16 // low nibble
            // High nibble is the horizontal vane (3 = middle); the low bits are
            // mode-dependent and a zero byte here is not a valid combination.
            frame[8] = when (state.mode) {
                AcMode.COOLING -> 0x36
                AcMode.DRY -> 0x32
                AcMode.FAN -> 0x37
                AcMode.HEATING, AcMode.AUTO -> 0x30
            }
            val fan = when (state.fan) {
                FanSpeed.AUTO -> 0 // carried by the separate auto bit below
                FanSpeed.SILENT -> 5
                else -> state.fan.bars // 1..4
            }
            val vane = if (state.swing) 0b111 else 0b000
            frame[9] = fan or (vane shl 3) or VANE_SET_144 or
                if (state.fan == FanSpeed.AUTO) FAN_AUTO_144 else 0
            // Bytes 10..16 hold the clock and timers, which this remote leaves unset.
            frame[17] = IrFrames.sumChecksum(frame, 17)
            return frame
        }
    },

    /** Mitsubishi Electric, 17-byte frame; the tail is an inverted copy. */
    ELECTRIC_136("136-bit", "Mitsubishi Electric · 17-byte frame (MSZ-FH, GE)", TIMING_136) {
        override fun frame(state: AcState): IntArray {
            val frame = IntArray(17)
            PROLOGUE_136.copyInto(frame)
            if (state.power) frame[5] = 0x40 // bit 6
            val mode = when (state.mode) {
                AcMode.COOLING -> 0b001
                AcMode.HEATING -> 0b010
                AcMode.DRY -> 0b101
                AcMode.FAN -> 0b000
                AcMode.AUTO -> 0b011
            }
            frame[6] = mode or ((state.temperatureC.coerceIn(17, 30) - 16) shl 4)
            // Only four fan steps and no "auto", so auto maps onto maximum.
            val fan = when (state.fan) {
                FanSpeed.AUTO, FanSpeed.LEVEL_4 -> 0b11
                FanSpeed.LEVEL_3 -> 0b10
                FanSpeed.LEVEL_2 -> 0b01
                FanSpeed.LEVEL_1, FanSpeed.SILENT -> 0b00
            }
            val swingV = if (state.swing) 0b1100 else 0b0011
            frame[7] = 0x01 or (fan shl 1) or (swingV shl 4)
            frame[8] = 0x04
            // Bytes 11..16 are bytes 5..10 inverted — this protocol's checksum.
            for (i in 0 until 6) frame[11 + i] = frame[5 + i].inv() and 0xFF
            return frame
        }
    },

    /** Mitsubishi Electric, 14-byte frame; shares its checksum lineage with TCL. */
    ELECTRIC_112(
        "112-bit",
        "Mitsubishi Electric · 14-byte frame (MSH, SEZ)",
        TIMING_112,
        // This code set has no fan-only mode at all.
        modes = setOf(AcMode.COOLING, AcMode.HEATING, AcMode.DRY, AcMode.AUTO),
    ) {
        override fun frame(state: AcState): IntArray {
            val frame = IntArray(14)
            PROLOGUE_112.copyInto(frame)
            frame[5] = 0x20 or if (state.power) 0x04 else 0 // power is bit 2
            frame[6] = when (state.mode) {
                AcMode.COOLING -> 0b011
                AcMode.HEATING -> 0b001
                AcMode.DRY -> 0b010
                // No fan-only mode here, which is why [modes] excludes it.
                AcMode.FAN, AcMode.AUTO -> 0b111
            }
            frame[7] = 31 - state.temperatureC.coerceIn(16, 31) // counts downwards
            // No auto value is known for this code set, so auto sends medium —
            // what the reference implementation falls back to. "Silent" and the
            // lowest step are one and the same frame here.
            val fan = when (state.fan) {
                FanSpeed.LEVEL_4 -> 0b000
                FanSpeed.AUTO, FanSpeed.LEVEL_3 -> 0b101
                FanSpeed.LEVEL_2 -> 0b011
                FanSpeed.LEVEL_1, FanSpeed.SILENT -> 0b010
            }
            val swingV = if (state.swing) 0b111 else 0b010
            frame[8] = fan or (swingV shl 3)
            frame[12] = 0x30 // horizontal vane: auto
            frame[13] = IrFrames.sumChecksum(frame, 13)
            return frame
        }
    },
    ;

    /** The state bytes this code set would send for [state], checksum included. */
    abstract fun frame(state: AcState): IntArray

    fun pattern(state: AcState): IntArray = IrFrames.toPattern(frame(state), timing)

    companion object {
        /** The one confirmed working against a real indoor unit. */
        val DEFAULT = ELECTRIC_112

        fun byName(name: String?): AcProtocol =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val TIMING_144 =
    IrTiming(3400, 1750, 450, 1300, 420, 440, gap = 15500, frames = 2)
private val TIMING_136 =
    IrTiming(3324, 1474, 467, 1137, 351, 467, gap = 20000, frames = 1)
private val TIMING_112 =
    IrTiming(3450, 1696, 450, 1250, 385, 450, gap = 20000, frames = 1)

private val PROLOGUE_144 = intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00)
private val PROLOGUE_136 = intArrayOf(0x23, 0xCB, 0x26, 0x21, 0x00)
private val PROLOGUE_112 = intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00)

private const val VANE_SET_144 = 0x40
private const val FAN_AUTO_144 = 0x80
