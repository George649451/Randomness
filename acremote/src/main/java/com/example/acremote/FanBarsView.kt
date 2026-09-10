package com.example.acremote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

/**
 * The WIND SPEED indicator on the LCD: up to four bars of increasing height,
 * or a word ("AUTO", "SILENT") when the speed has no bar representation. Only
 * lit segments are drawn — unlit ones are invisible, like on the real display.
 */
class FanBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Number of lit bars, 0..4. Ignored while [text] is set. */
    var bars: Int = 0
        set(value) {
            field = value.coerceIn(0, BAR_HEIGHTS_DP.size)
            invalidate()
        }

    /** Word drawn instead of bars, or null to draw bars. */
    var text: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private val segmentPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.lcd_segment)
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.lcd_segment)
        textSize = sp(13f)
        isFakeBoldText = true
        isAntiAlias = true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bottom = height.toFloat()
        val left = dp(2f)

        val word = text
        if (word != null) {
            canvas.drawText(word, left, bottom, textPaint)
            return
        }

        val barWidth = dp(5f)
        val gap = dp(3f)
        for (index in 0 until bars) {
            val x = left + index * (barWidth + gap)
            canvas.drawRect(x, bottom - dp(BAR_HEIGHTS_DP[index]), x + barWidth, bottom, segmentPaint)
        }
    }

    private companion object {
        val BAR_HEIGHTS_DP = floatArrayOf(5f, 9f, 13f, 17f)
    }
}
