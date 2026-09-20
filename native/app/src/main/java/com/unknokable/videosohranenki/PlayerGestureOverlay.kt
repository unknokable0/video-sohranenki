package com.unknokable.videosohranenki

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.media3.common.Player
import kotlin.math.abs

class PlayerGestureOverlay(
    private val activity: Activity,
    private val target: View,
    private val player: Player,
    private val durationProvider: () -> Long,
    private val progressZone: (Float) -> Boolean,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: (Boolean, Int) -> Unit,
    private val onTemporarySpeed: (Boolean) -> Unit,
    private val onScrub: (Long, Boolean) -> Unit,
    private val onBrightness: (Int) -> Unit,
    private val onVolume: (Int) -> Unit,
    private val onFillMode: (Boolean) -> Unit,
    private val onSwipeDown: () -> Unit,
    private val onSwipeUp: () -> Unit
) : View.OnTouchListener {
    companion object {
        const val DOUBLE_TAP_MS = 320L
        const val DOUBLE_TAP_CHAIN_MS = 720L
        const val LONG_PRESS_MS = 420L
        const val SWIPE_TRIGGER_DP = 86f
        const val VERTICAL_CONTROL_DP = 22f
        const val SCRUB_RANGE_FRACTION = 0.72f
    }

    private val density = activity.resources.displayMetrics.density
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var downPosition = 0L
    private var moved = false
    private var verticalMode = 0
    private var longMode = 0
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var chainAt = 0L
    private var chainDirection = 0
    private var chainSeconds = 0
    private var brightnessStart = 0.5f
    private var volumeStart = 0
    private var fillMode = false
    private var scaleAccum = 1f

    private val longPress = Runnable {
        if (moved || scaleDetector.isInProgress) return@Runnable
        target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (player.isPlaying && !progressZone(downY)) {
            longMode = 1
            onTemporarySpeed(true)
        } else {
            longMode = 2
            downPosition = player.currentPosition
            onScrub(downPosition, false)
        }
    }

    private val scaleDetector = ScaleGestureDetector(activity,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                target.removeCallbacks(longPress)
                moved = true
                scaleAccum = 1f
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleAccum *= detector.scaleFactor
                if (!fillMode && scaleAccum > 1.08f) {
                    fillMode = true
                    onFillMode(true)
                    target.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scaleAccum = 1f
                } else if (fillMode && scaleAccum < 0.92f) {
                    fillMode = false
                    onFillMode(false)
                    target.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scaleAccum = 1f
                }
                return true
            }
        })

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                downPosition = player.currentPosition
                moved = false
                verticalMode = 0
                longMode = 0
                brightnessStart = activity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                volumeStart = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                v.postDelayed(longPress, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                v.removeCallbacks(longPress)
                moved = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                val dx = event.x - downX
                val dy = event.y - downY
                val absX = abs(dx)
                val absY = abs(dy)
                if (longMode == 2) {
                    val duration = durationProvider().coerceAtLeast(1L)
                    val delta = (dx / (target.width * SCRUB_RANGE_FRACTION) * duration).toLong()
                    onScrub((downPosition + delta).coerceIn(0L, duration), false)
                    return true
                }
                if (longMode == 1) return true
                if (verticalMode == 0 && absY > VERTICAL_CONTROL_DP * density && absY > absX * 1.2f) {
                    verticalMode = if (downX < target.width / 2f) -1 else 1
                    moved = true
                    v.removeCallbacks(longPress)
                }
                if (verticalMode != 0) {
                    val delta = -dy / target.height.coerceAtLeast(1)
                    if (verticalMode < 0) {
                        val value = (brightnessStart + delta).coerceIn(0.02f, 1f)
                        val attrs = activity.window.attributes
                        attrs.screenBrightness = value
                        activity.window.attributes = attrs
                        onBrightness((value * 100).toInt())
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val value = (volumeStart + delta * max).toInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        onVolume((value * 100f / max).toInt())
                    }
                    return true
                }
                if (absX > 18f * density || absY > 18f * density) {
                    moved = true
                    v.removeCallbacks(longPress)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.removeCallbacks(longPress)
                if (longMode == 1) onTemporarySpeed(false)
                if (longMode == 2) onScrub(player.currentPosition, true)
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (verticalMode == 0 && longMode == 0 && !scaleDetector.isInProgress) {
                    if (abs(dy) > SWIPE_TRIGGER_DP * density && abs(dy) > abs(dx) * 1.25f) {
                        if (dy > 0) onSwipeDown() else onSwipeUp()
                        return true
                    }
                    if (!moved && SystemClock.uptimeMillis() - downAt < LONG_PRESS_MS) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapAt <= DOUBLE_TAP_MS && abs(event.x - lastTapX) < target.width * 0.35f) {
                            val direction = if (event.x >= target.width / 2f) 1 else -1
                            if (direction == chainDirection && now - chainAt <= DOUBLE_TAP_CHAIN_MS) chainSeconds += 10 else chainSeconds = 10
                            chainDirection = direction
                            chainAt = now
                            lastTapAt = 0L
                            val duration = durationProvider().coerceAtLeast(1L)
                            player.seekTo((player.currentPosition + direction * 10_000L).coerceIn(0L, duration))
                            target.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDoubleTap(direction > 0, chainSeconds)
                        } else {
                            lastTapAt = now
                            lastTapX = event.x
                            v.postDelayed({
                                if (lastTapAt == now) {
                                    lastTapAt = 0L
                                    onSingleTap()
                                }
                            }, DOUBLE_TAP_MS)
                        }
                    }
                }
                verticalMode = 0
                longMode = 0
                return true
            }
        }
        return true
    }
}
