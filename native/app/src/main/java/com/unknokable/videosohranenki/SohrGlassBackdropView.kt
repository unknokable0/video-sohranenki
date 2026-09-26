package com.unknokable.videosohranenki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class SohrGlassBackdropView(
    context: Context,
    private val palette: ThemePalette,
    animations: Boolean
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (animations) post { if (isAttachedToWindow) animator.start() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(palette.background)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val t = phase * (Math.PI * 2.0).toFloat()

        blob(canvas,
            w * (.12f + .035f * sin(t)),
            h * (.13f + .025f * sin(t * .7f)),
            w * .58f,
            withAlpha(palette.accent, if (palette === AppThemes.Light) 42 else 48)
        )
        blob(canvas,
            w * (.92f + .035f * sin(t + 1.8f)),
            h * (.42f + .035f * sin(t * .8f + 1.3f)),
            w * .52f,
            withAlpha(palette.accent, if (palette === AppThemes.Light) 30 else 38)
        )
        blob(canvas,
            w * (.28f + .05f * sin(t * .65f + 2.1f)),
            h * (.92f + .025f * sin(t + .6f)),
            w * .70f,
            withAlpha(palette.accentSoft, if (palette === AppThemes.Light) 55 else 65)
        )
    }

    private fun blob(canvas:Canvas,x:Float,y:Float,r:Float,color:Int){
        paint.shader = RadialGradient(x,y,r,intArrayOf(color,withAlpha(color,0)),floatArrayOf(0f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(x,y,r,paint)
        paint.shader=null
    }

    private fun withAlpha(color:Int,a:Int)=android.graphics.Color.argb(a.coerceIn(0,255),android.graphics.Color.red(color),android.graphics.Color.green(color),android.graphics.Color.blue(color))
}
