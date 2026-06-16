package com.bionicmusic.ui.library

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vertical A–Z alphabet index drawn on the right edge of the library list.
 * Dragging selects a letter and reports it through [onLetterSelected].
 */
class FastScrollIndex @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val letters = ('A'..'Z').toList() + '#'
    private var highlighted = -1

    var onLetterSelected: ((Char) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (height == 0) return
        val cellHeight = height.toFloat() / letters.size
        paint.textSize = cellHeight.coerceAtMost(28f) * 0.7f
        val cx = width / 2f
        for (i in letters.indices) {
            paint.color = if (i == highlighted) Color.WHITE else Color.parseColor("#888888")
            val cy = cellHeight * i + cellHeight / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letters[i].toString(), cx, cy, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = (event.y / height * letters.size).toInt()
                    .coerceIn(0, letters.size - 1)
                if (index != highlighted) {
                    highlighted = index
                    onLetterSelected?.invoke(letters[index])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                highlighted = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
