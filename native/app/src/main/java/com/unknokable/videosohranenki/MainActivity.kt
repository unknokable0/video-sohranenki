package com.unknokable.videosohranenki

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewAnimationUtils
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.i18n.phonenumbers.PhoneNumberUtil
import coil.load
import coil.transform.CircleCropTransformation
import io.github.tdlibandroid.ktx.TdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var client: TdClient
    private lateinit var root: FrameLayout
    private var streamServer: TelegramStreamServer? = null
    private var playerScreen: PlayerScreen? = null
    private lateinit var settings: AppSettings
    private lateinit var streakTracker: StreakTracker
    private lateinit var updateManager: SohrUpdateManager
    private var pendingUpdateApk: File? = null
    private var waitingForInstallPermission = false
    private var updateProgressLabel: TextView? = null
    private var currentVideos: List<VideoItem> = emptyList()
    private var currentDay: DayCollection? = null
    private var isPlayerScreen = false
    private var isSettingsScreen = false
    private var isAccountScreen = false
    private var isStreakScreen = false
    private var fullScreen = false
    private var channelChatId: Long = 0L
    private var pendingCodeState: TdApi.AuthorizationStateWaitCode? = null
    private var authSubmitButton: Button? = null
    private var authErrorView: TextView? = null
    private var requestedPhoneNumber: String? = null
    private var authResetInProgress = false
    private var loadJob: kotlinx.coroutines.Job? = null
    private var reloadRequested = false
    private var suppressNextRootAnimation = false
    private var currentPrimaryTab = SohrTab.VIDEOS
    private var pendingRootSlide = 0
    private var primaryShell: LinearLayout? = null
    private var primaryContentHost: FrameLayout? = null
    private var primaryNav: SohrBottomNavView? = null
    private var primaryShellLightTheme: Boolean? = null
    private var startupPhase = true
    private var startupStatusView: TextView? = null
    private var completedUpdateNotice: String? = null
    private var feedRefreshButton: LinearLayout? = null
    private var feedRefreshLabel: TextView? = null
    private var feedRefreshLoader: LoadingWaveView? = null
    private var feedRefreshCompletedFlash = false

    private val palette get() = settings.palette()
    private val bg get() = palette.background
    private val panel get() = palette.surface
    private val purple get() = palette.accent
    private val text get() = palette.text
    private val muted get() = palette.muted
    private fun t(key: String): String = AppLanguages.t(settings.languageCode, key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = AppSettings(this)
        streakTracker = StreakTracker(this)
        updateManager = SohrUpdateManager(this)

        val runtimePrefs = getSharedPreferences("sohr_runtime", MODE_PRIVATE)
        val previousVersionCode = runtimePrefs.getInt("last_version_code", 0)
        if (previousVersionCode > 0 && previousVersionCode < BuildConfig.VERSION_CODE) {
            completedUpdateNotice = BuildConfig.VERSION_NAME
        }
        runtimePrefs.edit()
            .putInt("last_version_code", BuildConfig.VERSION_CODE)
            .apply()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (!fullScreen) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                view.setPadding(0, 0, 0, 0)
            }
            insets
        }
        applySystemTheme()
        showStartupSplash()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullScreen) {
                    setFullscreen(false)
                    return
                }
                if (isPlayerScreen) {
                    playerScreen?.destroy()
                    playerScreen = null
                    isPlayerScreen = false
                    currentDay?.let { showDayCollection(it) } ?: showFeed(currentVideos)
                    return
                }
                if (isSettingsScreen) {
                    isSettingsScreen = false
                    showFeed(currentVideos)
                    return
                }
                if (isAccountScreen) {
                    isAccountScreen = false
                    showFeed(currentVideos)
                    return
                }
                if (isStreakScreen) {
                    isStreakScreen = false
                    showFeed(currentVideos)
                    return
                }
                if (currentDay != null) {
                    currentDay = null
                    showFeed(currentVideos)
                    return
                }
                finish()
            }
        })

        if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            showMessage(
                "Не подключены Telegram API ключи",
                "Проверь TELEGRAM_API_ID и TELEGRAM_API_HASH в GitHub Actions Secrets."
            )
            return
        }

        try {
            System.loadLibrary("tdjni")
        } catch (e: Throwable) {
            showMessage("Ошибка TDLib", e.message ?: "Не удалось загрузить tdjni")
            return
        }

        requestedPhoneNumber = settings.authPhone
        val tdlibDir = filesDir.absolutePath + "/tdlib_session_" + settings.authGeneration
        client = TdClient(
            filesDir = tdlibDir,
            verbosityLevel = 1,
            apiId = BuildConfig.TELEGRAM_API_ID,
            apiHash = BuildConfig.TELEGRAM_API_HASH
        )

        lifecycleScope.launch {
            client.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
                    is TdApi.UpdateNewMessage -> {
                        if (channelChatId != 0L && update.message.chatId == channelChatId) {
                            loadVideos()
                        }
                    }
                }
            }
        }

        root.postDelayed({
            showLoading("Подключаем Telegram…")
            client.init()
        }, 620L)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission && updateManager.canRequestInstall()) {
            waitingForInstallPermission = false
            pendingUpdateApk?.takeIf { it.exists() }?.let { apk ->
                root.postDelayed({ launchUpdateInstaller(apk) }, 220L)
            }
        }
    }

    private fun showStartupSplash() {
        val page = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "SOHR"
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }

        val loader = LoadingWaveView(this, purple).apply {
            alpha = 0f
        }

        val status = TextView(this).apply {
            text = "Запускаем SOHR…"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
            alpha = 0f
        }
        startupStatusView = status

        center.addView(
            logo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        center.addView(
            loader,
            LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                topMargin = dp(18)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        center.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        page.addView(
            center,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.removeAllViews()
        root.addView(
            page,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320L)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .start()

        loader.animate()
            .alpha(1f)
            .setStartDelay(120L)
            .setDuration(220L)
            .start()
        status.animate()
            .alpha(1f)
            .setStartDelay(200L)
            .setDuration(220L)
            .start()
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> runOnUiThread {
                pendingCodeState = null
                showPhoneLogin()
            }
            is TdApi.AuthorizationStateWaitCode -> runOnUiThread {
                pendingCodeState = state
                val saved = settings.authPhone
                val actual = normalizePhone(state.codeInfo.phoneNumber)
                if (saved.isNullOrBlank()) {
                    resetTelegramAuthorization("Незавершённый старый вход")
                } else if (actual.isNotBlank() && normalizePhone(saved) != actual) {
                    resetTelegramAuthorization("Номер старой сессии не совпал")
                } else {
                    requestedPhoneNumber = saved
                    showCodeLogin(state)
                }
            }
            is TdApi.AuthorizationStateWaitEmailAddress -> runOnUiThread { showEmailAddressLogin() }
            is TdApi.AuthorizationStateWaitEmailCode -> runOnUiThread { showEmailCodeLogin(state) }
            is TdApi.AuthorizationStateWaitPassword -> runOnUiThread { showPasswordLogin(state.passwordHint ?: "") }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> runOnUiThread {
                resetTelegramAuthorization("Возврат к входу по номеру")
            }
            is TdApi.AuthorizationStateReady -> {
                settings.authPhone = null
                ensureStreamServer()
                cleanupStorage()
                loadVideos()
            }
            is TdApi.AuthorizationStateLoggingOut -> runOnUiThread { showLoading("Выходим…") }
            is TdApi.AuthorizationStateClosing -> runOnUiThread { showLoading("Закрываем соединение…") }
            is TdApi.AuthorizationStateClosed -> Unit
        }
    }

    private fun showPhoneLogin() {
        showLoading(t("loading_countries"))
        lifecycleScope.launch {
            val phoneUtil = PhoneNumberUtil.getInstance()
            val regions = runCatching {
                client.send(TdApi.GetCountries()).countries
                    .asSequence()
                    .filter { !it.isHidden && it.callingCodes.isNotEmpty() }
                    .mapNotNull { info ->
                        val code = info.callingCodes.firstOrNull()
                            ?.filter { ch -> ch.isDigit() }
                            ?.toIntOrNull()
                            ?: return@mapNotNull null
                        PhoneCountry(
                            region = info.countryCode.uppercase(Locale.ROOT),
                            name = info.name.ifBlank {
                                Locale("", info.countryCode).getDisplayCountry(Locale("ru"))
                            },
                            dialCode = code,
                            flag = countryFlag(info.countryCode)
                        )
                    }
                    .distinctBy { pair -> pair.region to pair.dialCode }
                    .sortedBy { country -> country.name.lowercase(Locale("ru")) }
                    .toList()
            }.getOrElse {
                phoneUtil.supportedRegions
                    .map { region ->
                        PhoneCountry(
                            region = region,
                            name = Locale("", region).getDisplayCountry(Locale("ru")).ifBlank { region },
                            dialCode = phoneUtil.getCountryCodeForRegion(region),
                            flag = countryFlag(region)
                        )
                    }
                    .sortedBy { country -> country.name }
            }

            withContext(Dispatchers.Main) {
                renderPhoneLogin(regions)
            }
        }
    }

    private fun renderPhoneLogin(regions: List<PhoneCountry>) {
        startupPhase = false
        startupStatusView = null
        val phoneUtil = PhoneNumberUtil.getInstance()
        var selected = detectCountry(regions)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBg(panel, 24)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "SOHR"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg(purple, 16)
        }

        val languageButton = TextView(this).apply {
            text = AppLanguages.byCode(settings.languageCode).shortLabel
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            background = roundedBg(palette.surfaceAlt, 14)
            setOnClickListener {
                animatePress(this)
                ModernDialogs.showChoices(
                    context = this@MainActivity,
                    palette = palette,
                    title = t("choose_language"),
                    options = AppLanguages.all.map { it.label },
                    selected = AppLanguages.all.indexOfFirst { it.code == settings.languageCode }.coerceAtLeast(0)
                ) { which ->
                    settings.languageCode = AppLanguages.all[which].code
                    renderPhoneLogin(regions)
                }
            }
        }

        topRow.addView(brand, LinearLayout.LayoutParams(dp(64), dp(52)))
        topRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topRow.addView(languageButton, LinearLayout.LayoutParams(dp(58), dp(44)))

        val title = TextView(this).apply {
            text = t("login")
            textSize = 29f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = t("choose_country")
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(7), 0, dp(16))
        }

        val country = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(palette.surfaceAlt, 16)
        }

        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            background = roundedBg(palette.surfaceAlt, 16)
        }

        val prefix = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(4), 0, dp(10), 0)
        }

        val input = EditText(this).apply {
            hint = t("phone")
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setSingleLine(true)
            background = null
        }

        var formatter = phoneUtil.getAsYouTypeFormatter(selected.region)
        var formatting = false

        fun applyCountry() {
            country.text = "${selected.flag}  ${selected.name}   +${selected.dialCode}"
            prefix.text = "+${selected.dialCode}"
            formatter = phoneUtil.getAsYouTypeFormatter(selected.region)
            val digits = input.text.toString().filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                formatting = true
                formatter.clear()
                var formatted = ""
                digits.forEach { ch -> formatted = formatter.inputDigit(ch) }
                input.setText(formatted)
                input.setSelection(formatted.length)
                formatting = false
            }
        }

        country.setOnClickListener {
            animatePress(country)
            showCountryPicker(
                regions = regions,
                selected = selected
            ) { picked ->
                selected = picked
                applyCountry()
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (formatting) return
                val digits = s?.toString().orEmpty().filter { it.isDigit() }
                formatter.clear()
                var formatted = ""
                digits.take(18).forEach { ch -> formatted = formatter.inputDigit(ch) }
                if (formatted != s?.toString().orEmpty()) {
                    formatting = true
                    input.setText(formatted)
                    input.setSelection(formatted.length)
                    formatting = false
                }
            }
        })

        val error = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#FF6B81"))
            visibility = View.GONE
            setPadding(dp(2), dp(8), dp(2), 0)
        }
        authErrorView = error

        val submit = Button(this).apply {
            text = t("continue")
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 16)
            setOnClickListener {
                animatePress(this)
                val national = input.text.toString().filter { it.isDigit() }
                if (national.isBlank()) {
                    error.text = t("phone_empty")
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                val normalized = "+${selected.dialCode}$national"
                error.visibility = View.GONE
                isEnabled = false
                alpha = 0.72f

                lifecycleScope.launch {
                    try {
                        val parsed = phoneUtil.parse(normalized, selected.region)
                        if (!phoneUtil.isValidNumber(parsed)) {
                            throw IllegalArgumentException(t("phone_check"))
                        }

                        requestedPhoneNumber = normalized
                        settings.authPhone = normalized

                        val authSettings = TdApi.PhoneNumberAuthenticationSettings(
                            false,
                            true,
                            false,
                            true,
                            false,
                            null,
                            emptyArray()
                        )
                        client.send(TdApi.SetAuthenticationPhoneNumber(normalized, authSettings))
                    } catch (e: Exception) {
                        error.text = friendlyAuthError(e.message)
                        error.visibility = View.VISIBLE
                        isEnabled = true
                        alpha = 1f
                    }
                }
            }
        }
        authSubmitButton = submit

        val help = TextView(this).apply {
            text = if (settings.languageCode == "ru") {
                "Выбери страну, введи номер и нажми «Продолжить». Telegram сам выберет доступный способ подтверждения для этого номера."
            } else {
                t("choose_country")
            }
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(2), dp(12), dp(2), 0)
            setLineSpacing(0f, 1.12f)
        }

        card.addView(topRow)
        card.addView(title)
        card.addView(subtitle)
        card.addView(country, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))
        card.addView(phoneRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply { topMargin = dp(10) })
        phoneRow.addView(prefix)
        phoneRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        card.addView(submit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply { topMargin = dp(10) })
        card.addView(error)
        card.addView(help)

        container.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        applyCountry()
        replaceRoot(container)
        input.requestFocus()
    }

    private data class PhoneCountry(
        val region: String,
        val name: String,
        val dialCode: Int,
        val flag: String
    )


    private fun showCountryPicker(
        regions: List<PhoneCountry>,
        selected: PhoneCountry,
        onSelected: (PhoneCountry) -> Unit
    ) {
        val dialog = Dialog(this)
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = roundedBg(panel, 24)
        }

        val title = TextView(this).apply {
            text = t("choose_country_title")
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), 0, dp(2), dp(10))
        }

        val search = EditText(this).apply {
            hint = t("country_search")
            setSingleLine(true)
            textSize = 15f
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(palette.surfaceAlt, 15)
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }

        fun render(query: String) {
            list.removeAllViews()
            val q = query.trim().lowercase(Locale.getDefault())
            val filtered = if (q.isBlank()) {
                regions
            } else {
                regions.filter { region ->
                    region.name.lowercase(Locale.getDefault()).contains(q) ||
                        region.region.lowercase(Locale.ROOT).contains(q) ||
                        ("+" + region.dialCode).contains(q) ||
                        region.dialCode.toString().contains(q)
                }
            }

            filtered.forEach { region ->
                val row = TextView(this).apply {
                    text = "${region.flag}   ${region.name}   +${region.dialCode}"
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(this@MainActivity.text)
                    setTypeface(
                        typeface,
                        if (region.region == selected.region) Typeface.BOLD else Typeface.NORMAL
                    )
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    background = roundedBg(
                        if (region.region == selected.region) palette.surfaceAlt else Color.TRANSPARENT,
                        13
                    )
                    setOnClickListener {
                        animatePress(this)
                        onSelected(region)
                        dialog.dismiss()
                    }
                }
                list.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(4) }
                )
            }

            if (filtered.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = t("nothing_found")
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                    setPadding(0, dp(24), 0, dp(24))
                })
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                render(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        wrapper.addView(title)
        wrapper.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )
        wrapper.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(520)
            ).apply { topMargin = dp(10) }
        )

        dialog.setContentView(wrapper)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        render("")
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        search.requestFocus()
    }

    private fun detectCountry(countries: List<PhoneCountry>): PhoneCountry {
        val telephony = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOf(
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
            Locale.getDefault().country
        ).mapNotNull { it?.uppercase(Locale.ROOT)?.takeIf { value -> value.length == 2 } }

        val region = candidates.firstOrNull { code -> countries.any { it.region == code } }
            ?: "PL"
        return countries.firstOrNull { it.region == region }
            ?: countries.first()
    }

    private fun countryFlag(region: String): String {
        if (region.length != 2) return ""
        val upper = region.uppercase(Locale.ROOT)
        val first = upper[0].code - 'A'.code + 0x1F1E6
        val second = upper[1].code - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun showCodeLogin(state: TdApi.AuthorizationStateWaitCode) {
        val info = state.codeInfo
        val expected = requestedPhoneNumber
        val actual = normalizePhone(info.phoneNumber)
        if (!expected.isNullOrBlank() && actual.isNotBlank() && normalizePhone(expected) != actual) {
            authErrorView?.apply {
                text = "Telegram ждёт код для другого номера. Авторизация будет сброшена."
                visibility = View.VISIBLE
            }
            resetTelegramAuthorization("Номер авторизации не совпал с введённым")
            return
        }
        val delivery = authCodeDeliveryLabel(info.type?.javaClass?.simpleName.orEmpty())
        val nextDelivery = authCodeDeliveryLabel(info.nextType?.javaClass?.simpleName.orEmpty())
        val timeout = info.timeout.coerceAtLeast(0)

        val extra = buildString {
            append("Код отправлен: ")
            append(delivery)
            if (info.phoneNumber.isNotBlank()) append("\nНомер: ${info.phoneNumber}")
            if (timeout > 0 && nextDelivery.isNotBlank()) {
                append("\nПовторная отправка через $timeout сек. Следующий способ: $nextDelivery.")
            } else if (nextDelivery.isNotBlank()) {
                append("\nМожно запросить код ещё раз: $nextDelivery.")
            }
        }

        showAuthForm(
            title = "Код подтверждения",
            subtitle = extra,
            hint = "Код",
            inputType = InputType.TYPE_CLASS_NUMBER,
            button = "Продолжить",
            footer = "Если код не появился, сначала проверь официальный Telegram на других устройствах. Затем попробуй «Отправить код ещё раз».",
            showBack = true,
            secondaryButton = "Отправить код ещё раз",
            onBack = { resetTelegramAuthorization("Смена номера") },
            onSecondary = {
                launchRequest {
                    client.send(TdApi.ResendAuthenticationCode(null))
                }
            }
        ) { value ->
            val code = value.trim()
            if (code.length < 3 || code.any { !it.isDigit() }) {
                throw IllegalArgumentException("Проверь код подтверждения")
            }
            client.send(TdApi.CheckAuthenticationCode(code))
        }
    }

    private fun showEmailAddressLogin() {
        showAuthForm(
            title = "Email для входа",
            subtitle = "Telegram просит подтвердить вход через email.",
            hint = "name@example.com",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            button = "Отправить код",
            footer = "Введи email, который Telegram просит для этой авторизации. Код придёт на него.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена способа входа") }
        ) { value ->
            val email = value.trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                throw IllegalArgumentException("Проверь адрес email")
            }
            client.send(TdApi.SetAuthenticationEmailAddress(email))
        }
    }

    private fun showEmailCodeLogin(state: TdApi.AuthorizationStateWaitEmailCode) {
        val pattern = state.codeInfo?.emailAddressPattern.orEmpty()
        showAuthForm(
            title = "Код из email",
            subtitle = if (pattern.isBlank()) {
                "Telegram отправил код на email."
            } else {
                "Telegram отправил код на $pattern"
            },
            hint = "Код",
            inputType = InputType.TYPE_CLASS_NUMBER,
            button = "Продолжить",
            footer = "Если письма нет, проверь Спам/Промоакции и подожди немного.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена способа входа") }
        ) { value ->
            val code = value.trim()
            if (code.length < 3) throw IllegalArgumentException("Проверь код из email")
            client.send(
                TdApi.CheckAuthenticationEmailCode(
                    TdApi.EmailAddressAuthenticationCode(code)
                )
            )
        }
    }

    private fun showPasswordLogin(hint: String) {
        showAuthForm(
            title = "Облачный пароль",
            subtitle = if (hint.isBlank()) "На аккаунте включена двухэтапная проверка." else "Подсказка: $hint",
            hint = "Пароль",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            button = "Войти",
            footer = "Это пароль двухэтапной защиты Telegram. Он не сохраняется в приложении.",
            showBack = true,
            onBack = { resetTelegramAuthorization("Смена номера") }
        ) { value ->
            if (value.isBlank()) throw IllegalArgumentException("Введи пароль")
            client.send(TdApi.CheckAuthenticationPassword(value))
        }
    }

    private fun normalizePhone(value: String): String =
        value.filter { it.isDigit() }

    private fun resetTelegramAuthorization(reason: String) {
        if (authResetInProgress) return
        authResetInProgress = true
        lifecycleScope.launch {
            showLoading("Сбрасываем старый вход…")
            val oldGeneration = settings.authGeneration
            settings.authGeneration = oldGeneration + 1
            settings.authPhone = null
            requestedPhoneNumber = null

            runCatching { client.close() }
            kotlinx.coroutines.delay(300)

            runCatching {
                java.io.File(filesDir, "tdlib_session_" + oldGeneration).deleteRecursively()
            }
            recreate()
        }
    }

    private fun authCodeDeliveryLabel(className: String): String {
        val name = className.lowercase()
        return when {
            "telegrammessage" in name -> "в Telegram"
            "sms" in name -> "по SMS"
            "call" in name -> "телефонным звонком"
            "fragment" in name -> "через Fragment"
            "email" in name -> "на email"
            name.isBlank() -> "Telegram"
            else -> "Telegram ($className)"
        }
    }

    private fun showAuthForm(
        title: String,
        subtitle: String,
        hint: String,
        inputType: Int,
        button: String,
        footer: String? = null,
        showBack: Boolean = false,
        secondaryButton: String? = null,
        onBack: (() -> Unit)? = null,
        onSecondary: (() -> Unit)? = null,
        onSubmit: suspend (String) -> Unit
    ) {
        startupPhase = false
        startupStatusView = null

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBg(panel, 24)
        }

        val brand = TextView(this).apply {
            text = "SOHR"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg(purple, 16)
        }
        if (showBack) {
            val back = TextView(this).apply {
                text = "‹  Назад"
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(palette.surfaceAlt, 14)
                setOnClickListener {
                    animatePress(this)
                    onBack?.invoke()
                }
            }
            card.addView(back, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply { bottomMargin = dp(4) })
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 29f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, 0)
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 15f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(24))
        }
        val isPassword = (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(15), dp(14), if (isPassword) dp(52) else dp(15), dp(14))
            background = roundedBg(palette.surfaceAlt, 16)
            if (isPassword) transformationMethod = PasswordTransformationMethod.getInstance()
        }

        val inputContainer = FrameLayout(this).apply {
            background = roundedBg(palette.surfaceAlt, 16)
        }
        input.background = null
        inputContainer.addView(input, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (isPassword) {
            var visiblePassword = false
            val eye = ImageButton(this).apply {
                setImageResource(R.drawable.ic_visibility_off)
                imageTintList = ColorStateList.valueOf(purple)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ColorDrawable(Color.TRANSPARENT)
                contentDescription = "Показать пароль"
                setOnClickListener {
                    visiblePassword = !visiblePassword
                    input.transformationMethod = if (visiblePassword) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
                    setImageResource(
                        if (visiblePassword) R.drawable.ic_visibility
                        else R.drawable.ic_visibility_off
                    )
                    contentDescription = if (visiblePassword) "Скрыть пароль" else "Показать пароль"
                    input.setSelection(input.text.length)
                    animatePress(this)
                }
            }
            inputContainer.addView(eye, FrameLayout.LayoutParams(
                dp(48),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ))
        }
        val errorView = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#FF6B81"))
            visibility = View.GONE
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        authErrorView = errorView

        val submit = Button(this).apply {
            text = button
            setTextColor(Color.WHITE)
            background = roundedBg(purple, 16)
            setOnClickListener {
                animatePress(this)
                val value = input.text.toString()
                if (value.isBlank()) {
                    errorView.text = "Заполни поле"
                    errorView.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                errorView.visibility = View.GONE
                isEnabled = false
                alpha = 0.72f
                lifecycleScope.launch {
                    try {
                        onSubmit(value)
                    } catch (e: Exception) {
                        val message = friendlyAuthError(e.message)
                        errorView.text = message
                        errorView.visibility = View.VISIBLE
                        this@apply.isEnabled = true
                        this@apply.alpha = 1f
                    }
                }
            }
        }
        authSubmitButton = submit

        card.addView(brand, LinearLayout.LayoutParams(dp(52), dp(52)))
        card.addView(titleView)
        card.addView(subtitleView)
        card.addView(inputContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))
        card.addView(submit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply { topMargin = dp(12) })
        card.addView(errorView)

        secondaryButton?.let { secondaryLabel ->
            val secondary = TextView(this).apply {
                text = secondaryLabel
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                background = roundedBg(palette.surfaceAlt, 16)
                setOnClickListener {
                    animatePress(this)
                    onSecondary?.invoke()
                }
            }
            card.addView(secondary, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { topMargin = dp(10) })
        }

        footer?.let { helpText ->
            val help = TextView(this).apply {
                text = helpText
                textSize = 12.5f
                setTextColor(muted)
                setPadding(dp(2), dp(14), dp(2), 0)
                setLineSpacing(0f, 1.15f)
            }
            card.addView(help)
        }

        container.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        replaceRoot(container)
        input.requestFocus()
    }

    private fun animatePress(view: View) {
        if (!settings.animations) return

        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.rotation = 0f

        val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)

        view.animate()
            .scaleX(0.955f)
            .scaleY(0.955f)
            .setDuration(65L)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(125L)
                    .setInterpolator(ease)
                    .start()
            }
            .start()
    }

    private fun friendlyAuthError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            "PHONE_NUMBER_INVALID" in text -> "Номер телефона неверный. Введи его с + и кодом страны."
            "PHONE_NUMBER_FLOOD" in text -> "Слишком много попыток. Telegram временно ограничил отправку кода."
            "PHONE_CODE_INVALID" in text -> "Код неверный. Проверь цифры и попробуй ещё раз."
            "PHONE_CODE_EXPIRED" in text -> "Код уже истёк. Нажми «Отправить код ещё раз»."
            "PASSWORD_HASH_INVALID" in text -> "Неверный облачный пароль."
            "API_ID" in text -> "Ошибка Telegram API. Нужно проверить API ID/API Hash приложения."
            text.isBlank() -> "Telegram не принял запрос. Попробуй ещё раз."
            else -> text
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                val message = friendlyAuthError(e.message)
                authErrorView?.let {
                    it.text = message
                    it.visibility = View.VISIBLE
                } ?: Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                authSubmitButton?.apply {
                    isEnabled = true
                    alpha = 1f
                }
            }
        }
    }

    private fun cleanupStorage() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val currentName = "tdlib_session_" + settings.authGeneration
                filesDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("tdlib_session_") && it.name != currentName }
                    ?.forEach { it.deleteRecursively() }
            }

            runCatching {
                val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
                cacheDir.walkTopDown()
                    .filter { it.isFile && it.lastModified() < cutoff }
                    .forEach { it.delete() }
            }

            runCatching {
                val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
                var total = files.sumOf { it.length() }
                val maxBytes = 256L * 1024L * 1024L
                if (total > maxBytes) {
                    files.sortedBy { it.lastModified() }.forEach { file ->
                        if (total <= maxBytes) return@forEach
                        val size = file.length()
                        if (file.delete()) total -= size
                    }
                }
            }
        }
    }

    private fun ensureStreamServer() {
        if (streamServer != null) return
        try {
            streamServer = TelegramStreamServer(client).also {
                it.start(10_000, false)
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Не удалось запустить видеопоток: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadVideos(inPlace: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (inPlace) {
                feedRefreshLabel?.text = "Уже проверяем…"
            } else {
                reloadRequested = true
            }
            return
        }

        if (inPlace) {
            setFeedRefreshLoading(true)
        }

        loadJob = lifecycleScope.launch {
            if (!inPlace) {
                withContext(Dispatchers.Main) { showLoading("Собираем записи за неделю…") }
            }
            try {
                val chat = client.send(TdApi.SearchPublicChat("t2x2_video"))
                channelChatId = chat.id

                val zone = ZoneId.systemDefault()
                val cutoffDate = LocalDate.now(zone).minusDays(6)
                val cutoffEpoch = cutoffDate.atStartOfDay(zone).toEpochSecond()

                val collected = linkedMapOf<Long, VideoItem>()
                var fromMessageId = 0L
                var page = 0
                var reachedOldMessages = false

                while (page < 20 && !reachedOldMessages) {
                    val history = client.send(
                        TdApi.GetChatHistory(chat.id, fromMessageId, 0, 100, false)
                    )
                    if (history.messages.isEmpty()) break

                    for (message in history.messages) {
                        if (message.date.toLong() < cutoffEpoch) {
                            reachedOldMessages = true
                            continue
                        }
                        messageToVideo(message)?.let { collected[it.messageId] = it }
                    }

                    val last = history.messages.lastOrNull() ?: break
                    if (last.id == fromMessageId) break
                    fromMessageId = last.id
                    page++
                }

                val videos = collected.values
                    .filter { it.date.toLong() >= cutoffEpoch }
                    .sortedByDescending { it.date }

                val preparedVideos = if (settings.previews) {
                    videos.chunked(6).flatMap { batch ->
                        coroutineScope {
                            batch.map { item ->
                                async(Dispatchers.IO) { attachThumbnail(item) }
                            }.awaitAll()
                        }
                    }
                } else {
                    videos.map { it.copy(thumbnailPath = null) }
                }

                withContext(Dispatchers.Main) {
                    currentVideos = preparedVideos
                    currentDay = null
                    feedRefreshCompletedFlash = inPlace
                    showFeed(preparedVideos)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (inPlace) {
                        setFeedRefreshLoading(false, "Ошибка")
                        Toast.makeText(
                            this@MainActivity,
                            e.message ?: "Не удалось проверить новые видео",
                            Toast.LENGTH_LONG
                        ).show()
                        feedRefreshButton?.postDelayed({
                            setFeedRefreshLoading(false, "Проверить новые")
                        }, 1400L)
                    } else {
                        showMessage(
                            "Не удалось загрузить записи",
                            e.message ?: "Неизвестная ошибка Telegram"
                        )
                    }
                }
            }
        }
    }

    private fun setFeedRefreshLoading(loading: Boolean, label: String? = null) {
        feedRefreshButton?.isEnabled = !loading
        feedRefreshButton?.alpha = if (loading) 0.92f else 1f
        feedRefreshLoader?.visibility = if (loading) View.VISIBLE else View.GONE
        feedRefreshLabel?.text = label ?: if (loading) "Работаем…" else "Проверить новые"
    }

    private fun messageToVideo(message: TdApi.Message): VideoItem? {
        return when (val content = message.content) {
            is TdApi.MessageVideo -> {
                val file = content.video.video
                VideoItem(
                    messageId = message.id,
                    title = content.caption.text.trim().ifBlank {
                        content.video.fileName.ifBlank { "Запись стрима" }
                    },
                    date = message.date,
                    durationSeconds = content.video.duration,
                    fileId = file.id,
                    fileSize = if (file.size > 0) file.size else file.expectedSize,
                    mimeType = content.video.mimeType.ifBlank { "video/mp4" },
                    thumbnailFileId = content.video.thumbnail?.file?.id
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val mime = doc.mimeType.ifBlank { "application/octet-stream" }
                val name = doc.fileName
                val isVideo = mime.startsWith("video/") ||
                    name.endsWith(".mp4", true) ||
                    name.endsWith(".mkv", true) ||
                    name.endsWith(".mov", true) ||
                    name.endsWith(".webm", true)
                if (!isVideo) return null
                val file = doc.document
                VideoItem(
                    messageId = message.id,
                    title = content.caption.text.trim().ifBlank {
                        name.ifBlank { "Запись стрима" }
                    },
                    date = message.date,
                    durationSeconds = 0,
                    fileId = file.id,
                    fileSize = if (file.size > 0) file.size else file.expectedSize,
                    mimeType = mime,
                    thumbnailFileId = doc.thumbnail?.file?.id
                )
            }
            else -> null
        }
    }

    private suspend fun attachThumbnail(item: VideoItem): VideoItem {
        val thumbId = item.thumbnailFileId ?: return item
        return try {
            val file = client.send(TdApi.DownloadFile(thumbId, 1, 0, 0, true))
            val path = file.local.path.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
            item.copy(thumbnailPath = path)
        } catch (_: Exception) {
            item
        }
    }

    private fun showFeed(videos: List<VideoItem>) {
        completedUpdateNotice?.let { version ->
            completedUpdateNotice = null
            root.postDelayed({
                ModernDialogs.showNotice(
                    context = this@MainActivity,
                    palette = palette,
                    title = "Обновление завершено",
                    message = "SOHR обновлён до версии " + version + ". Всё готово к работе.",
                    button = "Готово"
                )
            }, 420L)
        }

        startupPhase = false
        startupStatusView = null
        isPlayerScreen = false
        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val zone = ZoneId.systemDefault()
        val baseGroups = videos
            .groupBy { Instant.ofEpochSecond(it.date.toLong()).atZone(zone).toLocalDate() }
            .map { (date, dayVideos) ->
                DayCollection(date, dayVideos.sortedByDescending { it.date })
            }

        val groups = baseGroups.sortedByDescending { it.date }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(10))
            setBackgroundColor(bg)
        }

        val titleView = TextView(this).apply {
            text = "SOHR"
            textSize = 24f
            gravity = Gravity.START
            maxLines = 1
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Последние 7 дней • ${groups.size} сборников • ${videos.size} видео"
            textSize = 12f
            gravity = Gravity.START
            maxLines = 1
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(12))
        }

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sectionTitle = TextView(this).apply {
            text = "Сборники по дням"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(muted)
        }

        val refreshLoader = LoadingWaveView(this, purple).apply {
            visibility = View.GONE
        }

        val refreshLabel = TextView(this).apply {
            text = if (feedRefreshCompletedFlash) "Готово" else "Проверить новые"
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
        }

        val refresh = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBg(palette.surfaceAlt, 16)
            isClickable = true
            isFocusable = true
            contentDescription = "Проверить новые видео"
            addView(
                refreshLoader,
                LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(7)
                }
            )
            addView(
                refreshLabel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                animatePress(this)
                loadVideos(inPlace = true)
            }
        }

        feedRefreshButton = refresh
        feedRefreshLabel = refreshLabel
        feedRefreshLoader = refreshLoader

        controlRow.addView(
            sectionTitle,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        controlRow.addView(
            refresh,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44))
        )

        if (feedRefreshCompletedFlash) {
            feedRefreshCompletedFlash = false
            refresh.postDelayed({
                if (feedRefreshButton === refresh && refresh.isEnabled) {
                    refreshLabel.animate().cancel()
                    refreshLabel.animate()
                        .alpha(0f)
                        .setDuration(80L)
                        .withEndAction {
                            refreshLabel.text = "Проверить новые"
                            refreshLabel.animate().alpha(1f).setDuration(120L).start()
                        }
                        .start()
                }
            }, 1100L)
        }

        header.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(controlRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(header)

        if (groups.isEmpty()) {
            val empty = TextView(this).apply {
                text = "За последние 7 дней видео не найдено."
                setTextColor(muted)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            page.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val list = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = DayCollectionAdapter(groups, palette, settings.animations) { showDayCollection(it) }
                setBackgroundColor(bg)
                setHasFixedSize(true)
                itemAnimator = if (settings.animations) itemAnimator else null
            }
            page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun showDayCollection(collection: DayCollection) {
        currentDay = collection
        isPlayerScreen = false
        isSettingsScreen = false
        isAccountScreen = false
        setFullscreen(false)
        applySystemTheme()

        val sortedVideos = collection.videos.sortedByDescending { it.date }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(bg)
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            background = roundedBg(palette.surfaceAlt, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = dayTitle(collection.date)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "${collection.videos.size} видео • @t2x2_video"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        }

        titles.addView(titleView)
        titles.addView(subtitle)

        val spacer = View(this)
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(spacer, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = VideoAdapter(sortedVideos, palette, settings.animations) { openPlayer(it) }
            setBackgroundColor(bg)
            setHasFixedSize(true)
            itemAnimator = if (settings.animations) itemAnimator else null
        }

        page.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        replaceRoot(withBottomNav(page, SohrTab.VIDEOS))
    }

    private fun dayTitle(date: LocalDate): String {
        val today = LocalDate.now()
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
        return when (date) {
            today -> "Сегодня • $formatted"
            today.minusDays(1) -> "Вчера • $formatted"
            else -> formatted.replaceFirstChar { it.titlecase(Locale("ru")) }
        }
    }

    private fun openPlayer(item: VideoItem, startSeconds: Int = 0) {
        val server = streamServer
        if (server == null) {
            Toast.makeText(this, "Видеопоток ещё не готов", Toast.LENGTH_SHORT).show()
            return
        }

        feedRefreshButton = null
        feedRefreshLabel = null
        feedRefreshLoader = null
        server.prefetch(item)
        playerScreen?.destroy()
        isSettingsScreen = false
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = true

        playerScreen = PlayerScreen(
            activity = this,
            item = item,
            mediaUrl = server.url(item),
            previewDataSourceFactory = { server.mediaDataSource(item) },
            settings = settings,
            startPositionMs = startSeconds * 1000L,
            onBack = { onBackPressedDispatcher.onBackPressed() },
            onFullscreen = { setFullscreen(it) },
            onPlaybackStarted = { streakTracker.markWatched() }
        )

        replaceRoot(playerScreen!!.root)
    }

    private fun showSettings() {
        isSettingsScreen = true
        isAccountScreen = false
        isStreakScreen = false
        isPlayerScreen = false
        applySystemTheme()
        val screen = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                if (needsReload) loadVideos() else showFeed(currentVideos)
            },
            onThemeChanged = { light, source ->
                animateThemeReveal(light, source)
            },
            onLanguageChanged = {
                showSettings()
            },
            onCheckUpdates = { checkForUpdates() },
            onLogout = { confirmLogout() }
        )
        replaceRoot(withBottomNav(screen.build(), SohrTab.SETTINGS))
    }

    private fun showStreak() {
        isStreakScreen = true
        isSettingsScreen = false
        isAccountScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val streak = streakTracker.currentStreak()
        val totalDays = streakTracker.totalWatchedDays()
        val watchedToday = streakTracker.watchedToday()
        val flameColor = streakColor(streak)
        val next = nextStreakMilestone(streak)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "Стрик"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val subtitle = TextView(this).apply {
            text = "Дни подряд со стримером"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = roundedBg(panel, 26)
        }

        val fire = StreakFireView(this, flameColor)

        val count = TextView(this).apply {
            text = streak.toString()
            textSize = 44f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(flameColor)
            setPadding(0, dp(2), 0, 0)
        }

        val daysLabel = TextView(this).apply {
            text = when {
                streak == 1 -> "день подряд"
                streak in 2..4 -> "дня подряд"
                else -> "дней подряд"
            }
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val status = TextView(this).apply {
            text = if (watchedToday) "Сегодня уже засчитано" else "Посмотри видео сегодня, чтобы продолжить"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(8), dp(10), dp(8), 0)
        }

        hero.addView(
            fire,
            LinearLayout.LayoutParams(dp(178), dp(178)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        hero.addView(count)
        hero.addView(daysLabel)
        hero.addView(status)

        val progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBg(panel, 20)
        }

        val progressTitle = TextView(this).apply {
            text = if (next == null) {
                "Максимальный уровень огня"
            } else {
                "До следующего огня • " + (next - streak) + " дн."
            }
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }

        val progressSubtitle = TextView(this).apply {
            text = "Всего дней просмотра: " + totalDays
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(10))
        }

        val track = FrameLayout(this).apply {
            background = roundedBg(palette.surfaceAlt, 6)
            clipToOutline = true
        }

        val fill = View(this).apply {
            background = roundedBg(flameColor, 6)
        }

        track.addView(
            fill,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val progress = streakProgress(streak)
        track.post {
            val params = fill.layoutParams as FrameLayout.LayoutParams
            params.width = (track.width * progress).toInt().coerceAtLeast(if (streak > 0) dp(8) else 0)
            fill.layoutParams = params
            if (settings.animations) {
                fill.scaleX = 0f
                fill.pivotX = 0f
                fill.animate()
                    .scaleX(1f)
                    .setDuration(520L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start()
            }
        }

        progressCard.addView(progressTitle)
        progressCard.addView(progressSubtitle)
        progressCard.addView(
            track,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(9)
            )
        )

        val levelsTitle = TextView(this).apply {
            text = "Уровни огня"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(dp(2), dp(18), 0, dp(10))
        }

        val levels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBg(panel, 20)
        }

        val levelData = listOf(
            Triple("1–9 дней", Color.parseColor("#E7E7EC"), "Белый"),
            Triple("10–19 дней", Color.parseColor("#9A68FF"), "Фиолетовый"),
            Triple("20–49 дней", Color.parseColor("#4D98FF"), "Синий"),
            Triple("50–99 дней", Color.parseColor("#FF4A5E"), "Красный"),
            Triple("100+ дней", Color.parseColor("#B7FF28"), "Кислотный")
        )

        levelData.forEach { entry ->
            val range = entry.first
            val color = entry.second
            val name = entry.third

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(9), dp(4), dp(9))
            }

            val dot = View(this).apply {
                background = roundedBg(color, 10)
            }
            val rangeView = TextView(this).apply {
                text = range
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(this@MainActivity.text)
                setPadding(dp(12), 0, 0, 0)
            }
            val nameView = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.END
                setTextColor(muted)
            }

            row.addView(dot, LinearLayout.LayoutParams(dp(18), dp(18)))
            row.addView(rangeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(nameView)
            levels.addView(row)
        }

        page.addView(title)
        page.addView(subtitle)
        page.addView(hero)
        page.addView(
            progressCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )
        page.addView(levelsTitle)
        page.addView(levels)

        scroll.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (settings.animations) {
            hero.alpha = 0f
            hero.translationY = dp(14).toFloat()
            progressCard.alpha = 0f
            progressCard.translationY = dp(12).toFloat()
            levels.alpha = 0f
            levels.translationY = dp(10).toFloat()
        }

        replaceRoot(withBottomNav(scroll, SohrTab.STREAK))

        if (settings.animations) {
            hero.post {
                val ease = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                hero.animate().alpha(1f).translationY(0f).setDuration(300L).setInterpolator(ease).start()
                progressCard.animate().alpha(1f).translationY(0f).setStartDelay(70L).setDuration(300L).setInterpolator(ease).start()
                levels.animate().alpha(1f).translationY(0f).setStartDelay(140L).setDuration(320L).setInterpolator(ease).start()
            }
        }
    }

    private fun streakColor(streak: Int): Int = StreakFireView.colorForStreak(streak)

    private fun nextStreakMilestone(streak: Int): Int? = when {
        streak < 10 -> 10
        streak < 20 -> 20
        streak < 50 -> 50
        streak < 100 -> 100
        streak < 200 -> 200
        else -> null
    }

    private fun streakProgress(streak: Int): Float {
        val start: Int
        val end: Int
        when {
            streak < 10 -> { start = 0; end = 10 }
            streak < 20 -> { start = 10; end = 20 }
            streak < 50 -> { start = 20; end = 50 }
            streak < 100 -> { start = 50; end = 100 }
            streak < 200 -> { start = 100; end = 200 }
            else -> return 1f
        }
        return ((streak - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    private fun animateThemeReveal(light: Boolean, source: View) {
        if (settings.lightTheme == light) return

        if (!settings.animations || root.width <= 0 || root.height <= 0) {
            settings.lightTheme = light
            primaryShell = null
            primaryContentHost = null
            primaryNav = null
            primaryShellLightTheme = null
            applySystemTheme()
            showSettings()
            return
        }

        val oldShell = primaryShell
        val oldSystemColor = bg

        settings.lightTheme = light
        val newSystemColor = bg
        animateSystemChrome(oldSystemColor, newSystemColor, light, 430L)

        val nextContent = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                if (needsReload) loadVideos() else showFeed(currentVideos)
            },
            onThemeChanged = { nextLight, nextSource ->
                animateThemeReveal(nextLight, nextSource)
            },
            onLanguageChanged = { showSettings() },
            onCheckUpdates = { checkForUpdates() },
            onLogout = { confirmLogout() }
        ).build()

        val nextHost = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
            addView(
                nextContent,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        lateinit var nextNav: SohrBottomNavView
        nextNav = SohrBottomNavView(this, palette, SohrTab.SETTINGS) { tab ->
            if (tab != currentPrimaryTab) {
                pendingRootSlide = if (tab.ordinal > currentPrimaryTab.ordinal) 1 else -1
                currentPrimaryTab = tab
                when (tab) {
                    SohrTab.VIDEOS -> showFeed(currentVideos)
                    SohrTab.SETTINGS -> showSettings()
                    SohrTab.STREAK -> showStreak()
                    SohrTab.ACCOUNT -> showAccount()
                }
            }
        }

        val nextShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(
                nextHost,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                nextNav,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
                ).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                }
            )
        }

        nextShell.visibility = View.INVISIBLE
        root.addView(
            nextShell,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val sourceLocation = IntArray(2)
        val rootLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        root.getLocationOnScreen(rootLocation)

        val cx = sourceLocation[0] - rootLocation[0] + source.width / 2
        val cy = sourceLocation[1] - rootLocation[1] + source.height / 2

        nextShell.post {
            nextShell.visibility = View.VISIBLE

            val maxX = maxOf(cx, nextShell.width - cx).toDouble()
            val maxY = maxOf(cy, nextShell.height - cy).toDouble()
            val finalRadius = kotlin.math.hypot(maxX, maxY).toFloat()

            ViewAnimationUtils.createCircularReveal(
                nextShell,
                cx.coerceIn(0, nextShell.width),
                cy.coerceIn(0, nextShell.height),
                0f,
                finalRadius
            ).apply {
                duration = 430L
                interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (oldShell != null && oldShell.parent === root) {
                            root.removeView(oldShell)
                        }

                        primaryShell = nextShell
                        primaryContentHost = nextHost
                        primaryNav = nextNav
                        primaryShellLightTheme = light
                        currentPrimaryTab = SohrTab.SETTINGS

                        root.setBackgroundColor(bg)
                        applySystemTheme()
                    }
                })
                start()
            }
        }
    }

    private fun showAccount() {
        isAccountScreen = true
        isSettingsScreen = false
        isStreakScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val loadingPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            val spinner = LoadingWaveView(this@MainActivity, purple)
            val label = TextView(this@MainActivity).apply {
                text = "Загружаем профиль…"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, dp(14), 0, 0)
            }
            addView(spinner, LinearLayout.LayoutParams(dp(58), dp(58)))
            addView(label)
        }
        replaceRoot(withBottomNav(loadingPage, SohrTab.ACCOUNT))

        lifecycleScope.launch {
            try {
                val user = client.send(TdApi.GetMe())
                val avatarPath = withContext(Dispatchers.IO) {
                    val photoId = user.profilePhoto?.small?.id ?: return@withContext null
                    runCatching {
                        val file = client.send(TdApi.DownloadFile(photoId, 2, 0, 0, true))
                        file.local.path.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
                    }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    renderAccount(user, avatarPath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("Не удалось открыть аккаунт", e.message ?: "Ошибка Telegram")
                }
            }
        }
    }

    private fun renderAccount(user: TdApi.User, avatarPath: String?) {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(18), dp(16), dp(12))
        }

        val title = TextView(this).apply {
            text = "Аккаунт"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
        }
        val subtitle = TextView(this).apply {
            text = "Профиль"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        }
        page.addView(title)
        page.addView(subtitle)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = roundedBg(panel, 24)
        }

        val avatarFrame = FrameLayout(this).apply {
            background = roundedBg(palette.accentSoft, 46)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg(panel, 42)
        }

        if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
            avatar.load(File(avatarPath)) {
                crossfade(settings.animations)
                transformations(CircleCropTransformation())
            }
        } else {
            avatar.load(R.drawable.ic_launcher) {
                transformations(CircleCropTransformation())
            }
        }

        avatarFrame.addView(
            avatar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        card.addView(avatarFrame, LinearLayout.LayoutParams(dp(92), dp(92)))

        val fullName = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Telegram" }

        card.addView(TextView(this).apply {
            text = fullName
            textSize = 21f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(12), 0, dp(3))
        })

        card.addView(TextView(this).apply {
            text = "SOHR • Telegram"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
        })

        val phoneRaw = user.phoneNumber.orEmpty().let { if (it.startsWith("+")) it else "+$it" }
        var revealed = false
        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = roundedBg(palette.surfaceAlt, 16)
        }

        val phoneLabel = TextView(this).apply {
            text = "Номер телефона"
            textSize = 12f
            setTextColor(muted)
        }
        val phoneValue = TextView(this).apply {
            text = maskPhone(phoneRaw)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(3), 0, 0)
        }
        val phoneTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(phoneLabel)
            addView(phoneValue)
        }
        val eye = ImageButton(this).apply {
            setImageResource(R.drawable.ic_visibility_off)
            imageTintList = ColorStateList.valueOf(purple)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBg(palette.surface, 18)
            contentDescription = "Показать номер"
            setOnClickListener {
                revealed = !revealed
                phoneValue.animate()
                    .alpha(0f)
                    .setDuration(if (settings.animations) 70L else 0L)
                    .withEndAction {
                        phoneValue.text = if (revealed) phoneRaw else maskPhone(phoneRaw)
                        phoneValue.alpha = 0f
                        phoneValue.animate()
                            .alpha(1f)
                            .setDuration(if (settings.animations) 120L else 0L)
                            .start()
                    }
                    .start()
                setImageResource(
                    if (revealed) R.drawable.ic_visibility
                    else R.drawable.ic_visibility_off
                )
                contentDescription = if (revealed) "Скрыть номер" else "Показать номер"
                animatePress(this)
            }
        }
        phoneRow.addView(phoneTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        phoneRow.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))

        card.addView(phoneRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        page.addView(card)

        val privacy = TextView(this).apply {
            text = "Номер телефона скрыт по умолчанию."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        page.addView(privacy)

        if (settings.animations) {
            card.alpha = 0f
            card.translationY = dp(12).toFloat()
            avatar.scaleX = 0.88f
            avatar.scaleY = 0.88f
        }

        replaceRoot(withBottomNav(page, SohrTab.ACCOUNT))

        if (settings.animations) {
            card.post {
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                    .start()

                avatar.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(60)
                    .setDuration(280)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
                    .start()
            }
        }
    }

    private fun maskPhone(phone: String): String {
        if (phone.length <= 5) return "••••"
        val visibleStart = phone.take(3)
        val visibleEnd = phone.takeLast(2)
        return visibleStart + " ••• ••• ••" + visibleEnd
    }

    private fun withBottomNav(content: View, selected: SohrTab): View {
        val rebuild = primaryShell == null ||
            primaryContentHost == null ||
            primaryNav == null ||
            primaryShellLightTheme != settings.lightTheme

        if (rebuild) {
            val shell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(bg)
            }

            val host = FrameLayout(this).apply {
                clipChildren = true
                clipToPadding = true
            }

            val nav = SohrBottomNavView(this, palette, selected) { tab ->
                if (tab != currentPrimaryTab) {
                    pendingRootSlide = if (tab.ordinal > currentPrimaryTab.ordinal) 1 else -1
                    currentPrimaryTab = tab
                    when (tab) {
                        SohrTab.VIDEOS -> showFeed(currentVideos)
                        SohrTab.SETTINGS -> showSettings()
                        SohrTab.STREAK -> showStreak()
                        SohrTab.ACCOUNT -> showAccount()
                    }
                }
            }

            host.addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            shell.addView(
                host,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            shell.addView(
                nav,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(74)
                ).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                    topMargin = dp(6)
                    bottomMargin = dp(8)
                }
            )

            primaryShell = shell
            primaryContentHost = host
            primaryNav = nav
            primaryShellLightTheme = settings.lightTheme
            currentPrimaryTab = selected
            pendingRootSlide = 0
            return shell
        }

        val shell = primaryShell!!
        val host = primaryContentHost!!
        val nav = primaryNav!!

        val slide = pendingRootSlide
        pendingRootSlide = 0
        currentPrimaryTab = selected
        nav.syncSelected(selected, animate = false)

        val old = if (host.childCount > 0) host.getChildAt(host.childCount - 1) else null

        content.alpha = if (settings.animations && slide != 0) 0.88f else 1f
        content.translationX = if (settings.animations && slide != 0) dp(16).toFloat() * slide else 0f

        host.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (old != null && old !== content) {
            if (settings.animations && slide != 0) {
                old.animate().cancel()
                content.animate().cancel()

                old.animate()
                    .alpha(0.72f)
                    .translationX(-dp(8).toFloat() * slide)
                    .setDuration(120L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                    .start()

                content.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(220L)
                    .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .withEndAction {
                        if (old.parent === host) host.removeView(old)
                    }
                    .start()
            } else {
                host.removeView(old)
            }
        }

        return shell
    }



    private fun confirmLogout() {
        ModernDialogs.showConfirm(
            context = this,
            palette = palette,
            title = "Выйти из аккаунта?",
            message = "Сессия будет удалена с этого телефона. При следующем входе снова понадобится номер и код.",
            confirm = "Выйти",
            destructive = true
        ) {
            lifecycleScope.launch {
                try {
                    showLoading("Выходим из аккаунта…")
                    settings.authPhone = null
                    client.send(TdApi.LogOut())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, e.message ?: "Не удалось выйти", Toast.LENGTH_LONG).show()
                    showSettings()
                }
            }
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        fullScreen = enabled
        playerScreen?.setFullscreenMode(enabled)
        ViewCompat.requestApplyInsets(root)
        if (enabled) {
            if (settings.autoRotateFullscreen) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                run { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
            }
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            showLoading("Проверяем обновления…")
            try {
                val info = updateManager.check()
                if (info == null) {
                    showSettings()
                    root.post {
                        ModernDialogs.showChoices(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Обновлений нет",
                            options = listOf("У тебя последняя версия • " + BuildConfig.VERSION_NAME),
                            selected = 0
                        ) { }
                    }
                } else {
                    showSettings()
                    val details = buildString {
                        append("Доступна SOHR ")
                        append(info.versionName)
                        if (info.notes.isNotBlank()) {
                            append("\n\n")
                            append(info.notes)
                        }
                    }
                    root.post {
                        ModernDialogs.showConfirm(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Доступно обновление",
                            message = details,
                            confirm = "Обновить"
                        ) {
                            downloadAndInstallUpdate(info)
                        }
                    }
                }
            } catch (e: Exception) {
                showSettings()
                Toast.makeText(
                    this@MainActivity,
                    "Не удалось проверить обновления: " + (e.message ?: "ошибка сети"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun downloadAndInstallUpdate(info: UpdateInfo) {
        lifecycleScope.launch {
            showUpdateProgress(0)
            try {
                val apk = updateManager.download(info) { progress ->
                    runOnUiThread {
                        updateProgressLabel?.text = "Скачиваем SOHR " + info.versionName + " • " + progress + "%"
                    }
                }
                pendingUpdateApk = apk
                updateProgressLabel?.text = "Обновление готово"

                if (!updateManager.isSignatureCompatible(apk)) {
                    pendingUpdateApk = null
                    showSettings()
                    root.post {
                        ModernDialogs.showNotice(
                            context = this@MainActivity,
                            palette = palette,
                            title = "Нужна одноразовая переустановка",
                            message = "Текущая версия SOHR была подписана старым временным ключом. Android не разрешит обновить её поверх новой версии. Один раз установи первую версию с постоянной подписью после удаления старой — дальше обновления будут ставиться поверх без этого конфликта.",
                            button = "Понятно"
                        )
                    }
                    return@launch
                }

                if (updateManager.canRequestInstall()) {
                    root.postDelayed({ launchUpdateInstaller(apk) }, 260L)
                } else {
                    waitingForInstallPermission = true
                    Toast.makeText(
                        this@MainActivity,
                        "Разреши SOHR устанавливать обновления — после возврата установка продолжится сама.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(updateManager.unknownSourcesIntent())
                }
            } catch (e: Exception) {
                pendingUpdateApk = null
                showSettings()
                Toast.makeText(
                    this@MainActivity,
                    "Не удалось скачать обновление: " + (e.message ?: "ошибка"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showUpdateProgress(progress: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val spinner = LoadingWaveView(this, purple)
        val title = TextView(this).apply {
            text = "Обновление SOHR"
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(this@MainActivity.text)
            setPadding(0, dp(18), 0, 0)
        }
        val label = TextView(this).apply {
            text = "Скачиваем… " + progress + "%"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(7), 0, 0)
        }
        updateProgressLabel = label

        box.addView(spinner, LinearLayout.LayoutParams(dp(72), dp(72)))
        box.addView(title)
        box.addView(label)
        replaceRoot(box)
    }

    private fun launchUpdateInstaller(apk: File) {
        if (!apk.exists()) {
            showSettings()
            Toast.makeText(this, "Файл обновления не найден", Toast.LENGTH_LONG).show()
            return
        }

        try {
            startActivity(updateManager.installerIntent(apk))
        } catch (e: Exception) {
            showSettings()
            Toast.makeText(
                this,
                "Не удалось открыть установку: " + (e.message ?: "ошибка"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showLoading(message: String) {
        if (startupPhase && startupStatusView != null) {
            startupStatusView?.animate()?.cancel()
            startupStatusView?.text = message
            startupStatusView?.alpha = 0.72f
            startupStatusView?.animate()?.alpha(1f)?.setDuration(140L)?.start()
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }

        val spinner = LoadingWaveView(this, purple)
        val label = TextView(this).apply {
            text = message
            setTextColor(muted)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }

        box.addView(spinner, LinearLayout.LayoutParams(dp(72), dp(72)))
        box.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        replaceRoot(box)
    }

    private fun showMessage(title: String, description: String) {
        startupPhase = false
        startupStatusView = null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(bg)
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 23f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        box.addView(titleView)
        box.addView(descriptionView)
        replaceRoot(box)
    }

    private fun replaceRoot(view: View) {
        if (view === primaryShell) {
            suppressNextRootAnimation = false
            pendingRootSlide = 0

            if (root.childCount == 1 && root.getChildAt(0) === view) {
                return
            }

            (view.parent as? ViewGroup)?.removeView(view)
            root.removeAllViews()
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        val animate = settings.animations && !suppressNextRootAnimation
        suppressNextRootAnimation = false

        val slide = pendingRootSlide
        pendingRootSlide = 0

        root.removeAllViews()
        if (animate) {
            view.alpha = if (slide != 0) 0.84f else 0f
            view.translationX = if (slide != 0) dp(14).toFloat() * slide else 0f
            view.translationY = if (slide == 0) dp(5).toFloat() else 0f
        }

        root.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        if (animate) {
            view.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(if (slide != 0) 210L else 175L)
                .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
        }
    }

    private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun animateSystemChrome(fromColor: Int, toColor: Int, light: Boolean, durationMs: Long) {
        val evaluator = android.animation.ArgbEvaluator()
        var appearanceSwitched = false
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val color = evaluator.evaluate(fraction, fromColor, toColor) as Int
                root.setBackgroundColor(color)
                window.statusBarColor = color
                window.navigationBarColor = color
                if (!appearanceSwitched && fraction >= 0.55f) {
                    appearanceSwitched = true
                    applySystemBarIconAppearance(light)
                }
            }
        }.start()
    }

    private fun applySystemBarIconAppearance(light: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            run {
                window.decorView.systemUiVisibility =
                    if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    private fun applySystemTheme() {
        root.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        applySystemBarIconAppearance(settings.lightTheme)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        playerScreen?.destroy()
        playerScreen = null
        streamServer?.stop()
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }
}
