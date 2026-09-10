package com.example.acremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MitsubishiIrEncoderTest {

    @Test
    fun `frame is 18 bytes starting with the vendor prologue`() {
        val frame = MitsubishiIrEncoder.encodeState(AcState())
        assertEquals(18, frame.size)
        assertArrayEquals(intArrayOf(0x23, 0xCB, 0x26, 0x01, 0x00), frame.take(5).toIntArray())
    }

    @Test
    fun `cooling at 21 degrees with auto fan encodes the documented bytes`() {
        val frame = MitsubishiIrEncoder.encodeState(
            AcState(power = true, mode = AcMode.COOLING, temperatureC = 21, fan = FanSpeed.AUTO),
        )
        assertEquals(0x20, frame[5]) // power on
        assertEquals(0x0B, frame[6]) // 0x08 base | cool (0b011)
        assertEquals(5, frame[7]) // 21 °C - 16 °C offset
        assertEquals(0x40, frame[9]) // auto fan, auto vane, vane-set flag
    }

    @Test
    fun `power off clears the power byte`() {
        val frame = MitsubishiIrEncoder.encodeState(AcState(power = false))
        assertEquals(0x00, frame[5])
    }

    @Test
    fun `swing selects the auto-move vane`() {
        val frame = MitsubishiIrEncoder.encodeState(AcState(swing = true, fan = FanSpeed.LEVEL_2))
        assertEquals(0x40 or (7 shl 3) or 2, frame[9])
    }

    @Test
    fun `temperature is clamped to the units range`() {
        assertEquals(0, MitsubishiIrEncoder.encodeState(AcState(temperatureC = 4))[7])
        assertEquals(15, MitsubishiIrEncoder.encodeState(AcState(temperatureC = 40))[7])
    }

    @Test
    fun `checksum is the low byte of every preceding byte`() {
        val frame = MitsubishiIrEncoder.encodeState(AcState())
        assertEquals(frame.take(17).sum() and 0xFF, frame[17])
    }

    @Test
    fun `pattern repeats the frame twice with header and footer`() {
        val pattern = MitsubishiIrEncoder.toPattern(AcState())
        // Two frames of: header pair + 144 mark/space pairs + footer pair.
        assertEquals(2 * (2 + 18 * 8 * 2 + 2), pattern.size)
        assertEquals(3400, pattern[0])
        assertEquals(1750, pattern[1])
        assertEquals(440, pattern[pattern.size - 2])
        assertEquals(17100, pattern[pattern.size - 1])
        assertEquals(3400, pattern[pattern.size / 2])
    }

    @Test
    fun `bits go out least significant first`() {
        val pattern = MitsubishiIrEncoder.toPattern(AcState())
        // First byte is 0x23 = 0b00100011, so the first eight spaces read
        // 1, 1, 0, 0, 0, 1, 0, 0.
        val spaces = (0 until 8).map { pattern[2 + it * 2 + 1] }
        assertEquals(listOf(1300, 1300, 420, 420, 420, 1300, 420, 420), spaces)
    }
}
