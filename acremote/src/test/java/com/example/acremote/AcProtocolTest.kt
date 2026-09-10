package com.example.acremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reference frames below are the known-good states shipped by
 * IRremoteESP8266 (captured from real handsets), so reproducing them byte for
 * byte is the closest thing to hardware verification available here.
 */
class AcProtocolTest {

    @Test
    fun `144-bit reproduces the reference frame for heat 22 with silent fan`() {
        // 23 CB 26 01 00 20 08 06 30 45 67 ... — power on, heat, 22 C, silent,
        // vane auto. Byte 10 is the handset clock, which this remote leaves unset.
        val frame = AcProtocol.ELECTRIC_144.frame(
            AcState(
                power = true,
                mode = AcMode.HEATING,
                temperatureC = 22,
                fan = FanSpeed.SILENT,
                swing = false,
            ),
        )
        assertArrayEquals(
            intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00, 0x20, 0x08, 0x06, 0x30, 0x45),
            frame.take(10).toIntArray(),
        )
        assertEquals(0, frame[10]) // clock not set
        assertEquals(18, frame.size)
    }

    @Test
    fun `144-bit puts the mode in bits 3 to 5, not the low bits`() {
        fun byte6(mode: AcMode) = AcProtocol.ELECTRIC_144.frame(AcState(mode = mode))[6]
        assertEquals(0b011 shl 3, byte6(AcMode.COOLING))
        assertEquals(0b001 shl 3, byte6(AcMode.HEATING))
        assertEquals(0b010 shl 3, byte6(AcMode.DRY))
        assertEquals(0b111 shl 3, byte6(AcMode.FAN))
        assertEquals(0b100 shl 3, byte6(AcMode.AUTO))
    }

    @Test
    fun `144-bit byte 8 carries the vane position and mode bits`() {
        fun byte8(mode: AcMode) = AcProtocol.ELECTRIC_144.frame(AcState(mode = mode))[8]
        assertEquals(0x36, byte8(AcMode.COOLING))
        assertEquals(0x32, byte8(AcMode.DRY))
        assertEquals(0x37, byte8(AcMode.FAN))
        assertEquals(0x30, byte8(AcMode.HEATING))
        assertEquals(0x30, byte8(AcMode.AUTO))
    }

    @Test
    fun `144-bit fan auto sets the auto bit rather than speed zero`() {
        fun byte9(fan: FanSpeed) = AcProtocol.ELECTRIC_144.frame(AcState(fan = fan))[9]
        assertEquals(0x80 or 0x40, byte9(FanSpeed.AUTO))
        assertEquals(0x40 or 1, byte9(FanSpeed.LEVEL_1))
        assertEquals(0x40 or 4, byte9(FanSpeed.LEVEL_4))
        assertEquals(0x40 or 5, byte9(FanSpeed.SILENT))
    }

    @Test
    fun `144-bit swing selects the sweeping vane`() {
        val frame = AcProtocol.ELECTRIC_144.frame(
            AcState(swing = true, fan = FanSpeed.LEVEL_2),
        )
        assertEquals(0x40 or (0b111 shl 3) or 2, frame[9])
    }

    @Test
    fun `144-bit power off clears the power byte`() {
        assertEquals(0x00, AcProtocol.ELECTRIC_144.frame(AcState(power = false))[5])
        assertEquals(0x20, AcProtocol.ELECTRIC_144.frame(AcState(power = true))[5])
    }

    @Test
    fun `144-bit temperature is offset by 16 and clamped`() {
        assertEquals(5, AcProtocol.ELECTRIC_144.frame(AcState(temperatureC = 21))[7])
        assertEquals(0, AcProtocol.ELECTRIC_144.frame(AcState(temperatureC = 4))[7])
        assertEquals(15, AcProtocol.ELECTRIC_144.frame(AcState(temperatureC = 40))[7])
    }

    @Test
    fun `136-bit reproduces the reference frame and its inverted tail`() {
        // 23 CB 26 21 00 40 C2 C7 04 — power on, heat, 28 C, fan max, vane auto.
        val frame = AcProtocol.ELECTRIC_136.frame(
            AcState(
                power = true,
                mode = AcMode.HEATING,
                temperatureC = 28,
                fan = FanSpeed.LEVEL_4,
                swing = true,
            ),
        )
        assertEquals(17, frame.size)
        assertArrayEquals(
            intArrayOf(0x23, 0xCB, 0x26, 0x21, 0x00, 0x40, 0xC2, 0xC7, 0x04),
            frame.take(9).toIntArray(),
        )
        for (i in 0 until 6) {
            assertEquals("byte ${11 + i}", frame[5 + i].inv() and 0xFF, frame[11 + i])
        }
    }

    @Test
    fun `112-bit reproduces the reference frame`() {
        // 23 CB 26 01 00 24 03 0B 10 00 00 00 30 — power on, cool, 20 C, fan max.
        val frame = AcProtocol.ELECTRIC_112.frame(
            AcState(
                power = true,
                mode = AcMode.COOLING,
                temperatureC = 20,
                fan = FanSpeed.LEVEL_4,
                swing = false,
            ),
        )
        assertEquals(14, frame.size)
        assertArrayEquals(
            intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00, 0x24, 0x03, 0x0B, 0x10, 0x00, 0x00, 0x00, 0x30),
            frame.take(13).toIntArray(),
        )
        assertEquals(IrFrames.sumChecksum(frame, 13), frame[13])
    }

    @Test
    fun `112-bit fan auto falls back to medium, and silent equals the lowest step`() {
        fun byte8(fan: FanSpeed) = AcProtocol.ELECTRIC_112.frame(AcState(fan = fan))[8] and 0b111
        assertEquals(0b101, byte8(FanSpeed.AUTO)) // no auto value exists here
        assertEquals(0b101, byte8(FanSpeed.LEVEL_3))
        assertEquals(0b000, byte8(FanSpeed.LEVEL_4))
        assertEquals(0b010, byte8(FanSpeed.LEVEL_1))
        assertEquals(0b010, byte8(FanSpeed.SILENT))
    }

    @Test
    fun `112-bit has no fan-only mode, so the mode cycle skips it`() {
        val protocol = AcProtocol.ELECTRIC_112
        assertTrue(AcMode.FAN !in protocol.modes)

        // Cycling from dry lands on auto, never on fan.
        val cycled = AcState(mode = AcMode.DRY).nextMode(protocol.modes)
        assertEquals(AcMode.AUTO, cycled.mode)

        // Every mode reachable through the button is one this code set encodes.
        var state = AcState(mode = AcMode.COOLING)
        repeat(protocol.modes.size * 2) {
            state = state.nextMode(protocol.modes)
            assertTrue(state.mode.name, state.mode in protocol.modes)
        }
    }

    @Test
    fun `switching to a narrower code set pulls the mode onto a supported one`() {
        val fanOnly = AcState(mode = AcMode.FAN)
        assertEquals(AcMode.AUTO, fanOnly.coerceModeTo(AcProtocol.ELECTRIC_112.modes).mode)
        // The wider code sets leave it alone.
        assertEquals(AcMode.FAN, fanOnly.coerceModeTo(AcProtocol.ELECTRIC_144.modes).mode)
    }

    @Test
    fun `144-bit and 136-bit cover every mode`() {
        for (protocol in listOf(AcProtocol.ELECTRIC_144, AcProtocol.ELECTRIC_136)) {
            assertEquals(protocol.name, AcMode.entries.toSet(), protocol.modes)
        }
    }

    @Test
    fun `112-bit temperature counts downwards`() {
        assertEquals(31 - 21, AcProtocol.ELECTRIC_112.frame(AcState(temperatureC = 21))[7])
    }

    @Test
    fun `sum-checksummed code sets end with the low byte of the preceding bytes`() {
        for (protocol in listOf(AcProtocol.ELECTRIC_144, AcProtocol.ELECTRIC_112)) {
            val frame = protocol.frame(AcState())
            val last = frame.size - 1
            assertEquals(protocol.name, IrFrames.sumChecksum(frame, last), frame[last])
        }
    }

    @Test
    fun `144-bit pattern repeats twice with the documented gap`() {
        val protocol = AcProtocol.ELECTRIC_144
        val pattern = protocol.pattern(AcState())
        assertEquals(2 * (2 + 18 * 8 * 2 + 2), pattern.size)
        assertEquals(3400, pattern[0])
        assertEquals(1750, pattern[1])
        assertEquals(440, pattern[pattern.size - 2])
        assertEquals(15500, pattern[pattern.size - 1])
        assertEquals(3400, pattern[pattern.size / 2]) // second copy of the frame
    }

    @Test
    fun `bits go out least significant first`() {
        val pattern = AcProtocol.ELECTRIC_144.pattern(AcState())
        // First byte is 0x23 = 0b00100011, so the first eight spaces read
        // 1, 1, 0, 0, 0, 1, 0, 0.
        val spaces = (0 until 8).map { pattern[2 + it * 2 + 1] }
        assertEquals(listOf(1300, 1300, 420, 420, 420, 1300, 420, 420), spaces)
    }

    @Test
    fun `every code set emits bytes within range and a plausible duration`() {
        for (protocol in AcProtocol.entries) {
            val frame = protocol.frame(AcState())
            assertTrue(protocol.name, frame.all { it in 0..0xFF })
            // ConsumerIrManager drops patterns of two seconds or more.
            val micros = protocol.pattern(AcState()).sum()
            assertTrue("${protocol.name} lasts $micros us", micros < 2_000_000)
        }
    }

    @Test
    fun `code sets survive a round trip through preferences`() {
        for (protocol in AcProtocol.entries) {
            assertEquals(protocol, AcProtocol.byName(protocol.name))
        }
        assertEquals(AcProtocol.DEFAULT, AcProtocol.byName(null))
        assertEquals(AcProtocol.DEFAULT, AcProtocol.byName("ELECTRIC_999"))
    }
}
