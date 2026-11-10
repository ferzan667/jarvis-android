package com.razen.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Lightweight animated system display; the actual microphone level comes from the detector. */
class JarvisHudView(context: Context) : View(context) {
    private val cyan = Color.rgb(70, 225, 241)
    private val dim = Color.rgb(34, 73, 91)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var level = 0
    private var live = false
    private var running = false

    fun update(microphoneLevel: Int, liveSession: Boolean, isRunning: Boolean) {
        level = microphoneLevel.coerceIn(0, 100)
        live = liveSession
        running = isRunning
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.32f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = dim
        canvas.drawCircle(cx, cy, r * 1.25f, paint)
        canvas.drawCircle(cx, cy, r * 0.91f, paint)
        paint.color = cyan
        paint.strokeWidth = dp(3f)
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        for (i in 0..3) canvas.drawArc(rect, phase + i * 90f, 58f, false, paint)
        paint.strokeWidth = dp(1f)
        val inner = RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f)
        for (i in 0..5) canvas.drawArc(inner, -phase * 0.7f + i * 60f, 37f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (running) cyan else dim
        canvas.drawCircle(cx, cy, r * (0.21f + level / 800f), paint)
        paint.color = Color.rgb(6, 13, 25)
        canvas.drawCircle(cx, cy, r * 0.13f, paint)
        paint.color = cyan
        paint.strokeWidth = dp(2f)
        for (i in 0 until 36) {
            val angle = Math.toRadians((i * 10.0 + phase * 0.3))
            val inside = r * 1.12f
            val outside = inside + dp(if (i % 3 == 0) 10f else 5f)
            canvas.drawLine(
                cx + cos(angle).toFloat() * inside,
                cy + sin(angle).toFloat() * inside,
                cx + cos(angle).toFloat() * outside,
                cy + sin(angle).toFloat() * outside,
                paint
            )
        }
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(12f)
        paint.color = cyan
        canvas.drawText(if (live) "GEMINI LIVE" else if (running) "LISTENING" else "STANDBY",
            cx, cy + r * 1.53f, paint)
        if (running) {
            phase = (phase + 1.8f) % 360f
            postInvalidateDelayed(33)
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

