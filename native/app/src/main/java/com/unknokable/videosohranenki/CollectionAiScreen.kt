package com.unknokable.videosohranenki

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CollectionAiScreen(
    private val activity: Activity,
    private val collection: DayCollection,
    private val server: TelegramStreamServer,
    private val settings: AppSettings,
    private val onBack: () -> Unit,
    private val onOpenAt: (VideoItem, Int) -> Unit
) {
    val root = LinearLayout(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val analyzer = LocalAiAnalyzer()
    private val cache = AiResultCache(activity)
    private val palette get() = settings.palette()

    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private lateinit var analyzeButton: TextView

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(palette.background)

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val back = TextView(activity).apply {
            text = "‹"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            background = rounded(palette.surfaceAlt, 22)
            setOnClickListener { onBack() }
        }

        val title = TextView(activity).apply {
            text = "AI-карта сборника"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
        }

        val spacer = View(activity)
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(spacer, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), dp(12))
        }

        val subtitle = TextView(activity).apply {
            text = "${collection.videos.size} видео • локальный анализ на телефоне"
            textSize = 12.5f
            setTextColor(palette.muted)
        }

        analyzeButton = TextView(activity).apply {
            text = "AI  Проанализировать весь сборник"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(palette.accent, 16)
            setOnClickListener { analyzeAll() }
        }

        status = TextView(activity).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(0, dp(9), 0, 0)
        }

        top.addView(subtitle)
        top.addView(analyzeButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply { topMargin = dp(10) })
        top.addView(status)
        root.addView(top)

        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(18))
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        renderAll()
    }

    private fun analyzeAll() {
        if (!settings.aiAnalysis) {
            Toast.makeText(activity, "Включи AI-анализ в настройках", Toast.LENGTH_SHORT).show()
            return
        }

        analyzeButton.isEnabled = false
        analyzeButton.alpha = 0.65f

        scope.launch {
            try {
                val videos = collection.videos
                videos.forEachIndexed { index, video ->
                    val cached = cache.load(video.messageId)
                    if (cached == null) {
                        status.text = "Видео ${index + 1}/${videos.size}: ${cleanTitle(video.title)}"
                        val result = analyzer.analyze(
                            mediaDataSource = server.mediaDataSource(video),
                            durationSeconds = video.durationSeconds
                        ) { progress ->
                            activity.runOnUiThread {
                                status.text = "Видео ${index + 1}/${videos.size} • $progress%"
                            }
                        }
                        if (result.chapters.isNotEmpty()) {
                            cache.save(video.messageId, result)
                        }
                    }
                    renderAll()
                }
                status.text = "Готово • карта сборника обновлена"
            } catch (e: Exception) {
                status.text = "Анализ остановлен: ${e.message ?: "ошибка"}"
            } finally {
                analyzeButton.isEnabled = true
                analyzeButton.alpha = 1f
                analyzeButton.text = "AI  Обновить весь сборник"
            }
        }
    }

    private fun renderAll() {
        content.removeAllViews()
        var cachedCount = 0

        collection.videos.forEachIndexed { index, video ->
            val result = cache.load(video.messageId)
            if (result != null) cachedCount++

            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
                background = rounded(palette.surface, 18)
            }

            val header = TextView(activity).apply {
                text = "${index + 1}. ${cleanTitle(video.title)}"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.text)
            }
            card.addView(header)

            val meta = TextView(activity).apply {
                text = if (result == null) {
                    "Ещё не проанализировано"
                } else {
                    "${result.chapters.size} глав • ${result.sampledFrames} кадров"
                }
                textSize = 11.5f
                setTextColor(palette.muted)
                setPadding(0, dp(4), 0, dp(7))
            }
            card.addView(meta)

            if (result == null) {
                val one = TextView(activity).apply {
                    text = "Анализировать это видео"
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(palette.accent)
                    background = rounded(palette.surfaceAlt, 13)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setOnClickListener { analyzeOne(video) }
                }
                card.addView(one)
            } else {
                result.chapters.forEach { chapter ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        background = rounded(palette.surfaceAlt, 13)
                        setOnClickListener { onOpenAt(video, chapter.startSeconds) }
                    }

                    val time = TextView(activity).apply {
                        text = formatSeconds(chapter.startSeconds)
                        textSize = 12f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.accent)
                    }

                    val texts = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(9), 0, 0, 0)
                    }

                    val name = TextView(activity).apply {
                        text = chapter.title
                        textSize = 13f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.text)
                    }

                    val detail = TextView(activity).apply {
                        text = chapter.detail
                        textSize = 11f
                        maxLines = 2
                        setTextColor(palette.muted)
                    }

                    texts.addView(name)
                    texts.addView(detail)
                    row.addView(time, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
                    row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                    card.addView(row, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) })
                }
            }

            content.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        status.text = if (cachedCount == collection.videos.size && collection.videos.isNotEmpty()) {
            "Весь сборник уже проанализирован"
        } else {
            "Готово $cachedCount/${collection.videos.size} видео"
        }
    }

    private fun analyzeOne(video: VideoItem) {
        analyzeButton.isEnabled = false
        scope.launch {
            try {
                status.text = "Анализируем ${cleanTitle(video.title)}…"
                val result = analyzer.analyze(
                    mediaDataSource = server.mediaDataSource(video),
                    durationSeconds = video.durationSeconds
                ) { progress ->
                    activity.runOnUiThread { status.text = "Анализ… $progress%" }
                }
                if (result.chapters.isNotEmpty()) cache.save(video.messageId, result)
                renderAll()
            } catch (e: Exception) {
                status.text = "Ошибка анализа: ${e.message ?: "неизвестно"}"
            } finally {
                analyzeButton.isEnabled = true
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun cleanTitle(raw: String): String {
        val fileName = raw.matches(
            Regex("""\d{4}-\d{2}-\d{2}[_-].*\.(mp4|mkv|mov|webm)""", RegexOption.IGNORE_CASE)
        )
        return if (fileName) "Запись стрима" else raw
    }

    private fun formatSeconds(seconds: Int): String =
        "%d:%02d".format(seconds / 60, seconds % 60)

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
