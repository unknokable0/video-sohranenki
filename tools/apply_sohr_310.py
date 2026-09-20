from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'native/app/src/main/java/com/unknokable/videosohranenki/MainActivity.kt'
PLAYER = ROOT / 'native/app/src/main/java/com/unknokable/videosohranenki/PlayerScreen.kt'
GRADLE = ROOT / 'native/app/build.gradle.kts'
TIMEBAR = ROOT / 'native/app/src/main/java/com/unknokable/videosohranenki/SohrTimeBar.kt'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f'Patch target not found: {label}')
    return text.replace(old, new, 1)


# ---------- YouTube-style time bar ----------
TIMEBAR.write_text(r'''package com.unknokable.videosohranenki

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToLong

/** Lightweight YouTube-like timeline: played + buffered + scrubber with a larger touch target. */
class SohrTimeBar(context: Context) : View(context) {
    interface Listener {
        fun onScrubStart(positionMs: Long)
        fun onScrubMove(positionMs: Long, fraction: Float)
        fun onScrubStop(positionMs: Long, canceled: Boolean)
    }

    var listener: Listener? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var bufferedMs = 0L
    private var scrubbing = false
    private var scrubPositionMs = 0L

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(105, 255, 255, 255) }
    private val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255) }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 139, 250) }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(167, 139, 250) }
    private val rect = RectF()

    fun setProgress(positionMs: Long, durationMs: Long, bufferedPositionMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        if (!scrubbing) this.positionMs = positionMs.coerceIn(0L, this.durationMs.coerceAtLeast(0L))
        this.bufferedMs = bufferedPositionMs.coerceIn(0L, this.durationMs.coerceAtLeast(0L))
        invalidate()
    }

    private fun dp(v: Float) = v * density
    private fun fractionFor(ms: Long): Float = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    private fun positionFor(x: Float): Long {
        if (durationMs <= 0L) return 0L
        val fraction = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        return (durationMs * fraction).roundToLong()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(30f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return
        val centerY = height / 2f
        val trackH = dp(if (scrubbing) 4f else 3f)
        val radius = trackH / 2f
        val played = if (scrubbing) scrubPositionMs else positionMs
        val playedX = width * fractionFor(played)
        val bufferedX = width * fractionFor(bufferedMs)

        rect.set(0f, centerY - trackH / 2f, width.toFloat(), centerY + trackH / 2f)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        if (bufferedX > 0f) {
            rect.right = bufferedX
            canvas.drawRoundRect(rect, radius, radius, bufferPaint)
        }
        if (playedX > 0f) {
            rect.right = playedX
            canvas.drawRoundRect(rect, radius, radius, playedPaint)
        }
        if (scrubbing) {
            canvas.drawCircle(playedX.coerceIn(dp(7f), width - dp(7f)), centerY, dp(7f), thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L || width <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scrubbing = true
                scrubPositionMs = positionFor(event.x)
                listener?.onScrubStart(scrubPositionMs)
                listener?.onScrubMove(scrubPositionMs, fractionFor(scrubPositionMs))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrubPositionMs = positionFor(event.x)
                listener?.onScrubMove(scrubPositionMs, fractionFor(scrubPositionMs))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                if (!canceled) scrubPositionMs = positionFor(event.x)
                positionMs = if (canceled) positionMs else scrubPositionMs
                scrubbing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onScrubStop(positionMs, canceled)
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
''', encoding='utf-8')


# ---------- Player ----------
p = PLAYER.read_text(encoding='utf-8')
p = p.replace('import android.graphics.Bitmap\n', 'import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\n')
p = p.replace('import android.widget.SeekBar\n', '')

p = replace_once(p,
'''    private val settings: AppSettings,\n    private val startPositionMs: Long = 0L,\n    private val onBack: () -> Unit,''',
'''    private val settings: AppSettings,\n    private val startPositionMs: Long = 0L,\n    private val nextItem: VideoItem? = null,\n    private val onPlayNext: ((VideoItem) -> Unit)? = null,\n    private val onBack: () -> Unit,''', 'player constructor next item')

p = p.replace('    private lateinit var seekBar: SeekBar\n', '    private lateinit var seekBar: SohrTimeBar\n')
p = replace_once(p,
'''    private lateinit var previewTime: TextView\n    private val previewScope''',
'''    private lateinit var previewTime: TextView\n    private lateinit var posterImage: ImageView\n    private lateinit var endOverlay: LinearLayout\n    private val previewScope''', 'player extra views')

p = replace_once(p,
'''    private var previewBitmap: Bitmap? = null\n    private var lastPreviewMs = -1L''',
'''    private var previewBitmap: Bitmap? = null\n    private val previewCache = object : LinkedHashMap<Long, Bitmap>(10, 0.75f, true) {\n        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean {\n            if (size <= 10) return false\n            eldest?.value?.takeIf { it !== previewBitmap }?.recycle()\n            return true\n        }\n    }\n    private var previewRequestId = 0L\n    private var lastPreviewMs = -1L''', 'preview cache fields')

p = p.replace('        root.keepScreenOn = true\n', '        root.keepScreenOn = false\n')

p = replace_once(p,
'''        playerCard.addView(\n            playerView,\n            FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n            )\n        )\n\n        overlay = buildOverlay()''',
'''        playerCard.addView(\n            playerView,\n            FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n            )\n        )\n\n        posterImage = ImageView(activity).apply {\n            scaleType = ImageView.ScaleType.CENTER_CROP\n            setBackgroundColor(Color.BLACK)\n            item.thumbnailPath?.takeIf { it.isNotBlank() }?.let { path ->\n                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { setImageBitmap(it) }\n            }\n        }\n        playerCard.addView(\n            posterImage,\n            FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n            )\n        )\n\n        overlay = buildOverlay()''', 'poster insertion')

p = replace_once(p,
'''        previewBubble = buildSeekPreview()\n        playerCard.addView(\n            previewBubble,\n            FrameLayout.LayoutParams(dp(174), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {\n                bottomMargin = dp(58)\n            }\n        )\n\n        val loadControl''',
'''        previewBubble = buildSeekPreview()\n        playerCard.addView(\n            previewBubble,\n            FrameLayout.LayoutParams(dp(174), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {\n                bottomMargin = dp(58)\n            }\n        )\n\n        endOverlay = buildEndOverlay()\n        playerCard.addView(\n            endOverlay,\n            FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n            )\n        )\n\n        val loadControl''', 'end overlay insertion')

p = p.replace('                18_000,\n                75_000,\n                1_200,\n                4_000', '                15_000,\n                60_000,\n                900,\n                2_500')
p = p.replace('        player.playWhenReady = settings.autoplay\n', '        player.playWhenReady = true\n')

p = replace_once(p,
'''            override fun onIsPlayingChanged(isPlaying: Boolean) {\n                updatePlayIcon()\n                if (isPlaying && !playbackCounted) {''',
'''            override fun onIsPlayingChanged(isPlaying: Boolean) {\n                updatePlayIcon()\n                root.keepScreenOn = isPlaying\n                if (isPlaying && ::endOverlay.isInitialized) endOverlay.visibility = View.GONE\n                if (isPlaying && !playbackCounted) {''', 'dynamic keep screen on')

p = replace_once(p,
'''            override fun onPlaybackStateChanged(playbackState: Int) {\n                updateProgress()''',
'''            override fun onRenderedFirstFrame() {\n                posterImage.animate().cancel()\n                posterImage.animate()\n                    .alpha(0f)\n                    .setDuration(if (settings.animations) 120L else 0L)\n                    .withEndAction { posterImage.visibility = View.GONE }\n                    .start()\n            }\n\n            override fun onPlaybackStateChanged(playbackState: Int) {\n                updateProgress()''', 'first frame callback')

p = replace_once(p,
'''                if (playbackState == Player.STATE_ENDED) {\n                    settings.clearPlaybackPosition(item.messageId)\n                }''',
'''                if (playbackState == Player.STATE_ENDED) {\n                    settings.clearPlaybackPosition(item.messageId)\n                    root.keepScreenOn = false\n                    showEndOverlay()\n                } else if (playbackState == Player.STATE_READY && !player.isPlaying) {\n                    root.keepScreenOn = false\n                }''', 'ended state')

old_seek = '''        seekBar = SeekBar(activity).apply {\n            max = 1000\n            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#A78BFA"))\n            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)\n            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#66FFFFFF"))\n            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {\n                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {\n                    if (!fromUser) return\n                    val duration = player.duration\n                    if (duration <= 0) return\n                    val target = duration * progress / 1000L\n                    previewTime.text = formatMs(target)\n                    currentTime.text = formatMs(target)\n                    showPreview()\n                    requestPreview(target)\n                }\n\n                override fun onStartTrackingTouch(seekBar: SeekBar?) {\n                    dragging = true\n                    showPreview()\n                }\n\n                override fun onStopTrackingTouch(seekBar: SeekBar?) {\n                    val duration = player.duration\n                    if (duration > 0) {\n                        val target = duration * (seekBar?.progress ?: 0) / 1000L\n                        player.seekTo(target)\n                        currentTime.text = formatMs(target)\n                    }\n                    dragging = false\n                    handler.postDelayed({ hidePreview() }, 120)\n                }\n            })\n        }'''
new_seek = '''        seekBar = SohrTimeBar(activity).apply {\n            listener = object : SohrTimeBar.Listener {\n                override fun onScrubStart(positionMs: Long) {\n                    dragging = true\n                    showPreview()\n                    updatePreviewUi(positionMs, 0f)\n                }\n\n                override fun onScrubMove(positionMs: Long, fraction: Float) {\n                    updatePreviewUi(positionMs, fraction)\n                    requestPreview(positionMs)\n                }\n\n                override fun onScrubStop(positionMs: Long, canceled: Boolean) {\n                    if (!canceled) {\n                        player.seekTo(positionMs)\n                        currentTime.text = formatMs(positionMs)\n                    }\n                    dragging = false\n                    handler.postDelayed({ hidePreview() }, 90L)\n                }\n            }\n        }'''
p = replace_once(p, old_seek, new_seek, 'youtube timebar')
p = p.replace('        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))', '        bottom.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))')

p = replace_once(p,
'''    private fun requestPreview(positionMs: Long) {\n        if (positionMs < 0) return\n        if (lastPreviewMs >= 0 && kotlin.math.abs(positionMs - lastPreviewMs) < 750) return\n        lastPreviewMs = positionMs\n\n        previewJob?.cancel()\n        previewJob = previewScope.launch {\n            delay(70)\n            val bitmap = runCatching {''',
'''    private fun updatePreviewUi(positionMs: Long, fraction: Float) {\n        previewTime.text = formatMs(positionMs)\n        currentTime.text = formatMs(positionMs)\n        val maxShift = ((playerCard.width - dp(174)) / 2f).coerceAtLeast(0f)\n        previewBubble.translationX = ((fraction.coerceIn(0f, 1f) - 0.5f) * 2f * maxShift)\n    }\n\n    private fun requestPreview(positionMs: Long) {\n        if (positionMs < 0) return\n        val bucket = (positionMs / 2_000L) * 2_000L\n        previewCache[bucket]?.takeIf { !it.isRecycled }?.let { cached ->\n            previewImage.setImageBitmap(cached)\n            previewBitmap = cached\n            return\n        }\n        if (lastPreviewMs >= 0 && kotlin.math.abs(bucket - lastPreviewMs) < 1_500) return\n        lastPreviewMs = bucket\n        val requestId = ++previewRequestId\n\n        previewJob?.cancel()\n        previewJob = previewScope.launch {\n            delay(20)\n            val bitmap = runCatching {''', 'preview request header')

p = p.replace('                        positionMs * 1000L,', '                        bucket * 1000L,', 1)
p = replace_once(p,
'''                    if (!dragging) {\n                        bitmap.recycle()\n                        return@withContext\n                    }\n                    previewImage.setImageBitmap(bitmap)\n                    previewBitmap?.takeIf { it !== bitmap }?.recycle()\n                    previewBitmap = bitmap''',
'''                    if (!dragging || requestId != previewRequestId) {\n                        bitmap.recycle()\n                        return@withContext\n                    }\n                    previewCache[bucket] = bitmap\n                    previewImage.setImageBitmap(bitmap)\n                    previewBitmap = bitmap''', 'preview result cache')

insert_before = '''    private fun buildDetails(): LinearLayout {'''
end_methods = r'''    private fun buildEndOverlay(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE

            val label = TextView(activity).apply {
                text = if (nextItem != null) "Видео закончилось" else "Конец видео"
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(12))
            }
            val button = TextView(activity).apply {
                text = if (nextItem != null) "▶  Следующее видео" else "↻  Смотреть сначала"
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = rounded("#8B5CF6", 22)
                setOnClickListener {
                    pulse(this)
                    val next = nextItem
                    if (next != null && onPlayNext != null) {
                        onPlayNext.invoke(next)
                    } else {
                        endOverlay.visibility = View.GONE
                        player.seekTo(0L)
                        player.play()
                    }
                }
            }
            addView(label)
            addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showEndOverlay() {
        hideOverlay()
        endOverlay.alpha = 0f
        endOverlay.visibility = View.VISIBLE
        endOverlay.animate().cancel()
        endOverlay.animate().alpha(1f).setDuration(if (settings.animations) 140L else 0L).start()
    }

'''
p = replace_once(p, insert_before, end_methods + insert_before, 'end overlay methods')

p = replace_once(p,
'''    private fun scheduleProgress() {\n        handler.postDelayed({\n            updateProgress()\n            scheduleProgress()\n        }, 350)\n    }''',
'''    private fun scheduleProgress() {\n        handler.postDelayed({\n            updateProgress()\n            scheduleProgress()\n        }, if (player.isPlaying) 250L else 850L)\n    }''', 'adaptive progress polling')

p = replace_once(p,
'''                seekBar.progress = ((player.currentPosition * 1000L) / duration).toInt().coerceIn(0, 1000)\n                totalTime.text = formatMs(duration)''',
'''                seekBar.setProgress(player.currentPosition, duration, player.bufferedPosition)\n                totalTime.text = formatMs(duration)''', 'timebar progress update')

p = replace_once(p,
'''        previewImage.setImageDrawable(null)\n        previewBitmap?.recycle()\n        previewBitmap = null''',
'''        previewImage.setImageDrawable(null)\n        previewCache.values.toSet().forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }\n        previewCache.clear()\n        previewBitmap = null''', 'preview cleanup')

PLAYER.write_text(p, encoding='utf-8')


# ---------- Main activity ----------
m = MAIN.read_text(encoding='utf-8')

# Telegram order inside each daily collection: oldest upload first, newest last.
m = m.replace('DayCollection(date, dayVideos.sortedByDescending { it.date })', 'DayCollection(date, dayVideos.sortedBy { it.date })')
m = m.replace('val sortedVideos = collection.videos.sortedByDescending { it.date }', 'val sortedVideos = collection.videos.sortedBy { it.date }')

# In-place refresh must not rebuild/animate the whole screen when nothing changed.
m = replace_once(m,
'''                withContext(Dispatchers.Main) {\n                    currentVideos = preparedVideos\n                    currentDay = null\n                    feedRefreshCompletedFlash = inPlace\n                    showFeed(preparedVideos)\n                }''',
'''                withContext(Dispatchers.Main) {\n                    val changed = currentVideos.map { it.messageId } != preparedVideos.map { it.messageId }\n                    currentVideos = preparedVideos\n                    if (inPlace && !changed) {\n                        setFeedRefreshLoading(false, "Готово")\n                        feedRefreshButton?.postDelayed({\n                            if (feedRefreshButton?.isEnabled == true) {\n                                setFeedRefreshLoading(false, "Проверить новые")\n                            }\n                        }, 900L)\n                    } else {\n                        currentDay = null\n                        feedRefreshCompletedFlash = inPlace\n                        if (inPlace) suppressNextRootAnimation = true\n                        showFeed(preparedVideos)\n                    }\n                }''', 'smooth in-place refresh')

# Compute/prefetch next item and pass it into PlayerScreen.
m = replace_once(m,
'''        server.prefetch(item)\n        playerScreen?.destroy()''',
'''        server.prefetch(item)\n        val orderedForPlayback = (currentDay?.videos ?: currentVideos).sortedBy { it.date }\n        val currentIndex = orderedForPlayback.indexOfFirst { it.messageId == item.messageId }\n        val nextItem = if (currentIndex >= 0) orderedForPlayback.getOrNull(currentIndex + 1) else null\n        nextItem?.let { server.prefetch(it) }\n        playerScreen?.destroy()''', 'next item calculation')

m = replace_once(m,
'''            settings = settings,\n            startPositionMs = resumePositionMs,\n            onBack = { onBackPressedDispatcher.onBackPressed() },''',
'''            settings = settings,\n            startPositionMs = resumePositionMs,\n            nextItem = nextItem,\n            onPlayNext = { next -> openPlayer(next) },\n            onBack = { onBackPressedDispatcher.onBackPressed() },''', 'pass next item')

# Hardware layers during short navigation animations reduce jank on heavier screens.
m = m.replace(
'''                old.animate().cancel()\n                content.animate().cancel()\n\n                val telegramInterpolator''',
'''                old.animate().cancel()\n                content.animate().cancel()\n                old.setLayerType(View.LAYER_TYPE_HARDWARE, null)\n                content.setLayerType(View.LAYER_TYPE_HARDWARE, null)\n\n                val telegramInterpolator''', 1)
m = m.replace(
'''                            old.alpha = 1f\n                            old.translationX = 0f\n                            if (old.parent === host) host.removeView(old)''',
'''                            old.alpha = 1f\n                            old.translationX = 0f\n                            old.setLayerType(View.LAYER_TYPE_NONE, null)\n                            content.setLayerType(View.LAYER_TYPE_NONE, null)\n                            if (old.parent === host) host.removeView(old)''', 1)
m = m.replace(
'''                        .withEndAction {\n                            if (old.parent === host) host.removeView(old)\n                        }''',
'''                        .withEndAction {\n                            old.setLayerType(View.LAYER_TYPE_NONE, null)\n                            content.setLayerType(View.LAYER_TYPE_NONE, null)\n                            if (old.parent === host) host.removeView(old)\n                        }''', 1)

m = replace_once(m,
'''        old.animate().cancel()\n        view.animate().cancel()\n\n        val telegramInterpolator''',
'''        old.animate().cancel()\n        view.animate().cancel()\n        old.setLayerType(View.LAYER_TYPE_HARDWARE, null)\n        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)\n\n        val telegramInterpolator''', 'root hardware layers')
m = m.replace(
'''                    old.alpha = 1f\n                    old.translationX = 0f\n                    if (old.parent === root) root.removeView(old)''',
'''                    old.alpha = 1f\n                    old.translationX = 0f\n                    old.setLayerType(View.LAYER_TYPE_NONE, null)\n                    view.setLayerType(View.LAYER_TYPE_NONE, null)\n                    if (old.parent === root) root.removeView(old)''', 1)
m = m.replace(
'''                .withEndAction {\n                    if (old.parent === root) root.removeView(old)\n                }''',
'''                .withEndAction {\n                    old.setLayerType(View.LAYER_TYPE_NONE, null)\n                    view.setLayerType(View.LAYER_TYPE_NONE, null)\n                    if (old.parent === root) root.removeView(old)\n                }''', 1)

MAIN.write_text(m, encoding='utf-8')


# ---------- Version ----------
g = GRADLE.read_text(encoding='utf-8')
g = g.replace('// Build marker: SOHR 3.0.9 Telegram navigation', '// Build marker: SOHR 3.0.10 player polish')
g = g.replace('versionCode = 309', 'versionCode = 310')
g = g.replace('versionName = "3.0.9-beta"', 'versionName = "3.0.10-beta"')
GRADLE.write_text(g, encoding='utf-8')

print('SOHR 3.0.10 patch applied')
