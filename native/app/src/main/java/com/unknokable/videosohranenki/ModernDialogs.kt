package com.unknokable.videosohranenki

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 12))
            background = rounded(palette.surface, dp(context, 22).toFloat())
        }

        addHeader(box, context, palette, title, dialog)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        options.forEachIndexed { index, label ->
            val selectedNow = index == selected
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                alpha = 0f
                translationY = dp(context, 6).toFloat()
                setPadding(dp(context, 12), 0, dp(context, 12), 0)
                background = rounded(
                    if (selectedNow) palette.accentSoft else palette.surfaceAlt,
                    dp(context, 14).toFloat()
                )
                isClickable = true
                isFocusable = true
            }

            val dot = View(context).apply {
                background = rounded(
                    if (selectedNow) palette.accent else Color.TRANSPARENT,
                    dp(context, 7).toFloat()
                )
            }

            val text = TextView(context).apply {
                this.text = label
                textSize = 14.5f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (selectedNow) palette.accent else palette.text)
                setTypeface(typeface, if (selectedNow) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(context, 11), 0, 0, 0)
                maxLines = 2
            }

            row.addView(dot, LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)))
            row.addView(text, LinearLayout.LayoutParams(0, dp(context, 48), 1f))

            row.setOnClickListener {
                if (!row.isEnabled) return@setOnClickListener
                row.isEnabled = false
                row.animate().cancel()
                row.animate()
                    .scaleX(0.975f)
                    .scaleY(0.975f)
                    .alpha(0.88f)
                    .setDuration(55L)
                    .withEndAction {
                        dialog.dismiss()
                        onSelect(index)
                    }
                    .start()
            }

            list.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
            ).apply { bottomMargin = dp(context, 6) })

            row.post {
                row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((35L * index).coerceAtMost(210L))
                    .setDuration(150L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
        }

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }
        box.addView(scroll)

        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.52f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        val width = (context.resources.displayMetrics.widthPixels * 0.82f).toInt()
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.72f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        scroll.maximumHeightCompat(maxHeight)

        box.alpha = 0f
        box.scaleX = 0.975f
        box.scaleY = 0.975f
        box.translationY = dp(context, 8).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(170L)
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
        showSingleAction(context, palette, title, message, button, false, onClose)
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
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 16))
            background = rounded(palette.surface, dp(context, 22).toFloat())
        }

        addHeader(box, context, palette, title, dialog)

        box.addView(TextView(context).apply {
            text = message
            textSize = 13.5f
            setTextColor(palette.muted)
            setLineSpacing(0f, 1.08f)
            setPadding(0, dp(context, 8), 0, dp(context, 14))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val cancel = compactButton(context, palette.surfaceAlt, palette.text, "Отмена")
        val ok = compactButton(
            context,
            if (destructive) Color.parseColor("#D9435F") else palette.accent,
            Color.WHITE,
            confirm
        )

        cancel.setOnClickListener { animateClose(cancel, dialog) {} }
        ok.setOnClickListener {
            if (!ok.isEnabled) return@setOnClickListener
            ok.isEnabled = false
            animateClose(ok, dialog, onConfirm)
        }

        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(context, 46), 1f).apply {
            marginEnd = dp(context, 7)
        })
        actions.addView(ok, LinearLayout.LayoutParams(0, dp(context, 46), 1f))
        box.addView(actions)

        showDialog(context, dialog, box, 0.84f)
    }

    private fun showSingleAction(
        context: Context,
        palette: ThemePalette,
        title: String,
        message: String,
        button: String,
        destructive: Boolean,
        onClose: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 16))
            background = rounded(palette.surface, dp(context, 22).toFloat())
        }

        addHeader(box, context, palette, title, dialog)

        box.addView(TextView(context).apply {
            text = message
            textSize = 13.5f
            setTextColor(palette.muted)
            setLineSpacing(0f, 1.08f)
            setPadding(0, dp(context, 8), 0, dp(context, 14))
        })

        val ok = compactButton(
            context,
            if (destructive) Color.parseColor("#D9435F") else palette.accent,
            Color.WHITE,
            button
        )
        ok.setOnClickListener { animateClose(ok, dialog, onClose) }
        box.addView(ok, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 46)
        ))

        showDialog(context, dialog, box, 0.84f)
    }

    private fun addHeader(
        box: LinearLayout,
        context: Context,
        palette: ThemePalette,
        title: String,
        dialog: Dialog
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 2), 0, 0, dp(context, 10))
        }
        val titleView = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }
        val close = TextView(context).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            background = rounded(palette.surfaceAlt, dp(context, 16).toFloat())
            isClickable = true
            isFocusable = true
            contentDescription = "Закрыть"
            setOnClickListener { animateClose(this, dialog) {} }
        }
        row.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(close, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
            marginStart = dp(context, 10)
        })
        box.addView(row)
    }

    private fun showDialog(
        context: Context,
        dialog: Dialog,
        box: View,
        widthRatio: Float
    ) {
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.apply {
            setDimAmount(0.52f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout((context.resources.displayMetrics.widthPixels * widthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        box.alpha = 0f
        box.scaleX = 0.975f
        box.scaleY = 0.975f
        box.translationY = dp(context, 8).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(170L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    private fun compactButton(context: Context, bg: Int, fg: Int, label: String): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            background = rounded(bg, dp(context, 14).toFloat())
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
        }

    private fun animateClose(view: View, dialog: Dialog, end: () -> Unit) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.975f)
            .scaleY(0.975f)
            .setDuration(55L)
            .withEndAction {
                dialog.dismiss()
                end()
            }
            .start()
    }

    private fun ScrollView.maximumHeightCompat(maxHeight: Int) {
        viewTreeObserver.addOnGlobalLayoutListener {
            if (height > maxHeight) {
                layoutParams = layoutParams.apply { this.height = maxHeight }
            }
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(context: Context, v: Int) =
        (v * context.resources.displayMetrics.density).toInt()
}
