package com.unknokable.videosohranenki
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
class StreakFireView(context:Context,private var flameColor:Int):View(context){
 private val glow=Paint(Paint.ANTI_ALIAS_FLAG);private val outer=Paint(Paint.ANTI_ALIAS_FLAG);private val inner=Paint(Paint.ANTI_ALIAS_FLAG);private val core=Paint(Paint.ANTI_ALIAS_FLAG);private val op=Path();private val ip=Path();private var phase=0f
 private val animator=ValueAnimator.ofFloat(0f,1f).apply{duration=2300L;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator();addUpdateListener{phase=it.animatedFraction;invalidate()}}
 init{setLayerType(LAYER_TYPE_SOFTWARE,null);animator.start()}
 fun setFlameColor(c:Int){flameColor=c;invalidate()}
 override fun onDraw(c:Canvas){super.onDraw(c);val w=width.toFloat();val h=height.toFloat();if(w<=0||h<=0)return;val t=(phase*Math.PI*2).toFloat();val sway=sin(t)*w*.018f;val tip=sin(t*1.31f+.7f)*w*.03f;val pulse=1f+sin(t*1.7f)*.018f;val cx=w*.5f;val bottom=h*.90f;glow.shader=RadialGradient(cx,h*.60f,w*.44f,intArrayOf(alpha(flameColor,82),alpha(flameColor,0)),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,h*.60f,w*.44f,glow);c.save();c.translate(sway,0f);c.scale(pulse,pulse,cx,bottom);outer.shader=LinearGradient(0f,h*.12f,0f,h*.91f,intArrayOf(blend(flameColor,Color.WHITE,.40f),flameColor,darken(flameColor,.20f)),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP);op.reset();op.moveTo(cx,bottom);op.cubicTo(w*.19f,h*.83f,w*.16f,h*.61f,w*.31f,h*.43f);op.cubicTo(w*.39f,h*.34f,w*.36f,h*.23f,cx+tip,h*.07f);op.cubicTo(w*.56f+tip*.4f,h*.24f,w*.75f,h*.31f,w*.70f,h*.49f);op.cubicTo(w*.87f,h*.62f,w*.78f,h*.85f,cx,bottom);op.close();c.drawPath(op,outer);inner.shader=LinearGradient(0f,h*.34f,0f,h*.82f,blend(flameColor,Color.WHITE,.76f),blend(flameColor,Color.WHITE,.28f),Shader.TileMode.CLAMP);val sh=sin(t*1.55f+1.2f)*w*.016f;ip.reset();ip.moveTo(cx+sh,h*.81f);ip.cubicTo(w*.39f,h*.73f,w*.41f,h*.60f,w*.47f,h*.50f);ip.cubicTo(w*.52f,h*.44f,w*.50f,h*.37f,w*.55f+sh,h*.30f);ip.cubicTo(w*.65f,h*.49f,w*.69f,h*.65f,w*.63f,h*.73f);ip.cubicTo(w*.60f,h*.78f,w*.55f,h*.81f,cx+sh,h*.81f);ip.close();c.drawPath(ip,inner);core.color=Color.WHITE;core.alpha=110;c.drawOval(w*.45f,h*.67f,w*.56f,h*.81f,core);c.restore()}
 override fun onAttachedToWindow(){super.onAttachedToWindow();if(!animator.isStarted)animator.start()}
 override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
 private fun alpha(c:Int,a:Int)=Color.argb(a.coerceIn(0,255),Color.red(c),Color.green(c),Color.blue(c))
 private fun darken(c:Int,a:Float)=Color.rgb((Color.red(c)*(1-a)).toInt(),(Color.green(c)*(1-a)).toInt(),(Color.blue(c)*(1-a)).toInt())
 companion object{
  fun colorForStreak(s:Int)=when{s<=0->Color.parseColor("#686873");s<10->Color.parseColor("#F2F2F5");s<20->Color.parseColor("#A16BFF");s<50->Color.parseColor("#4FA4FF");s<100->Color.parseColor("#FF5266");s<200->Color.parseColor("#B9FF32");else->Color.parseColor("#65FFE8")}
  fun levelName(s:Int)=when{s<=0->"Нет серии";s<10->"Искра";s<20->"Аметист";s<50->"Синий огонь";s<100->"Красное пламя";s<200->"Кислотный огонь";else->"Плазма"}
  private fun blend(a:Int,b:Int,t:Float):Int{val x=t.coerceIn(0f,1f);return Color.rgb((Color.red(a)+(Color.red(b)-Color.red(a))*x).toInt(),(Color.green(a)+(Color.green(b)-Color.green(a))*x).toInt(),(Color.blue(a)+(Color.blue(b)-Color.blue(a))*x).toInt())}
 }
}
