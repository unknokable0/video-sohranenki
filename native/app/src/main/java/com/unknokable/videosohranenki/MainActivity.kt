package com.unknokable.videosohranenki

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
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
    private var currentVideos: List<VideoItem> = emptyList()
    private var currentDay: DayCollection? = null
    private var isPlayerScreen = false
    private var isSettingsScreen = false
    private var isAccountScreen = false
    private var fullScreen = false
    private var channelChatId: Long = 0L
    private var pendingCodeState: TdApi.AuthorizationStateWaitCode? = null
    private var authSubmitButton: Button? = null
    private var authErrorView: TextView? = null
    private var requestedPhoneNumber: String? = null
    private var authResetInProgress = false
    private var loadJob: kotlinx.coroutines.Job? = null
    private var reloadRequested = false

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

        showLoading("Подключаем Telegram…")
        client.init()
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
            val eye = TextView(this).apply {
                text = "👁"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(purple)
                setOnClickListener {
                    visiblePassword = !visiblePassword
                    input.transformationMethod = if (visiblePassword) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
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
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(65).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(105).start()
        }.start()
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

    private fun loadVideos() {
        if (loadJob?.isActive == true) {
            reloadRequested = true
            return
        }

        loadJob = lifecycleScope.launch {
            withContext(Dispatchers.Main) { showLoading("Собираем записи за неделю…") }
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
                    videos.map { item -> attachThumbnail(item) }
                } else {
                    videos.map { it.copy(thumbnailPath = null) }
                }

                withContext(Dispatchers.Main) {
                    currentVideos = preparedVideos
                    currentDay = null
                    showFeed(preparedVideos)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage(
                        "Не удалось загрузить записи",
                        e.message ?: "Неизвестная ошибка Telegram"
                    )
                }
            }
        }
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
        isPlayerScreen = false
        isSettingsScreen = false
        isAccountScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()

        val zone = ZoneId.systemDefault()
        val baseGroups = videos
            .groupBy { Instant.ofEpochSecond(it.date.toLong()).atZone(zone).toLocalDate() }
            .map { (date, dayVideos) ->
                DayCollection(date, dayVideos.sortedByDescending { it.date })
            }

        val groups = when (settings.collectionSort) {
            CollectionSort.NEWEST -> baseGroups.sortedByDescending { it.date }
            CollectionSort.OLDEST -> baseGroups.sortedBy { it.date }
            CollectionSort.MOST_VIDEOS -> baseGroups.sortedByDescending { it.videos.size }
            CollectionSort.LONGEST -> baseGroups.sortedByDescending { it.totalDurationSeconds }
        }

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

        val refresh = TextView(this).apply {
            text = "↻  Проверить новые"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            background = roundedBg(palette.surfaceAlt, 16)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                animatePress(this)
                isEnabled = false
                loadVideos()
                postDelayed({ isEnabled = true }, 1200)
            }
        }

        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            background = roundedBg(palette.surfaceAlt, 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                showSettings()
                postDelayed({ isEnabled = true }, 500)
            }
        }

        controlRow.addView(sectionTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controlRow.addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(8) })
        controlRow.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        header.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(controlRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(header)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(10))
        }

        val sort = TextView(this).apply {
            text = "⇅  ${settings.collectionSort.label}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBg(palette.surface, 16)
            setOnClickListener { showCollectionSortDialog() }
        }

        tools.addView(sort)
        page.addView(tools)

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

        val sortedVideos = when (settings.videoSort) {
            VideoSort.NEWEST -> collection.videos.sortedByDescending { it.date }
            VideoSort.OLDEST -> collection.videos.sortedBy { it.date }
            VideoSort.LONGEST -> collection.videos.sortedByDescending { it.durationSeconds }
            VideoSort.SHORTEST -> collection.videos.sortedBy { it.durationSeconds }
            VideoSort.LARGEST -> collection.videos.sortedByDescending { it.fileSize }
            VideoSort.SMALLEST -> collection.videos.sortedBy { it.fileSize }
            VideoSort.TITLE -> collection.videos.sortedBy { it.title.lowercase(Locale("ru")) }
        }

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

        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }

        val countLabel = TextView(this).apply {
            text = "Видео в сборнике"
            textSize = 13f
            setTextColor(muted)
        }

        val sort = TextView(this).apply {
            text = "⇅  ${settings.videoSort.label}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(purple)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBg(palette.surface, 14)
            setOnClickListener { showVideoSortDialog(collection) }
        }

        sortRow.addView(countLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sortRow.addView(sort)
        page.addView(sortRow)


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

        playerScreen?.destroy()
        isSettingsScreen = false
        isPlayerScreen = true

        playerScreen = PlayerScreen(
            activity = this,
            item = item,
            mediaUrl = server.url(item),
            previewDataSourceFactory = { server.mediaDataSource(item) },
            settings = settings,
            startPositionMs = startSeconds * 1000L,
            onBack = { onBackPressedDispatcher.onBackPressed() },
            onFullscreen = { setFullscreen(it) }
        )

        replaceRoot(playerScreen!!.root)
    }

    private fun showSettings() {
        isSettingsScreen = true
        isAccountScreen = false
        isPlayerScreen = false
        applySystemTheme()
        val screen = SettingsScreen(
            this,
            settings,
            onBack = { needsReload ->
                isSettingsScreen = false
                if (needsReload) loadVideos() else showFeed(currentVideos)
            },
            onThemeChanged = {
                applySystemTheme()
                showSettings()
            },
            onLanguageChanged = {
                showSettings()
            },
            onLogout = { confirmLogout() }
        )
        replaceRoot(withBottomNav(screen.build(), SohrTab.SETTINGS))
    }

    private fun showAccount() {
        isAccountScreen = true
        isSettingsScreen = false
        isPlayerScreen = false
        currentDay = null
        setFullscreen(false)
        applySystemTheme()
        showLoading("Загружаем профиль…")

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
            text = "Telegram-профиль в SOHR"
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

        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg(palette.accentSoft, 38)
            clipToOutline = true
        }
        if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
            avatar.load(File(avatarPath)) {
                crossfade(settings.animations)
                transformations(CircleCropTransformation())
            }
        } else {
            avatar.setImageResource(R.drawable.ic_launcher)
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(84), dp(84)))

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
        val eye = TextView(this).apply {
            text = "👁"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(purple)
            background = roundedBg(palette.surface, 14)
            setOnClickListener {
                revealed = !revealed
                phoneValue.text = if (revealed) phoneRaw else maskPhone(phoneRaw)
                animatePress(this)
            }
        }
        phoneRow.addView(phoneTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        phoneRow.addView(eye, LinearLayout.LayoutParams(dp(46), dp(46)))

        card.addView(phoneRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        page.addView(card)

        val security = TextView(this).apply {
            text = "Данные аккаунта получаются напрямую через TDLib и не выводятся наружу. Номер скрыт по умолчанию."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        page.addView(security)

        replaceRoot(withBottomNav(page, SohrTab.ACCOUNT))
    }

    private fun maskPhone(phone: String): String {
        if (phone.length <= 5) return "••••"
        val visibleStart = phone.take(3)
        val visibleEnd = phone.takeLast(2)
        return visibleStart + " ••• ••• ••" + visibleEnd
    }

    private fun withBottomNav(content: View, selected: SohrTab): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        shell.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        val nav = SohrBottomNavView(this, palette, selected) { tab ->
            when (tab) {
                SohrTab.VIDEOS -> showFeed(currentVideos)
                SohrTab.SETTINGS -> showSettings()
                SohrTab.ACCOUNT -> showAccount()
            }
        }

        shell.addView(nav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(74)
        ).apply {
            marginStart = dp(14)
            marginEnd = dp(14)
            topMargin = dp(6)
            bottomMargin = dp(8)
        })

        return shell
    }

    private fun showCollectionSortDialog() {
        val values = CollectionSort.values()
        ModernDialogs.showChoices(
            context = this,
            palette = palette,
            title = "Сортировка сборников",
            options = values.map { it.label },
            selected = settings.collectionSort.ordinal
        ) { which ->
            settings.collectionSort = values[which]
            showFeed(currentVideos)
        }
    }

    private fun showVideoSortDialog(collection: DayCollection) {
        val values = VideoSort.values()
        ModernDialogs.showChoices(
            context = this,
            palette = palette,
            title = "Сортировка видео",
            options = values.map { it.label },
            selected = settings.videoSort.ordinal
        ) { which ->
            settings.videoSort = values[which]
            showDayCollection(collection)
        }
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

    private fun showLoading(message: String) {
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
        root.removeAllViews()
        if (settings.animations) {
            view.alpha = 0f
            view.translationY = dp(6).toFloat()
        }
        root.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (settings.animations) {
            view.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
    }

    private fun roundedBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun applySystemTheme() {
        root.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (settings.lightTheme) mask else 0
            window.insetsController?.setSystemBarsAppearance(appearance, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (settings.lightTheme) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else View.SYSTEM_UI_FLAG_VISIBLE
        }
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
