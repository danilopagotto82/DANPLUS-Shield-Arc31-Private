package com.nuvio.tv.core.scout

internal fun scoutListTitle(title: String, emoji: String, enabled: Boolean): String =
    if (!enabled || title.isBlank() || title.startsWith("$emoji ")) title else "$emoji $title"

internal fun scoutMoodEmoji(mood: ScoutMood): String =
    mood.sliceId?.let(::scoutSliceEmoji) ?: when (mood) {
        ScoutMood.MECHA -> "🤖"
        ScoutMood.NATURE -> "🌿"
        ScoutMood.SPACE -> "🚀"
        ScoutMood.FOOD -> "🍿"
        ScoutMood.MUSIC -> "🎵"
        ScoutMood.SPORT, ScoutMood.SPORT_ANIME -> "🏅"
        ScoutMood.SCIENCE -> "🔬"
        ScoutMood.MAGICAL, ScoutMood.MAGICAL_GIRL -> "✨"
        else -> "🎬"
    }

internal fun scoutSliceEmoji(id: String): String = when (id) {
    "mood_feel_good" -> "☀️"
    "mood_laugh", "genre_35", "theme_sitcom" -> "😄"
    "mood_action", "genre_28" -> "💥"
    "mood_edge", "genre_53" -> "⚡"
    "mood_mind", "theme_ai" -> "🧠"
    "mood_dark", "theme_dystopia" -> "🌑"
    "mood_scary", "genre_27" -> "👻"
    "mood_romantic", "genre_10749" -> "❤️"
    "mood_epic" -> "🏔️"
    "mood_tears", "genre_18" -> "💧"
    "mood_slow" -> "🌙"
    "mood_weird" -> "🌀"
    "theme_true", "genre_99" -> "📖"
    "theme_book" -> "📚"
    "theme_time" -> "⏳"
    "theme_heist", "genre_80" -> "💰"
    "theme_survival", "genre_12" -> "🧭"
    "theme_court" -> "⚖️"
    "theme_whodunit", "genre_9648" -> "🔎"
    "theme_space", "genre_878" -> "🚀"
    "theme_coming_of_age" -> "🌱"
    "theme_road" -> "🚗"
    "theme_superhero" -> "🦸"
    "theme_zombie" -> "🧟"
    "theme_spy" -> "🕵️"
    "theme_miniseries", "complete_series" -> "📺"
    "family_night", "genre_10751" -> "🍿"
    "genre_16" -> "🎨"
    "genre_14" -> "✨"
    "genre_36" -> "📜"
    "genre_10402" -> "🎵"
    "genre_10752" -> "🎖️"
    "genre_37" -> "🤠"
    "acclaim_gems" -> "💎"
    "acclaim_cult" -> "🎞️"
    "acclaim_crowd", "acclaim_top" -> "⭐"
    else -> when {
        id.startsWith("length_") -> "⏱️"
        id.startsWith("decade_") -> "📼"
        id.startsWith("country_") || id.startsWith("region_") -> "🌍"
        id.startsWith("language_") -> "💬"
        id.startsWith("q.") -> ScoutAnswers.decode(id)?.mood?.let(::scoutMoodEmoji) ?: "🎬"
        ScoutSlices.byId(id)?.award != null -> "🏆"
        else -> "🎬"
    }
}

internal fun scoutMixEmoji(id: String): String {
    val mix = ScoutReadyMixes.byId(id) ?: return "🎵"
    return when (mix.tab) {
        ScoutReadyTab.DECADES -> "📼"
        ScoutReadyTab.WORLD -> "🌍"
        ScoutReadyTab.YEAR -> "📅"
        else -> when (mix.key) {
            "feel_good" -> "☀️"
            "laugh", "sitcoms" -> "😄"
            "edge" -> "⚡"
            "weird" -> "🌀"
            "saturday" -> "🎨"
            "cops" -> "🔎"
            "scifi" -> "🚀"
            else -> "🎵"
        }
    }
}
