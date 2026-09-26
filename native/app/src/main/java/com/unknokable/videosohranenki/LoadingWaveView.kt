package com.unknokable.videosohranenki
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
class LoadingWaveView(context:Context,private val color:Int):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private var phase=0f
    private val animator=ValueAnimator.ofFloat(0f,1f).apply{duration=920L;repeatCount=ValueAnimator.INFINITE;repeatMode=ValueAnimator.RESTART;interpolator=LinearInterpolator();addUpdateListener{phase=it.animatedFraction;invalidate()}}
    init{animator.start()}
    override fun onDraw(c:Canvas){super.onDraw(c);val w=width.toFloat();val h=height.toFloat();if(w<=0f||h<=0f)return;val cx=w/2;val cy=h/2;val r=minOf(w,h)*.30f;val dot=minOf(w,h)*.055f;repeat(10){i->val shifted=(i/10f-phase+1f)%1f;val a=i/10f*(PI*2)-PI/2;val s=(1f-shifted).coerceIn(.12f,1f);paint.color=(color and 0x00FFFFFF) or (((42+s*213).toInt().coerceIn(0,255)) shl 24);c.drawCircle(cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,dot*(.58f+s*.42f),paint)};paint.color=(color and 0x00FFFFFF) or (28 shl 24);c.drawCircle(cx,cy,minOf(w,h)*.11f,paint)}
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(!animator.isStarted)animator.start()}
    override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
}
