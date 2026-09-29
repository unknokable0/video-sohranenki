package com.unknokable.videosohranenki

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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

        val box = dialogBox(context, palette)
        box.addView(header(context, palette, title, dialog, box))

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        options.forEachIndexed { index, label ->
            val selectedNow = index == selected
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 14), 0, dp(context, 14), 0)
                background = rounded(
                    if (selectedNow) palette.accentSoft else palette.surfaceAlt,
                    dp(context, 16).toFloat()
                )
                isClickable = true
                isFocusable = true
            }

            val marker = FrameLayout(context).apply {
                background = circle(
                    if (selectedNow) palette.accent else Color.TRANSPARENT,
                    if (selectedNow) palette.accent else palette.stroke,
                    context
                )
            }
            if (selectedNow) {
                marker.addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_check)
                        imageTintList = ColorStateList.valueOf(Color.WHITE)
                        setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5))
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            val text = TextView(context).apply {
                this.text = label
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setTextColor(if (selectedNow) palette.accent else palette.text)
                setTypeface(typeface, if (selectedNow) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(context, 12), dp(context, 14), 0, dp(context, 14))
                maxLines = 3
            }

            row.minimumHeight = dp(context, 56)
            row.addView(marker, LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)))
            row.addView(
                text,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            row.setOnClickListener {
                if (!row.isEnabled) return@setOnClickListener
                row.isEnabled = false
                press(row) { close(dialog, box) { onSelect(index) } }
            }

            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 8) }
            )
        }

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }
        box.addView(scroll)

        showDialog(context, dialog, box, 0.92f)
        scroll.limitHeight((context.resources.displayMetrics.heightPixels * 0.58f).toInt())
    }

    fun showAppearancePicker(
        context: Context,
        palette: ThemePalette,
        lightTheme: Boolean,
        accentKey: String,
        presets: List<ThemePreset>,
        onApply: (Boolean, String) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = dialogBox(context, palette)
        box.addView(header(context, palette, "Оформление", dialog, box))
        box.addView(TextView(context).apply {
            text = "Выберите режим и цвет SOHR"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(palette.muted)
            setPadding(0, 0, 0, dp(context, 14))
        })

        var selectedLight = lightTheme
        var selectedAccent = AppThemes.preset(accentKey).key

        val mode = FrameLayout(context).apply {
            background = rounded(palette.surfaceAlt, dp(context, 17).toFloat())
            setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4))
            clipChildren = true
        }
        val indicator = View(context).apply {
            background = rounded(palette.accent, dp(context, 14).toFloat())
        }
        val modeButtons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun modeButton(label: String): TextView = TextView(context).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
        }
        val dark = modeButton("Тёмная")
        val light = modeButton("Светлая")
        modeButtons.addView(dark, LinearLayout.LayoutParams(0, dp(context, 46), 1f))
        modeButtons.addView(light, LinearLayout.LayoutParams(0, dp(context, 46), 1f))
        mode.addView(indicator, FrameLayout.LayoutParams(0, dp(context, 46), Gravity.START or Gravity.CENTER_VERTICAL))
        mode.addView(modeButtons, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46), Gravity.CENTER))

        fun syncMode(animate: Boolean) {
            val slot = ((mode.width - mode.paddingLeft - mode.paddingRight) / 2f).coerceAtLeast(0f)
            if (slot <= 0f) return
            val lp = indicator.layoutParams as FrameLayout.LayoutParams
            lp.width = slot.toInt()
            lp.height = dp(context, 46)
            indicator.layoutParams = lp
            val target = if (selectedLight) slot else 0f
            indicator.animate().cancel()
            if (animate) {
                indicator.animate()
                    .translationX(target)
                    .setDuration(230L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            } else {
                indicator.translationX = target
            }
            dark.setTextColor(if (!selectedLight) Color.WHITE else palette.muted)
            light.setTextColor(if (selectedLight) Color.WHITE else palette.muted)
        }

        dark.setOnClickListener {
            if (!selectedLight) return@setOnClickListener
            selectedLight = false
            syncMode(true)
        }
        light.setOnClickListener {
            if (selectedLight) return@setOnClickListener
            selectedLight = true
            syncMode(true)
        }
        mode.post { syncMode(false) }
        box.addView(mode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 54)))

        val colorHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 2), dp(context, 16), 0, dp(context, 8))
        }
        colorHeader.addView(TextView(context).apply {
            text = "Цвет"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val selectedLabel = TextView(context).apply {
            textSize = 11.5f
            includeFontPadding = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
        }
        colorHeader.addView(selectedLabel)
        box.addView(colorHeader)

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun syncSelectedLabel() {
            val current = presets.firstOrNull { it.key == selectedAccent }
            selectedLabel.text = current?.label ?: "Выбран"
            selectedLabel.setTextColor(current?.previewColor ?: palette.accent)
        }

        fun rebuildGrid() {
            syncSelectedLabel()
            grid.removeAllViews()
            presets.chunked(3).forEach { chunk ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                chunk.forEach { preset ->
                    val selected = preset.key == selectedAccent
                    val tile = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(context, 5), dp(context, 8), dp(context, 5), dp(context, 8))
                        background = rounded(
                            if (selected) palette.accentSoft else Color.TRANSPARENT,
                            dp(context, 15).toFloat()
                        )
                        isClickable = true
                        isFocusable = true
                    }

                    val swatch = FrameLayout(context).apply {
                        background = circle(
                            if (selected) preset.previewColor else palette.surfaceAlt,
                            if (selected) preset.previewColor else palette.stroke,
                            context
                        )
                    }
                    swatch.addView(
                        View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(preset.previewColor)
                            }
                        },
                        FrameLayout.LayoutParams(dp(context, 34), dp(context, 34), Gravity.CENTER)
                    )

                    if (selected) {
                        swatch.addView(
                            ImageView(context).apply {
                                setImageResource(R.drawable.ic_check)
                                imageTintList = ColorStateList.valueOf(Color.WHITE)
                                setPadding(dp(context, 13), dp(context, 13), dp(context, 13), dp(context, 13))
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }

                    tile.addView(swatch, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))
                    tile.addView(TextView(context).apply {
                        text = preset.label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        maxLines = 2
                        minHeight = dp(context, 32)
                        setLineSpacing(0f, 1.02f)
                        setTextColor(if (selected) palette.text else palette.muted)
                        setPadding(dp(context, 2), dp(context, 5), dp(context, 2), 0)
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))

                    tile.setOnClickListener {
                        if (selectedAccent == preset.key) return@setOnClickListener
                        selectedAccent = preset.key
                        rebuildGrid()
                    }
                    row.addView(tile, LinearLayout.LayoutParams(0, dp(context, 96), 1f))
                }

                repeat(3 - chunk.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, dp(context, 96), 1f))
                }
                grid.addView(row)
            }
        }
        rebuildGrid()

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(grid)
        }
        box.addView(scroll)
        scroll.limitHeight((context.resources.displayMetrics.heightPixels * 0.43f).toInt())

        val apply = compactButton(context, palette.accent, Color.WHITE, "Применить")
        apply.setOnClickListener {
            if (!apply.isEnabled) return@setOnClickListener
            apply.isEnabled = false
            press(apply) {
                close(dialog, box) {
                    onApply(selectedLight, selectedAccent)
                }
            }
        }
        box.addView(
            apply,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)).apply {
                topMargin = dp(context, 12)
            }
        )

        showDialog(context, dialog, box, 0.91f)
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

        val box = dialogBox(context, palette)
        box.addView(header(context, palette, title, dialog, box))
        box.addView(messageView(context, palette, message))

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancel = compactButton(context, palette.surfaceAlt, palette.text, "Отмена")
        val ok = compactButton(
            context,
            if (destructive) Color.parseColor("#D9435F") else palette.accent,
            Color.WHITE,
            confirm
        )

        cancel.setOnClickListener { close(dialog, box) {} }
        ok.setOnClickListener {
            if (!ok.isEnabled) return@setOnClickListener
            ok.isEnabled = false
            press(ok) { close(dialog, box, onConfirm) }
        }

        actions.addView(
            cancel,
            LinearLayout.LayoutParams(0, dp(context, 48), 1f).apply {
                marginEnd = dp(context, 8)
            }
        )
        actions.addView(ok, LinearLayout.LayoutParams(0, dp(context, 48), 1f))
        box.addView(actions)

        showDialog(context, dialog, box, 0.88f)
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

        val box = dialogBox(context, palette)
        box.addView(header(context, palette, title, dialog, box))
        box.addView(messageView(context, palette, message))

        val ok = compactButton(
            context,
            if (destructive) Color.parseColor("#D9435F") else palette.accent,
            Color.WHITE,
            button
        )
        ok.setOnClickListener { press(ok) { close(dialog, box, onClose) } }
        box.addView(
            ok,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)
            )
        )

        showDialog(context, dialog, box, 0.88f)
    }

    private fun dialogBox(context: Context, palette: ThemePalette) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 18))
            background = rounded(palette.surface, dp(context, 28).toFloat())
            elevation = dp(context, 12).toFloat()
            addView(
                View(context).apply {
                    background = rounded(palette.stroke, dp(context, 2).toFloat())
                    alpha = 0.7f
                },
                LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(context, 10)
                }
            )
        }

    private fun header(
        context: Context,
        palette: ThemePalette,
        title: String,
        dialog: Dialog,
        box: View
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(context, 2), dp(context, 2), 0, dp(context, 14))
            clipChildren = false
            clipToPadding = false
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 18.5f
            gravity = Gravity.START
            includeFontPadding = false
            maxLines = 5
            setLineSpacing(dp(context, 1).toFloat(), 1.05f)
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(context, 2), dp(context, 7), dp(context, 8), dp(context, 7))
        }

        val closeButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(palette.muted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(context, 11), dp(context, 11), dp(context, 11), dp(context, 11))
            background = rounded(palette.surfaceAlt, dp(context, 18).toFloat())
            isClickable = true
            isFocusable = true
            contentDescription = "Закрыть"
            setOnClickListener { close(dialog, box) {} }
        }

        row.addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(context, 10)
            }
        )
        row.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(context, 40), dp(context, 40))
        )
        return row
    }

    private fun messageView(context: Context, palette: ThemePalette, message: String) =
        TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(palette.muted)
            setLineSpacing(dp(context, 1).toFloat(), 1.08f)
            setPadding(dp(context, 2), 0, dp(context, 2), dp(context, 16))
        }

    private fun showDialog(
        context: Context,
        dialog: Dialog,
        box: View,
        widthRatio: Float
    ) {
        dialog.setContentView(box)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        dialog.window?.apply {
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.46f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val requestedWidth =
                (context.resources.displayMetrics.widthPixels * widthRatio).toInt()
            val maxWidth = dp(context, 520)
            setLayout(
                minOf(requestedWidth, maxWidth),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        box.alpha = 0f
        box.scaleX = 0.955f
        box.scaleY = 0.955f
        box.translationY = dp(context, 18).toFloat()
        box.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()
    }

    private fun compactButton(
        context: Context,
        bg: Int,
        fg: Int,
        label: String
    ): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            includeFontPadding = false
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            background = rounded(bg, dp(context, 16).toFloat())
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
        }

    private fun press(view: View, end: () -> Unit) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.972f)
            .scaleY(0.972f)
            .alpha(0.9f)
            .setDuration(65L)
            .withEndAction(end)
            .start()
    }

    private fun close(dialog: Dialog, content: View, end: () -> Unit) {
        content.animate().cancel()
        content.animate()
            .alpha(0f)
            .scaleX(0.97f)
            .scaleY(0.97f)
            .translationY(dp(content.context, 10).toFloat())
            .setDuration(145L)
            .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
            .withEndAction {
                if (dialog.isShowing) dialog.dismiss()
                end()
            }
            .start()
    }

    private fun ScrollView.limitHeight(maxHeight: Int) {
        val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!viewTreeObserver.isAlive) return
                if (height > maxHeight) {
                    layoutParams = layoutParams.apply { this.height = maxHeight }
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun rounded(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun roundedStroke(
        color: Int,
        strokeColor: Int,
        radius: Float,
        strokeDp: Float,
        context: Context
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(dp(context, strokeDp), strokeColor)
    }

    private fun circle(fill: Int, stroke: Int, context: Context) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(dp(context, 1f), stroke)
        }

    private fun dp(context: Context, v: Int) =
        (v * context.resources.displayMetrics.density).toInt()

    private fun dp(context: Context, v: Float) =
        (v * context.resources.displayMetrics.density).toInt()
}
