package com.unknokable.videosohranenki

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

object ModernDialogs {
    fun showChoices(
        context: Context,
        palette: ThemePalette,
        title: String,
        options: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 14))
            background = rounded(palette.surface, dp(context, 24).toFloat())
        }

        box.addView(TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 2), 0, dp(context, 2), dp(context, 12))
        })

        options.forEachIndexed { index, label ->
            val selectedNow = index == selected
            val row = TextView(context).apply {
                text = if (selectedNow) "●   $label" else "○   $label"
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (selectedNow) palette.accent else palette.text)
                setTypeface(typeface, if (selectedNow) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
                background = rounded(
                    if (selectedNow) palette.accentSoft else palette.surfaceAlt,
                    dp(context, 16).toFloat()
                )
                setOnClickListener {
                    if (!isEnabled) return@setOnClickListener
                    isEnabled = false
                    animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .alpha(0.82f)
                        .setDuration(65L)
                        .withEndAction {
                            dialog.dismiss()
                            onSelect(index)
                        }
                        .start()
                }
            }
            box.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 8) })
        }

        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.58f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { width = (context.resources.displayMetrics.widthPixels * 0.88f).toInt() }
        }
        dialog.show()
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        box.alpha = 0f
        box.scaleX = 0.96f
        box.scaleY = 0.96f
        box.translationY = dp(context, 10).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(190L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    fun showConfirm(
        context: Context,
        palette: ThemePalette,
        title: String,
        message: String,
        confirm: String,
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 18))
            background = rounded(palette.surface, dp(context, 24).toFloat())
        }
        box.addView(TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(palette.muted)
            setPadding(0, dp(context, 8), 0, dp(context, 18))
        })
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = TextView(context).apply {
            text = "Отмена"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(palette.text)
            background = rounded(palette.surfaceAlt, dp(context, 15).toFloat())
            setOnClickListener {
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(60L).withEndAction {
                    dialog.dismiss()
                }.start()
            }
        }
        val ok = TextView(context).apply {
            text = confirm
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(if (destructive) Color.parseColor("#D9435F") else palette.accent, dp(context, 15).toFloat())
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(60L).withEndAction {
                    dialog.dismiss()
                    onConfirm()
                }.start()
            }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(context, 48), 1f))
        actions.addView(ok, LinearLayout.LayoutParams(0, dp(context, 48), 1f).apply { marginStart = dp(context, 10) })
        box.addView(actions)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        box.alpha = 0f
        box.scaleX = 0.96f
        box.scaleY = 0.96f
        box.translationY = dp(context, 10).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(190L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
