package com.example.noteguess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * One octave of piano keys (C..B) drawn with Canvas. Reports taps through
 * [onKeyPressed] as a semitone index 0..11 relative to C.
 */
class PianoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onKeyPressed: ((Int) -> Unit)? = null

    private val whiteSemitones = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    // Black key semitone keyed by the white key index on its left (no black
    // key after Mi/E and Si/B).
    private val blackSemitones = mapOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)

    private val whiteSolfege = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    private val whiteLetters = listOf("C", "D", "E", "F", "G", "A", "B")
    private val blackSolfege = mapOf(1 to "Do♯", 3 to "Re♯", 6 to "Fa♯", 8 to "Sol♯", 10 to "La♯")

    private var pressedNote = -1

    private val whitePaint = Paint().apply { color = Color.WHITE }
    private val blackPaint = Paint().apply { color = Color.rgb(30, 30, 34) }
    private val pressedPaint = Paint().apply { color = Color.rgb(140, 152, 224) }
    private val borderPaint = Paint().apply {
        color = Color.rgb(60, 60, 66)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        isAntiAlias = true
    }
    private val solfegePaint = Paint().apply {
        color = Color.rgb(40, 40, 46)
        textSize = sp(15f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    private val letterPaint = Paint().apply {
        color = Color.rgb(130, 130, 140)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val blackLabelPaint = Paint().apply {
        color = Color.rgb(225, 225, 232)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val whiteKeyWidth get() = width / 7f
    private val blackKeyHeight get() = height * 0.58f

    private fun blackKeyRect(leftWhiteIndex: Int): RectF {
        val center = (leftWhiteIndex + 1) * whiteKeyWidth
        val halfWidth = whiteKeyWidth * 0.32f
        return RectF(center - halfWidth, 0f, center + halfWidth, blackKeyHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0..6) {
            val rect = RectF(i * whiteKeyWidth, 0f, (i + 1) * whiteKeyWidth, height.toFloat())
            val fill = if (whiteSemitones[i] == pressedNote) pressedPaint else whitePaint
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, borderPaint)
            val centerX = rect.centerX()
            canvas.drawText(whiteSolfege[i], centerX, height - dp(26f), solfegePaint)
            canvas.drawText(whiteLetters[i], centerX, height - dp(10f), letterPaint)
        }

        for ((leftWhite, semitone) in blackSemitones) {
            val rect = blackKeyRect(leftWhite)
            val fill = if (semitone == pressedNote) pressedPaint else blackPaint
            canvas.drawRoundRect(rect, dp(3f), dp(3f), fill)
            canvas.drawText(blackSolfege.getValue(semitone), rect.centerX(), rect.bottom - dp(8f), blackLabelPaint)
        }

        if (!isEnabled) {
            canvas.drawColor(Color.argb(110, 128, 128, 136))
        }
    }

    private fun noteAt(x: Float, y: Float): Int? {
        if (y < blackKeyHeight) {
            for ((leftWhite, semitone) in blackSemitones) {
                if (blackKeyRect(leftWhite).contains(x, y)) return semitone
            }
        }
        val whiteIndex = (x / whiteKeyWidth).toInt().coerceIn(0, 6)
        return whiteSemitones[whiteIndex]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                val note = noteAt(event.x, event.y) ?: return false
                pressedNote = note
                invalidate()
                onKeyPressed?.invoke(note)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressedNote != -1) {
                    pressedNote = -1
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        invalidate()
    }
}
