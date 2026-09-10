package com.example.acremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/** Result of trying to put a frame on the air. */
enum class SendResult { SENT, NO_EMITTER, NO_PERMISSION, FAILED }

/**
 * Thin wrapper over [ConsumerIrManager]. Phones without an IR emitter report
 * [SendResult.NO_EMITTER] instead of throwing, so the UI stays usable as a
 * mock-up on hardware that cannot transmit.
 *
 * `transmit` blocks for as long as the pattern lasts, so it runs on a worker
 * thread and answers on the main one.
 */
class IrTransmitter(private val context: Context) {

    private val manager =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val hasEmitter: Boolean get() = manager?.hasIrEmitter() == true

    /**
     * True when the emitter advertises support for the 38 kHz A/C carrier.
     * Reading the frequency list needs the same TRANSMIT_IR permission as
     * transmitting, so a refusal here is reported as "unknown", not "no".
     */
    val supportsCarrier: Boolean
        get() {
            val ranges = carrierRanges() ?: return hasEmitter
            return ranges.any {
                IrFrames.CARRIER_FREQUENCY_HZ in it.minFrequency..it.maxFrequency
            }
        }

    private fun carrierRanges(): Array<ConsumerIrManager.CarrierFrequencyRange>? =
        runCatching { manager?.carrierFrequencies }.getOrNull()

    fun send(state: AcState, protocol: AcProtocol, onResult: (SendResult) -> Unit) {
        val ir = manager?.takeIf { it.hasIrEmitter() }
        if (ir == null) {
            onResult(SendResult.NO_EMITTER)
            return
        }
        val frame = protocol.frame(state)
        Log.d(TAG, "${protocol.shortLabel} frame ${IrFrames.hex(frame)}")
        val pattern = IrFrames.toPattern(frame, protocol.timing)
        worker.execute {
            val result = try {
                ir.transmit(IrFrames.CARRIER_FREQUENCY_HZ, pattern)
                SendResult.SENT
            } catch (e: SecurityException) {
                // Missing android.permission.TRANSMIT_IR — nothing reaches the LED.
                Log.w(TAG, "transmit denied", e)
                SendResult.NO_PERMISSION
            } catch (e: RuntimeException) {
                // Some vendors throw when the emitter is busy or the pattern is too long.
                Log.w(TAG, "transmit failed", e)
                SendResult.FAILED
            }
            main.post { onResult(result) }
        }
    }

    /**
     * Answers "did the LED actually fire?".
     *
     * `transmit` is documented as synchronous — it returns once the pattern has
     * been transmitted — so a call that comes back far sooner than the pattern
     * lasts means the vendor's HAL dropped it on the floor without complaining.
     * Several variations get probed, because some HALs reject long patterns or
     * ones that end on a gap.
     */
    fun selfTest(state: AcState, protocol: AcProtocol, onReport: (String) -> Unit) {
        worker.execute {
            val report = buildReport(state, protocol)
            main.post { onReport(report) }
        }
    }

    private fun buildReport(state: AcState, protocol: AcProtocol): String {
        val out = StringBuilder()
        out.append("${Build.MANUFACTURER} ${Build.MODEL}\n")
        out.append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        out.append("App ${appVersion()}\n\n")

        val ir = manager
        out.append("consumerir service: ${if (ir == null) "MISSING" else "present"}\n")
        if (ir == null) {
            out.append("\nThis phone exposes no consumer IR service at all. Any app that\n")
            out.append("does control IR here is using a vendor-private API instead.\n")
            return out.toString()
        }
        out.append("hasIrEmitter(): ${ir.hasIrEmitter()}\n")
        val ranges = carrierRanges()
        out.append("carrier ranges: ")
        out.append(
            when {
                ranges == null -> "unavailable (call refused)"
                ranges.isEmpty() -> "none reported"
                else -> ranges.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency} Hz" }
            },
        )
        out.append("\n\ncode set: ${protocol.shortLabel}\n")
        out.append("frame: ${IrFrames.hex(protocol.frame(state))}\n\n")

        for (probe in probes(state, protocol)) {
            val expectedMs = probe.pattern.sum() / 1000
            out.append("${probe.label}\n")
            out.append("  ${probe.pattern.size} slots, expect ${expectedMs} ms -> ")
            val started = System.nanoTime()
            val failure = try {
                ir.transmit(IrFrames.CARRIER_FREQUENCY_HZ, probe.pattern)
                null
            } catch (e: RuntimeException) {
                e
            }
            val actualMs = (System.nanoTime() - started) / 1_000_000
            when {
                failure != null ->
                    out.append("threw ${failure.javaClass.simpleName}: ${failure.message}\n")
                // A HAL that really emitted cannot return much before the
                // pattern has played out.
                actualMs < expectedMs * 6 / 10 ->
                    out.append("returned after $actualMs ms — DROPPED, nothing emitted\n")
                else -> out.append("returned after $actualMs ms — emitted\n")
            }
            Thread.sleep(PROBE_GAP_MS) // let the emitter settle between probes
        }

        out.append("\nAll probes 'emitted' means the LED fired and the frame or\n")
        out.append("code set is wrong for the unit. Any 'DROPPED' or 'threw' means\n")
        out.append("the transmission never happened.\n")
        return out.toString()
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private class Probe(val label: String, val pattern: IntArray)

    /** Variations worth trying when a HAL is fussy about pattern shape. */
    private fun probes(state: AcState, protocol: AcProtocol): List<Probe> {
        val full = protocol.pattern(state)
        val probes = mutableListOf(Probe("1. as sent", full))
        if (protocol.timing.frames > 1) {
            probes += Probe("2. one frame only", full.copyOfRange(0, full.size / protocol.timing.frames))
        }
        // Ending on a mark rather than a trailing gap.
        probes += Probe("${probes.size + 1}. no trailing gap", full.copyOfRange(0, full.size - 1))
        return probes
    }

    private companion object {
        const val TAG = "IrTransmitter"
        const val PROBE_GAP_MS = 400L
    }
}
