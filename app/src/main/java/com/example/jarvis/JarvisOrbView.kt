package com.example.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated JARVIS floating orb. The ring brightens while explicit wake mode is enabled.
 */
class JarvisOrbView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private var phase = 0f
    private var wakeActive = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    fun setWakeActive(active: Boolean) {
        wakeActive = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.34f
        val pulse = (sin(phase * 2.0 * PI) + 1.0).toFloat() / 2f
        val lift = sin(phase * 2.0 * PI).toFloat() * 2.5f * density
        val centerY = cy + lift
        val accent = if (wakeActive) Color.rgb(40, 255, 210) else Color.rgb(60, 190, 255)

        // Soft outer glow.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, centerY, radius * 1.9f,
            intArrayOf(Color.argb(105, 0, 190, 255), Color.argb(0, 0, 90, 180)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, centerY, radius * 1.9f, paint)
        paint.shader = null

        // Slowly pulsing energy rings.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = Color.argb((85 + pulse * 110).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, centerY, radius * (1.22f + pulse * 0.10f), paint)

        paint.strokeWidth = 1.1f * density
        paint.color = Color.argb((65 + (1f - pulse) * 95).toInt(), 75, 210, 255)
        canvas.drawCircle(cx, centerY, radius * (1.43f - pulse * 0.08f), paint)

        // Tilted orbit ring.
        canvas.save()
        canvas.rotate(phase * 360f, cx, centerY)
        paint.strokeWidth = 2f * density
        paint.color = Color.argb(185, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawOval(
            cx - radius * 1.48f,
            centerY - radius * 0.48f,
            cx + radius * 1.48f,
            centerY + radius * 0.48f,
            paint
        )
        canvas.restore()

        // Main glass-like sphere.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - radius * 0.30f,
            centerY - radius * 0.38f,
            radius * 1.65f,
            intArrayOf(
                Color.rgb(175, 245, 255),
                Color.rgb(35, 155, 220),
                Color.rgb(7, 35, 82),
                Color.rgb(2, 10, 28)
            ),
            floatArrayOf(0f, 0.28f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, centerY, radius, paint)
        paint.shader = null

        // Bright inner core and reflective highlight.
        paint.shader = RadialGradient(
            cx - radius * 0.25f,
            centerY - radius * 0.28f,
            radius * 0.78f,
            intArrayOf(Color.argb(175, 160, 250, 255), Color.argb(0, 35, 180, 255)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, centerY, radius * 0.78f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = Color.argb(210, 150, 240, 255)
        canvas.drawCircle(cx, centerY, radius, paint)

        // JARVIS initial.
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.92f
        paint.color = Color.WHITE
        canvas.drawText("J", cx, centerY + radius * 0.32f, paint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
