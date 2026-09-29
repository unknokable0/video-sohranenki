package com.unknokable.videosohranenki

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator

object SohrMotion {
    const val FAST = 120L
    const val NORMAL = 220L
    const val HERO = 320L

    fun smooth() = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    fun exit() = PathInterpolator(0.4f, 0f, 1f, 1f)

    fun press(view: View, enabled: Boolean = true) {
        SohrHaptics.tap(view)
        if (!enabled) return
        view.animate().cancel()
        view.animate()
            .scaleX(0.972f)
            .scaleY(0.972f)
            .alpha(0.94f)
            .setDuration(FAST / 2)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(FAST)
                    .setInterpolator(smooth())
                    .start()
            }
            .start()
    }
}

object SohrHaptics {
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun select(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun confirm(view: View) {
        val constant = if (android.os.Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }
}
