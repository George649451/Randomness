package com.example.acremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

/** Result of trying to put a frame on the air. */
enum class SendResult { SENT, NO_EMITTER, FAILED }

/**
 * Thin wrapper over [ConsumerIrManager]. Phones without an IR emitter report
 * [SendResult.NO_EMITTER] instead of throwing, so the UI stays usable as a
 * mock-up on hardware that cannot transmit.
 */
class IrTransmitter(context: Context) {

    private val manager =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean get() = manager?.hasIrEmitter() == true

    /** True when the emitter advertises support for the 38 kHz A/C carrier. */
    val supportsCarrier: Boolean
        get() {
            val ranges = runCatching { manager?.carrierFrequencies }.getOrNull() ?: return hasEmitter
            return ranges.any {
                MitsubishiIrEncoder.CARRIER_FREQUENCY_HZ in it.minFrequency..it.maxFrequency
            }
        }

    fun send(state: AcState): SendResult {
        val ir = manager?.takeIf { it.hasIrEmitter() } ?: return SendResult.NO_EMITTER
        val frame = MitsubishiIrEncoder.encodeState(state)
        Log.d(TAG, "frame ${MitsubishiIrEncoder.toHex(frame)}")
        return try {
            ir.transmit(MitsubishiIrEncoder.CARRIER_FREQUENCY_HZ, MitsubishiIrEncoder.toPattern(frame))
            SendResult.SENT
        } catch (e: RuntimeException) {
            // Some vendors throw when the emitter is busy or the pattern is too long.
            Log.w(TAG, "transmit failed", e)
            SendResult.FAILED
        }
    }

    private companion object {
        const val TAG = "IrTransmitter"
    }
}
