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
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 12))
            background = rounded(palette.surface, dp(context, 22).toFloat())
        }

        box.addView(TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 10))
        })

        options.forEachIndexed { index, label ->
            val selectedNow = index == selected

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 12), 0, dp(context, 12), 0)
                background = rounded(
                    if (selectedNow) palette.accentSoft else palette.surfaceAlt,
                    dp(context, 14).toFloat()
                )
                isClickable = true
                isFocusable = true
            }

            val dot = TextView(context).apply {
                text = if (selectedNow) "●" else "○"
                textSize = if (selectedNow) 17f else 18f
                gravity = Gravity.CENTER
                setTextColor(if (selectedNow) palette.accent else palette.muted)
            }

            val labelView = TextView(context).apply {
                text = label
                textSize = 14.5f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (selectedNow) palette.accent else palette.text)
                setTypeface(typeface, if (selectedNow) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(context, 10), 0, 0, 0)
            }

            row.addView(dot, LinearLayout.LayoutParams(dp(context, 22), dp(context, 46)))
            row.addView(
                labelView,
                LinearLayout.LayoutParams(0, dp(context, 46), 1f)
            )

            row.setOnClickListener {
                if (!row.isEnabled) return@setOnClickListener
                row.isEnabled = false
                row.animate().cancel()
                row.animate()
                    .scaleX(0.975f)
                    .scaleY(0.975f)
                    .alpha(0.84f)
                    .setDuration(55L)
                    .withEndAction {
                        row.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(90L)
                            .withEndAction {
                                dialog.dismiss()
                                onSelect(index)
                            }
                            .start()
                    }
                    .start()
            }

            box.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 46)
                ).apply {
                    if (index < options.lastIndex) bottomMargin = dp(context, 6)
                }
            )
        }

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.82f).toInt()
        dialog.window?.apply {
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.52f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        box.alpha = 0f
        box.scaleX = 0.94f
        box.scaleY = 0.94f
        box.translationY = dp(context, 12).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(175L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    fun showNotice(
        context: Context,
        palette: ThemePalette,
        title: String,
        message: String,
        button: String = "Готово",
        onClose: () -> Unit = {}
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
            setLineSpacing(0f, 1.08f)
            setPadding(0, dp(context, 9), 0, dp(context, 18))
        })

        val ok = TextView(context).apply {
            text = button
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(palette.accent, dp(context, 15).toFloat())
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                animate().cancel()
                animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(60L)
                    .withEndAction {
                        dialog.dismiss()
                        onClose()
                    }
                    .start()
            }
        }

        box.addView(
            ok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 50)
            )
        )

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        box.alpha = 0f
        box.scaleX = 0.965f
        box.scaleY = 0.965f
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
            setPadding(dp(context, 2), 0, dp(context, 2), 0)
        })

        box.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(palette.muted)
            setLineSpacing(0f, 1.08f)
            setPadding(dp(context, 2), dp(context, 9), dp(context, 2), dp(context, 18))
        })

        val cancel = TextView(context).apply {
            text = "Отмена"
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            background = rounded(palette.surfaceAlt, dp(context, 15).toFloat())
            setOnClickListener {
                animate().cancel()
                animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(60L)
                    .withEndAction { dialog.dismiss() }
                    .start()
            }
        }

        val ok = TextView(context).apply {
            text = confirm
            gravity = Gravity.CENTER
            textSize = 14.5f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(
                if (destructive) Color.parseColor("#D9435F") else palette.accent,
                dp(context, 15).toFloat()
            )
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                animate().cancel()
                animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(60L)
                    .withEndAction {
                        dialog.dismiss()
                        onConfirm()
                    }
                    .start()
            }
        }

        box.addView(
            ok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 50)
            )
        )
        box.addView(
            cancel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
            ).apply { topMargin = dp(context, 10) }
        )

        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.86f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        box.alpha = 0f
        box.scaleX = 0.965f
        box.scaleY = 0.965f
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
