from pathlib import Path
import re

main_path = Path('native/app/src/main/java/com/unknokable/videosohranenki/MainActivity.kt')
player_path = Path('native/app/src/main/java/com/unknokable/videosohranenki/PlayerScreen.kt')
main = main_path.read_text()
player = player_path.read_text()

def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f'Missing expected block: {label}')
    return text.replace(old, new, 1)

main = rep(main, '''                if (isPlayerScreen) {
                    playerScreen?.destroy()
                    playerScreen = null
                    isPlayerScreen = false
                    pendingRootSlide = -1
                    val day = currentDay
                    if (day != null && day.videos.isNotEmpty()) showDayCollection(day) else { currentDay = null; showFeed(currentVideos) }
                    return
                }''', '''                if (isPlayerScreen) {
                    val outgoingPlayer = playerScreen
                    playerScreen = null
                    isPlayerScreen = false
                    pendingRootSlide = -1
                    val day = currentDay
                    if (day != null && day.videos.isNotEmpty()) showDayCollection(day) else { currentDay = null; showFeed(currentVideos) }
                    root.postDelayed({ outgoingPlayer?.destroy() }, if (settings.animations) 280L else 0L)
                    return
                }''', 'smooth player back')

for flag in ('isSettingsScreen', 'isAccountScreen', 'isStreakScreen'):
    main = rep(main, f'''                if ({flag}) {{
                    {flag} = false
                    showFeed(currentVideos)
                    return
                }}''', f'''                if ({flag}) {{
                    {flag} = false
                    pendingRootSlide = -1
                    showFeed(currentVideos)
                    return
                }}''', f'back {flag}')
main = rep(main, '''                if (currentDay != null) {
                    currentDay = null
                    showFeed(currentVideos)
                    return
                }''', '''                if (currentDay != null) {
                    currentDay = null
                    pendingRootSlide = -1
                    showFeed(currentVideos)
                    return
                }''', 'collection back')

main = rep(main, '    private var pendingVideoSectionCrossfade = false\n', '    private var pendingVideoSectionCrossfade = false\n    private var pendingVideoSectionDirection = 0\n', 'section direction')
main = rep(main, '''    private fun markVideoWatched(messageId: Long) {
        val prefs = getSharedPreferences("sohr_watched", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(messageId.toString())
        prefs.edit().putStringSet("ids", ids).apply()
    }
''', '''    private fun markVideoWatched(messageId: Long): Boolean {
        val prefs = getSharedPreferences("sohr_watched", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val added = ids.add(messageId.toString())
        if (added) prefs.edit().putStringSet("ids", ids).apply()
        return added
    }

    private fun unmarkVideoWatched(messageId: Long): Boolean {
        val prefs = getSharedPreferences("sohr_watched", MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val removed = ids.remove(messageId.toString())
        if (removed) prefs.edit().putStringSet("ids", ids).apply()
        return removed
    }

    private fun switchVideoSection(section: Int) {
        if (section !in 1..2 || videoSection == section) return
        pendingVideoSectionCrossfade = true
        pendingVideoSectionDirection = if (section > videoSection) 1 else -1
        pendingRootSlide = 0
        videoSection = section
        showFeed(currentVideos)
    }
''', 'watched persistence')

main = rep(main, '''                setOnClickListener {
                    if(videoSection==section) return@setOnClickListener
                    animatePress(this)
                    pendingVideoSectionCrossfade=true
                    pendingRootSlide=0
                    videoSection=section
                    showFeed(currentVideos)
                }
            }
            tabs.addView(tab,LinearLayout.LayoutParams(0,dp(40),1f).apply { if(position>0) marginStart=dp(3) })
        }
        header.addView(tabs,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)))''', '''                var swipeStartX = 0f
                var swipeStartY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { swipeStartX = event.x; swipeStartY = event.y; false }
                        MotionEvent.ACTION_UP -> {
                            val dx = event.x - swipeStartX
                            val dy = event.y - swipeStartY
                            if (kotlin.math.abs(dx) >= dp(46) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                                switchVideoSection(if (dx < 0f) 2 else 1)
                                true
                            } else false
                        }
                        else -> false
                    }
                }
                setOnClickListener {
                    if(videoSection==section) return@setOnClickListener
                    animatePress(this)
                    switchVideoSection(section)
                }
            }
            tabs.addView(tab,LinearLayout.LayoutParams(0,dp(40),1f).apply { if(position>0) marginStart=dp(3) })
        }
        header.addView(tabs,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)))''', 'tabs swipe')

main = rep(main, '''        val sectionCrossfade = pendingVideoSectionCrossfade
        pendingVideoSectionCrossfade = false
        content.alpha = 1f''', '''        val sectionCrossfade = pendingVideoSectionCrossfade
        val sectionDirection = pendingVideoSectionDirection
        pendingVideoSectionCrossfade = false
        pendingVideoSectionDirection = 0
        content.alpha = 1f''', 'direction capture')
main = rep(main, '''                if (sectionCrossfade) {
                    content.alpha = 0f
                    content.translationX = 0f
                    old.alpha = 1f
                    old.translationX = 0f''', '''                if (sectionCrossfade) {
                    val travel = dp(18).toFloat() * if (sectionDirection == 0) 1 else sectionDirection
                    content.alpha = 0f
                    content.translationX = travel
                    old.alpha = 1f
                    old.translationX = 0f''', 'direction animation')

main = rep(main, '''            isWatched = isVideoWatched(item.messageId),
            onMarkWatched = { watched ->
                markVideoWatched(watched.messageId)
                if (videoSection != 2) currentDay = currentDay?.copy(videos = currentDay?.videos?.filterNot { it.messageId == watched.messageId } ?: emptyList())
            },''', '''            isWatched = isVideoWatched(item.messageId),
            onWatchedChange = { watched, shouldBeWatched ->
                if (shouldBeWatched) markVideoWatched(watched.messageId) else unmarkVideoWatched(watched.messageId)
                val noLongerBelongsToOpenSection = (videoSection == 1 && shouldBeWatched) || (videoSection == 2 && !shouldBeWatched)
                if (noLongerBelongsToOpenSection) currentDay = currentDay?.copy(videos = currentDay?.videos?.filterNot { it.messageId == watched.messageId } ?: emptyList())
            },''', 'watched callback')

player = rep(player, '    private val onMarkWatched: ((VideoItem) -> Unit)? = null,\n', '    private val onWatchedChange: ((VideoItem, Boolean) -> Unit)? = null,\n', 'callback signature')
start = player.index('        var watched=isWatched\n        lateinit var watchedButton:TextView')
end = player.index('        val shareButton=actionPill', start)
new_actions = '''        var watched=isWatched
        lateinit var watchedButton:TextView
        lateinit var restoreButton:TextView
        fun syncWatchedActions() {
            watchedButton.alpha = if (watched) .72f else 1f
            restoreButton.visibility = if (watched) View.VISIBLE else View.GONE
            restoreButton.alpha = if (watched) 1f else 0f
        }
        watchedButton=actionPill("✓  Просмотрено") {
            if(watched) {
                Toast.makeText(activity,"Видео уже добавлено в просмотренное",Toast.LENGTH_SHORT).show()
                watchedButton.animate().cancel(); watchedButton.animate().scaleX(1.03f).scaleY(1.03f).setDuration(if(settings.animations)90L else 0L).withEndAction { watchedButton.animate().scaleX(1f).scaleY(1f).setDuration(if(settings.animations)110L else 0L).start() }.start()
                return@actionPill
            }
            ModernDialogs.showConfirm(activity,palette,"Отметить просмотренным?","Видео переместится в раздел «Просмотренное» и исчезнет из обычных сборников.","Да, просмотрено") {
                watched=true; onWatchedChange?.invoke(item,true); syncWatchedActions()
            }
        }
        restoreButton=actionPill("↩  Вернуть в сборники") {
            if(!watched) return@actionPill
            ModernDialogs.showConfirm(activity,palette,"Вернуть в сборники?","Видео исчезнет из «Просмотренного» и снова появится в обычном сборнике.","Вернуть") {
                watched=false; onWatchedChange?.invoke(item,false); syncWatchedActions()
                Toast.makeText(activity,"Видео возвращено в сборники",Toast.LENGTH_SHORT).show()
            }
        }
        syncWatchedActions()
'''
player = player[:start] + new_actions + player[end:]
needle = 'row.addView(watchedButton,LinearLayout.LayoutParams(0,dp(44),1f))'
if needle not in player:
    raise SystemExit('Missing watchedButton row add')
player = player.replace(needle, needle + '\n        row.addView(restoreButton,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginStart=dp(8) })', 1)

main_path.write_text(main)
player_path.write_text(player)
print('Patch applied successfully')
