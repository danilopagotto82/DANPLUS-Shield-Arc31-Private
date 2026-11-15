package com.nuvio.tv.core.player.thumbnail

import android.content.Context
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import tv.seekr.previews.android.Seekr
import tv.seekr.previews.android.SeekrTrack
import tv.seekr.previews.core.SeekrContent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal object SeekrPreviews {
    private val keyPattern = Regex("sk_live_[0-9a-fA-F]{64}")
    private val imdbPattern = Regex("tt[0-9]+")
    private val _configured = MutableStateFlow(false)
    val configured = _configured.asStateFlow()
    @Volatile private var key: String? = null

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
        key = store(context).read(0)?.takeIf(::hasValidFormat)
        _configured.value = key != null
    }

    enum class KeyCheck { Valid, Invalid, Unavailable, RateLimited }

    suspend fun checkKey(value: String): KeyCheck = withContext(Dispatchers.IO) {
        if (!hasValidFormat(value)) return@withContext KeyCheck.Invalid
        var result = KeyCheck.Unavailable
        val checkHttp = http.newBuilder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            result = when (response.code) {
                401, 403 -> KeyCheck.Invalid
                429 -> KeyCheck.RateLimited
                else -> if (response.isSuccessful && runCatching {
                    JSONObject(response.peekBody(8_192).string()).opt("valid") == false
                }.getOrDefault(false)) KeyCheck.Invalid else KeyCheck.Unavailable
            }
            response
        }.build()
        try {
            val testClient = Seekr.create(apiKey = value.trim(), httpClient = checkHttp, maxCachedSheets = 2)
            if (testClient.validateKey()) KeyCheck.Valid else result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            KeyCheck.Unavailable
        }
    }

    suspend fun save(context: Context, value: String?): Boolean = withContext(Dispatchers.IO) {
        val key = value?.trim()
        if (key != null && !hasValidFormat(key)) return@withContext false
        val next = key?.let(::create)
        try {
            if (next != null && !next.validateKey()) return@withContext false
            store(context).write(0, key)
            this@SeekrPreviews.key = key
            _configured.value = key != null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    enum class LookupStatus { Off, Checking, Ready, NotFound, InvalidKey, RateLimited, Unavailable }

    data class Lookup(val status: LookupStatus, val track: SeekrTrack? = null)

    suspend fun lookup(content: SeekrContent, durationMs: Long, positionMs: Long): Lookup = withContext(Dispatchers.IO) {
        val credentials = key ?: return@withContext Lookup(LookupStatus.Off)
        if (durationMs <= 0L) return@withContext Lookup(LookupStatus.Unavailable)
        val problem = AtomicReference<LookupStatus?>(null)
        val lookupHttp = http.newBuilder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val host = response.request.url.host
            val failure = when {
                response.code == 401 && host == "api.seekr.tv" -> LookupStatus.InvalidKey
                response.code == 404 && host == "api.seekr.tv" -> LookupStatus.NotFound
                response.code == 429 -> LookupStatus.RateLimited
                !response.isSuccessful -> LookupStatus.Unavailable
                else -> null
            }
            if (failure != null) problem.set(failure)
            response
        }.build()
        try {
            val deviceClient = Seekr.create(apiKey = credentials, httpClient = lookupHttp, maxCachedSheets = 2)
            val track = deviceClient.loadTrack(content, durationMs)
                ?: return@withContext Lookup(problem.get() ?: LookupStatus.Unavailable)
            if (track.sourceDurationMs > 0 && abs(track.sourceDurationMs - durationMs) > MAX_DURATION_DIFFERENCE_MS) {
                return@withContext Lookup(LookupStatus.NotFound)
            }
            val position = positionMs.coerceIn(0, durationMs - 1)
            val firstThumb = track.thumbnailFor(position)
                ?: if (problem.get() == null && position != durationMs / 2) track.thumbnailFor(durationMs / 2) else null
            val bitmap = firstThumb?.bitmap
            if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                Lookup(LookupStatus.Ready, track)
            } else {
                Lookup(problem.get() ?: LookupStatus.Unavailable, track)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Lookup(problem.get() ?: LookupStatus.Unavailable)
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

    // The service selects the closest version within its documented duration window.
    private const val MAX_DURATION_DIFFERENCE_MS = 240_000L
}
