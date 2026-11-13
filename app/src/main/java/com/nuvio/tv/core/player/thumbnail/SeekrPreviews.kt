package com.nuvio.tv.core.player.thumbnail

import android.content.Context
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import tv.seekr.previews.android.Seekr
import tv.seekr.previews.android.SeekrTrack
import tv.seekr.previews.core.SeekrContent
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal object SeekrPreviews {
    private val keyPattern = Regex("sk_live_[0-9a-fA-F]{64}")
    private val imdbPattern = Regex("tt[0-9]+")
    private val _configured = MutableStateFlow(false)
    val configured = _configured.asStateFlow()
    @Volatile private var client: Seekr? = null

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }

    fun hasValidFormat(value: String): Boolean = keyPattern.matches(value.trim())

    fun load(context: Context) {
        val key = store(context).read(0)?.takeIf(::hasValidFormat)
        client = key?.let(::create)
        _configured.value = client != null
    }

    suspend fun save(context: Context, value: String?): Boolean = withContext(Dispatchers.IO) {
        val key = value?.trim()
        if (key != null && !hasValidFormat(key)) return@withContext false
        val next = key?.let(::create)
        try {
            if (next != null && !next.validateKey()) return@withContext false
            store(context).write(0, key)
            client = next
            _configured.value = next != null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun track(content: SeekrContent, durationMs: Long): SeekrTrack? {
        if (durationMs <= 0L) return null
        return try {
            client?.loadTrack(content, durationMs)?.takeIf {
                abs(it.sourceDurationMs - durationMs) <= MAX_DURATION_DIFFERENCE_MS
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun content(id: String?, type: String?, season: Int?, episode: Int?): SeekrContent? {
        val clean = id?.trim().orEmpty()
        val imdb = clean.substringBefore(':').takeIf(imdbPattern::matches)
        val tmdb = clean.takeIf { it.startsWith("tmdb:") }
            ?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull()?.takeIf { it > 0 }
        if (imdb == null && tmdb == null) return null
        return when (type?.lowercase()) {
            "movie" -> SeekrContent.Movie(tmdbId = tmdb, imdbId = imdb)
            "series", "tv", "show" -> {
                if (season == null || season <= 0 || episode == null || episode <= 0) null
                else SeekrContent.Episode(showTmdbId = tmdb, showImdbId = imdb, season = season, episode = episode)
            }
            else -> null
        }
    }

    private fun create(key: String) = Seekr.create(apiKey = key, httpClient = http, maxCachedSheets = 2)

    private fun store(context: Context) =
        ProtectedProfilePreferences(context, "danplus_seekr_key", "danplus_seekr_key_v1")

    private const val MAX_DURATION_DIFFERENCE_MS = 15_000L
}
