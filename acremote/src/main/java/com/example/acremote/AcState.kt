package com.example.acremote

/** Operating modes in the order the physical MODE button cycles through them. */
enum class AcMode(val protocolValue: Int) {
    COOLING(0b011),
    HEATING(0b001),
    DRY(0b010),
    FAN(0b111),
    AUTO(0b100),
    ;

    /** DRY and FAN run without a target temperature on these units. */
    val usesTemperature: Boolean get() = this == COOLING || this == HEATING || this == AUTO
}

/** Fan speeds in WIND SPEED button order: auto, four steps, then silent. */
enum class FanSpeed(val protocolValue: Int, val bars: Int) {
    AUTO(0, 0),
    LEVEL_1(1, 1),
    LEVEL_2(2, 2),
    LEVEL_3(3, 3),
    LEVEL_4(4, 4),
    SILENT(5, 0),
}

/**
 * Everything the remote holds. Mitsubishi units are stateless receivers: every
 * button press re-transmits the whole state, so this is what gets encoded each
 * time.
 */
data class AcState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.COOLING,
    val temperatureC: Int = 21,
    val fan: FanSpeed = FanSpeed.AUTO,
    val swing: Boolean = false,
) {
    fun withTemperature(value: Int) = copy(temperatureC = value.coerceIn(MIN_TEMP_C, MAX_TEMP_C))

    /**
     * Advances to the next mode the active code set can actually express —
     * cycling to a mode it cannot encode would leave the display claiming
     * something the A/C was never told.
     */
    fun nextMode(allowed: Set<AcMode>): AcState {
        if (allowed.isEmpty()) return this
        var next = mode
        do {
            next = AcMode.entries[(next.ordinal + 1) % AcMode.entries.size]
        } while (next !in allowed)
        return copy(mode = next)
    }

    fun nextFan() = copy(fan = FanSpeed.entries[(fan.ordinal + 1) % FanSpeed.entries.size])

    /** Pulls an unsupported mode onto one the given code set can send. */
    fun coerceModeTo(allowed: Set<AcMode>): AcState = when {
        mode in allowed -> this
        AcMode.AUTO in allowed -> copy(mode = AcMode.AUTO)
        else -> copy(mode = allowed.first())
    }

    companion object {
        const val MIN_TEMP_C = 16
        const val MAX_TEMP_C = 31
    }
}
