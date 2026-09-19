package com.unknokable.videosohranenki

data class AppLanguage(
    val code: String,
    val label: String,
    val shortLabel: String
)

object AppLanguages {
    val all = listOf(
        AppLanguage("ru", "Русский", "RU"),
        AppLanguage("uk", "Українська", "UA"),
        AppLanguage("pl", "Polski", "PL"),
        AppLanguage("en", "English", "EN")
    )

    fun byCode(code: String): AppLanguage =
        all.firstOrNull { it.code == code } ?: all.first()

    fun t(code: String, key: String): String {
        val ru = mapOf(
            "login" to "Вход",
            "choose_country" to "Выбери страну и введи номер Telegram.",
            "continue" to "Продолжить",
            "phone" to "Номер телефона",
            "language" to "Язык",
            "choose_language" to "Выбери язык приложения",
            "settings" to "Настройки",
            "personalization" to "Персонализация и воспроизведение",
            "theme" to "Тема",
            "theme_desc" to "Выбери оформление приложения",
            "dark" to "Тёмная",
            "light" to "Светлая",
            "autoplay" to "Автовоспроизведение",
            "autoplay_desc" to "Начинать видео сразу после открытия",
            "previews" to "Превью видео",
            "previews_desc" to "Показывать изображения из Telegram",
            "animations" to "Плавные анимации",
            "animations_desc" to "Переходы, нажатия и появление карточек",
            "ai" to "AI-анализ моментов",
            "ai_desc" to "Игры, реакции, разговоры и главы",
            "rotate" to "Автоповорот fullscreen",
            "rotate_desc" to "Поворачивать плеер горизонтально",
            "logout" to "Выйти из аккаунта",
            "country_search" to "Поиск страны, кода или +48",
            "choose_country_title" to "Выбери страну",
            "nothing_found" to "Ничего не найдено",
            "phone_empty" to "Введи номер телефона",
            "phone_check" to "Проверь номер телефона",
            "loading_countries" to "Загружаем страны Telegram…"
        )
        val uk = mapOf(
            "login" to "Вхід",
            "choose_country" to "Обери країну та введи номер Telegram.",
            "continue" to "Продовжити",
            "phone" to "Номер телефону",
            "language" to "Мова",
            "choose_language" to "Обери мову застосунку",
            "settings" to "Налаштування",
            "personalization" to "Персоналізація та відтворення",
            "theme" to "Тема",
            "theme_desc" to "Обери оформлення застосунку",
            "dark" to "Темна",
            "light" to "Світла",
            "autoplay" to "Автовідтворення",
            "autoplay_desc" to "Починати відео одразу після відкриття",
            "previews" to "Прев’ю відео",
            "previews_desc" to "Показувати зображення з Telegram",
            "animations" to "Плавні анімації",
            "animations_desc" to "Переходи, натискання та поява карток",
            "ai" to "AI-аналіз моментів",
            "ai_desc" to "Ігри, реакції, розмови та розділи",
            "rotate" to "Автоповорот fullscreen",
            "rotate_desc" to "Повертати плеєр горизонтально",
            "logout" to "Вийти з акаунта",
            "country_search" to "Пошук країни, коду або +48",
            "choose_country_title" to "Обери країну",
            "nothing_found" to "Нічого не знайдено",
            "phone_empty" to "Введи номер телефону",
            "phone_check" to "Перевір номер телефону",
            "loading_countries" to "Завантажуємо країни Telegram…"
        )
        val pl = mapOf(
            "login" to "Logowanie",
            "choose_country" to "Wybierz kraj i wpisz numer Telegram.",
            "continue" to "Dalej",
            "phone" to "Numer telefonu",
            "language" to "Język",
            "choose_language" to "Wybierz język aplikacji",
            "settings" to "Ustawienia",
            "personalization" to "Personalizacja i odtwarzanie",
            "theme" to "Motyw",
            "theme_desc" to "Wybierz wygląd aplikacji",
            "dark" to "Ciemny",
            "light" to "Jasny",
            "autoplay" to "Autoodtwarzanie",
            "autoplay_desc" to "Uruchamiaj film od razu po otwarciu",
            "previews" to "Podglądy wideo",
            "previews_desc" to "Pokazuj obrazy z Telegrama",
            "animations" to "Płynne animacje",
            "animations_desc" to "Przejścia, kliknięcia i pojawianie kart",
            "ai" to "Analiza AI",
            "ai_desc" to "Gry, reakcje, rozmowy i rozdziały",
            "rotate" to "Autoobrót fullscreen",
            "rotate_desc" to "Obracaj odtwarzacz poziomo",
            "logout" to "Wyloguj się",
            "country_search" to "Szukaj kraju, kodu lub +48",
            "choose_country_title" to "Wybierz kraj",
            "nothing_found" to "Brak wyników",
            "phone_empty" to "Wpisz numer telefonu",
            "phone_check" to "Sprawdź numer telefonu",
            "loading_countries" to "Ładujemy kraje Telegram…"
        )
        val en = mapOf(
            "login" to "Sign in",
            "choose_country" to "Choose a country and enter your Telegram number.",
            "continue" to "Continue",
            "phone" to "Phone number",
            "language" to "Language",
            "choose_language" to "Choose app language",
            "settings" to "Settings",
            "personalization" to "Personalization and playback",
            "theme" to "Theme",
            "theme_desc" to "Choose app appearance",
            "dark" to "Dark",
            "light" to "Light",
            "autoplay" to "Autoplay",
            "autoplay_desc" to "Start videos immediately after opening",
            "previews" to "Video previews",
            "previews_desc" to "Show images from Telegram",
            "animations" to "Smooth animations",
            "animations_desc" to "Transitions, taps and card animations",
            "ai" to "AI moment analysis",
            "ai_desc" to "Games, reactions, conversations and chapters",
            "rotate" to "Fullscreen auto-rotate",
            "rotate_desc" to "Rotate player to landscape",
            "logout" to "Sign out",
            "country_search" to "Search country, code or +48",
            "choose_country_title" to "Choose country",
            "nothing_found" to "Nothing found",
            "phone_empty" to "Enter phone number",
            "phone_check" to "Check the phone number",
            "loading_countries" to "Loading Telegram countries…"
        )
        return when (code) {
            "uk" -> uk[key]
            "pl" -> pl[key]
            "en" -> en[key]
            else -> ru[key]
        } ?: ru[key] ?: key
    }
}
