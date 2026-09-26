package com.unknokable.videosohranenki
import android.graphics.Color
data class ThemePalette(val background:Int,val surface:Int,val surfaceAlt:Int,val text:Int,val muted:Int,val accent:Int,val accentSoft:Int,val stroke:Int,val playerBackground:Int)
object AppThemes {
    val Dark=ThemePalette(Color.parseColor("#0B0A10"),Color.parseColor("#14131A"),Color.parseColor("#1C1A24"),Color.parseColor("#F7F5FF"),Color.parseColor("#9D98A8"),Color.parseColor("#8B5CF6"),Color.parseColor("#2B1D47"),Color.parseColor("#2A2732"),Color.BLACK)
    val Light=ThemePalette(Color.parseColor("#F5F4F8"),Color.parseColor("#FFFFFF"),Color.parseColor("#ECE9F2"),Color.parseColor("#17131F"),Color.parseColor("#716A7E"),Color.parseColor("#7C4DFF"),Color.parseColor("#E9E0FF"),Color.parseColor("#DDD8E6"),Color.BLACK)
}
