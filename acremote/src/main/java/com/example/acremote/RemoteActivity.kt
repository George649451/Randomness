package com.example.acremote

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat

/**
 * The remote itself: an LCD panel that mirrors the state being transmitted and
 * the five controls of the original handset (power, mode, temperature rocker,
 * wind speed, swing). Every press re-sends the complete state over infrared.
 */
class RemoteActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var transmitter: IrTransmitter

    private lateinit var tempCell: LinearLayout
    private lateinit var modeCell: LinearLayout
    private lateinit var fanCell: LinearLayout
    private lateinit var tempValue: TextView
    private lateinit var modeIcon: ImageView
    private lateinit var modeLabel: TextView
    private lateinit var fanBars: FanBarsView
    private lateinit var powerButton: ImageButton
    private lateinit var swingButton: ImageButton
    private lateinit var tempUpButton: ImageButton
    private lateinit var tempDownButton: ImageButton
    private lateinit var statusText: TextView

    private var state = AcState()
    private var protocol = AcProtocol.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        transmitter = IrTransmitter(this)
        state = loadState()
        protocol = AcProtocol.byName(prefs.getString(KEY_PROTOCOL, null))

        tempCell = findViewById(R.id.temp_cell)
        modeCell = findViewById(R.id.mode_cell)
        fanCell = findViewById(R.id.fan_cell)
        tempValue = findViewById(R.id.temp_value)
        modeIcon = findViewById(R.id.mode_icon)
        modeLabel = findViewById(R.id.mode_label)
        fanBars = findViewById(R.id.fan_bars)
        powerButton = findViewById(R.id.power_button)
        swingButton = findViewById(R.id.swing_button)
        tempUpButton = findViewById(R.id.temp_up_button)
        tempDownButton = findViewById(R.id.temp_down_button)
        statusText = findViewById(R.id.status_text)

        powerButton.setOnClickListener { press(it) { state.copy(power = !state.power) } }
        findViewById<ImageButton>(R.id.mode_button).setOnClickListener { press(it) { state.nextMode() } }
        findViewById<ImageButton>(R.id.fan_button).setOnClickListener { press(it) { state.nextFan() } }
        swingButton.setOnClickListener { press(it) { state.copy(swing = !state.swing) } }
        tempUpButton.setOnClickListener {
            press(it) { state.withTemperature(state.temperatureC + 1) }
        }
        tempDownButton.setOnClickListener {
            press(it) { state.withTemperature(state.temperatureC - 1) }
        }

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.help_button).setOnClickListener { showHelp() }

        render()
        // Say up front when the phone cannot transmit, rather than on first press.
        statusText.text = when {
            !transmitter.hasEmitter -> getString(R.string.status_no_emitter)
            !transmitter.supportsCarrier -> getString(R.string.status_no_carrier)
            else -> ""
        }
    }

    /** Common button handling: haptic tick, apply [change], redraw, transmit. */
    private fun press(view: View, change: () -> AcState) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        state = change()
        saveState()
        render()
        transmit()
    }

    private fun transmit() {
        statusText.text = when (transmitter.send(state, protocol)) {
            SendResult.SENT -> getString(R.string.status_sent, summary(), protocol.shortLabel)
            SendResult.NO_EMITTER -> getString(R.string.status_no_emitter)
            SendResult.NO_PERMISSION -> getString(R.string.status_no_permission)
            SendResult.FAILED -> getString(R.string.status_failed)
        }
    }

    /** Short human-readable version of what just went out on the air. */
    private fun summary(): String {
        if (!state.power) return getString(R.string.state_off)
        val parts = mutableListOf(getString(modeLabelRes(state.mode)))
        if (state.mode.usesTemperature) parts += "${state.temperatureC} ${getString(R.string.celsius)}"
        parts += getString(fanLabelRes(state.fan))
        if (state.swing) parts += getString(R.string.swing)
        return parts.joinToString(" · ")
    }

    private fun render() {
        val on = state.power
        val displayAlpha = if (on) 1f else 0f
        tempCell.alpha = displayAlpha
        modeCell.alpha = displayAlpha
        fanCell.alpha = displayAlpha

        tempValue.text =
            if (state.mode.usesTemperature) state.temperatureC.toString()
            else getString(R.string.temp_blank)

        modeIcon.setImageResource(modeIconRes(state.mode))
        modeLabel.setText(modeLabelRes(state.mode))

        when (state.fan) {
            FanSpeed.AUTO -> fanBars.text = getString(R.string.fan_auto)
            FanSpeed.SILENT -> fanBars.text = getString(R.string.fan_silent)
            else -> {
                fanBars.text = null
                fanBars.bars = state.fan.bars
            }
        }

        tint(powerButton, active = on)
        tint(swingButton, active = state.swing && on)

        // The rocker is dead in DRY and FAN, just like on the handset.
        val tempEnabled = on && state.mode.usesTemperature
        for (button in listOf(tempUpButton, tempDownButton)) {
            button.isEnabled = tempEnabled
            button.alpha = if (tempEnabled) 1f else 0.35f
        }
    }

    private fun tint(button: ImageButton, active: Boolean) {
        val color = if (active) R.color.button_icon_active else R.color.button_icon
        ImageViewCompat.setImageTintList(
            button,
            ColorStateList.valueOf(ContextCompat.getColor(this, color)),
        )
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.help_title)
            .setMessage("${getString(R.string.help_body)}\n\n${diagnostics()}")
            .setPositiveButton(R.string.help_close, null)
            .setNeutralButton(R.string.help_code_set) { _, _ -> showCodeSetChooser() }
            .show()
    }

    /** What the app is sending and whether the hardware can send it. */
    private fun diagnostics(): String {
        val emitter = when {
            !transmitter.hasEmitter -> getString(R.string.emitter_missing)
            transmitter.supportsCarrier -> getString(R.string.emitter_ready)
            else -> getString(R.string.emitter_unknown)
        }
        return getString(
            R.string.diagnostics,
            protocol.description,
            emitter,
            IrFrames.hex(protocol.frame(state)),
        )
    }

    /**
     * Lets the user hunt for the encoding their unit speaks — it cannot be
     * derived from the model, so trying is the only way.
     */
    private fun showCodeSetChooser() {
        val options = AcProtocol.entries.map { it.description }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.code_set_title)
            .setSingleChoiceItems(options, protocol.ordinal) { dialog, which ->
                protocol = AcProtocol.entries[which]
                saveState()
                dialog.dismiss()
                transmit()
            }
            .show()
    }

    private fun modeIconRes(mode: AcMode) = when (mode) {
        AcMode.COOLING -> R.drawable.ic_snowflake
        AcMode.HEATING -> R.drawable.ic_heat
        AcMode.DRY -> R.drawable.ic_dry
        AcMode.FAN -> R.drawable.ic_wind
        AcMode.AUTO -> R.drawable.ic_auto
    }

    private fun modeLabelRes(mode: AcMode) = when (mode) {
        AcMode.COOLING -> R.string.mode_cooling
        AcMode.HEATING -> R.string.mode_heating
        AcMode.DRY -> R.string.mode_dry
        AcMode.FAN -> R.string.mode_fan
        AcMode.AUTO -> R.string.mode_auto
    }

    private fun fanLabelRes(fan: FanSpeed) = when (fan) {
        FanSpeed.AUTO -> R.string.fan_auto
        FanSpeed.SILENT -> R.string.fan_silent
        else -> R.string.wind_speed
    }

    private fun loadState(): AcState {
        val defaults = AcState()
        return AcState(
            power = prefs.getBoolean(KEY_POWER, defaults.power),
            mode = AcMode.entries.getOrElse(prefs.getInt(KEY_MODE, defaults.mode.ordinal)) {
                defaults.mode
            },
            fan = FanSpeed.entries.getOrElse(prefs.getInt(KEY_FAN, defaults.fan.ordinal)) {
                defaults.fan
            },
            swing = prefs.getBoolean(KEY_SWING, defaults.swing),
        ).withTemperature(prefs.getInt(KEY_TEMP, defaults.temperatureC))
    }

    private fun saveState() {
        prefs.edit()
            .putBoolean(KEY_POWER, state.power)
            .putInt(KEY_MODE, state.mode.ordinal)
            .putInt(KEY_TEMP, state.temperatureC)
            .putInt(KEY_FAN, state.fan.ordinal)
            .putBoolean(KEY_SWING, state.swing)
            .putString(KEY_PROTOCOL, protocol.name)
            .apply()
    }

    private companion object {
        const val PREFS = "ac_remote"
        const val KEY_POWER = "power"
        const val KEY_MODE = "mode"
        const val KEY_TEMP = "temperature"
        const val KEY_FAN = "fan"
        const val KEY_SWING = "swing"
        const val KEY_PROTOCOL = "protocol"
    }
}
