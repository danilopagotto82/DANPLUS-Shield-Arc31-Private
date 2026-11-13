package com.nuvio.tv.ui.screens.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nuvio.tv.domain.model.MetaLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val localeSegment = Regex("[a-z]{2}(-[a-zA-Z]{2})?")
private val netflixTitleId = Regex("[0-9]{6,10}")

internal enum class StreamingApp(
    val playPackage: String,
    val firePackage: String = playPackage,
    vararg val otherPackages: String
) {
    NETFLIX("com.netflix.ninja", "com.netflix.ninja", "com.netflix.mediaclient"),
    PRIME("com.amazon.amazonvideo.livingroom", "com.amazon.firebat", "com.amazon.amazonvideo.livingroom.nvidia", "com.amazon.avod.thirdpartyclient"),
    DISNEY("com.disney.disneyplus"),
    HBO("com.wbd.stream", "com.hbo.hbonow"),
    GLOBOPLAY("com.globo.globotv"),
    APPLE("com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv"),
    PARAMOUNT("com.cbs.ca", "com.cbs.ca", "com.cbs.ott"),
    CRUNCHYROLL("com.crunchyroll.crunchyroid"),
    PLUTO("tv.pluto.android"),
    MUBI("com.mubi"),
    LOOKE("br.com.encripta.looke"),
    CLARO("br.com.claro.now.smarttvclient", "br.com.claro.now.smarttvclient", "br.com.netcombo.now"),
    OLDFLIX("oldflix.tv", "oldflix.tv", "oldflix.app"),
    PLEX("com.plexapp.android");

    fun packages(fireTv: Boolean): List<String> =
        (listOf(if (fireTv) firePackage else playPackage, playPackage, firePackage) + otherPackages).distinct()

    companion object {
        fun forProvider(id: Int): StreamingApp? = when (id) {
            8, 1796 -> NETFLIX
            10, 119, 201, 582, 683, 1825, 1968, 2100, 2156, 2243 -> PRIME
            337 -> DISNEY
            1899 -> HBO
            307 -> GLOBOPLAY
            2, 350, 1853, 2107, 2142 -> APPLE
            531, 2303 -> PARAMOUNT
            283 -> CRUNCHYROLL
            300 -> PLUTO
            11 -> MUBI
            47 -> LOOKE
            167, 484 -> CLARO
            499 -> OLDFLIX
            538 -> PLEX
            else -> null
        }
    }
}

internal class StreamingAppLauncher(private val context: Context) {
    private val manager get() = context.packageManager
    private val fireTv = android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

    fun installed(app: StreamingApp?): List<Intent> = app?.packages(fireTv).orEmpty().mapNotNull { packageName ->
        manager.getLeanbackLaunchIntentForPackage(packageName) ?: manager.getLaunchIntentForPackage(packageName)
    }

    fun openTitle(url: String, apps: List<Intent>): Boolean = apps.any { launch ->
        val packageName = launch.component?.packageName ?: launch.`package` ?: return@any false
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName))
    }

    fun openApp(apps: List<Intent>): Boolean = apps.any(::start)

    fun openStore(app: StreamingApp?, providerName: String): Boolean {
        val playUri = if (app == null) {
            Uri.parse("market://search").buildUpon().appendQueryParameter("q", providerName).build()
        } else {
            Uri.parse("market://details").buildUpon().appendQueryParameter("id", app.playPackage).build()
        }
        val amazonPackage = app?.takeUnless { it == StreamingApp.PRIME }?.firePackage
        val amazonUri = Uri.parse("amzn://apps/android").buildUpon()
            .appendQueryParameter(if (amazonPackage == null) "s" else "p", amazonPackage ?: providerName)
            .build()
        val play = Intent(Intent.ACTION_VIEW, playUri).setPackage("com.android.vending")
        val amazon = Intent(Intent.ACTION_VIEW, amazonUri).setPackage("com.amazon.venezia")
        return if (fireTv) start(amazon) || start(play) else start(play) || start(amazon)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal fun streamingTitleLink(app: StreamingApp, links: List<MetaLink>): String? {
    return links.firstNotNullOfOrNull { link ->
        val uri = link.url.toHttpUrlOrNull() ?: return@firstNotNullOfOrNull null
        if (!uri.isHttps || uri.username.isNotEmpty() || uri.password.isNotEmpty() || uri.port != 443) {
            return@firstNotNullOfOrNull null
        }
        val host = uri.host.removePrefix("www.")
        val parts = uri.pathSegments.dropWhile(localeSegment::matches)
        val valid = when (app) {
            StreamingApp.NETFLIX -> host == "netflix.com" && parts.firstOrNull() in setOf("title", "watch") &&
                parts.getOrNull(1)?.matches(netflixTitleId) == true
            StreamingApp.DISNEY -> host == "disneyplus.com" && parts.firstOrNull() in setOf("browse", "movies", "series") && parts.size >= 2
            StreamingApp.APPLE -> host == "tv.apple.com" && parts.firstOrNull() in setOf("movie", "show") &&
                parts.lastOrNull()?.startsWith("umc.cmc.") == true
            StreamingApp.PRIME -> host == "primevideo.com" && parts.any { it == "detail" } && parts.size >= 2
            StreamingApp.HBO -> host in setOf("hbomax.com", "max.com", "play.hbomax.com", "play.max.com") &&
                parts.firstOrNull() in setOf("movies", "shows", "movie", "show") && parts.size >= 2
            StreamingApp.GLOBOPLAY -> host == "globoplay.globo.com" && parts.firstOrNull() in setOf("v", "t") && parts.size >= 2
            StreamingApp.PARAMOUNT -> host == "paramountplus.com" &&
                parts.firstOrNull() in setOf("movies", "shows", "movie", "show") && parts.size >= 2
            StreamingApp.CRUNCHYROLL -> host == "crunchyroll.com" && parts.firstOrNull() in setOf("series", "watch") && parts.size >= 2
            StreamingApp.PLUTO -> host == "pluto.tv" && parts.firstOrNull() == "on-demand" && parts.size >= 2
            StreamingApp.MUBI -> host == "mubi.com" && parts.firstOrNull() == "films" && parts.size >= 2
            StreamingApp.LOOKE, StreamingApp.CLARO, StreamingApp.OLDFLIX, StreamingApp.PLEX -> false
        }
        uri.newBuilder().query(null).fragment(null).build().toString().takeIf { valid }
    }
}

/** TMDB offers have no provider title IDs. Look up exact public identifiers only when a user opens an installed app. */
internal object StreamingTitleLookup {
    private val endpoint = "https://www.wikidata.org/w/api.php".toHttpUrl()
    private val client by lazy {
        OkHttpClient.Builder().callTimeout(1600, TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun find(app: StreamingApp, titleId: String, imdbId: String?, movie: Boolean): String? {
        if (app !in setOf(StreamingApp.NETFLIX, StreamingApp.DISNEY, StreamingApp.APPLE)) return null
        val imdb = listOfNotNull(imdbId, titleId).firstOrNull { it.matches(Regex("tt[0-9]{5,12}")) }
        val tmdb = titleId.removePrefix("tmdb:").takeIf { it.matches(Regex("[0-9]{1,10}")) }
        val property = if (imdb != null) "P345" else if (movie) "P4947" else "P4983"
        val identifier = imdb ?: tmdb ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val search = request("action" to "query", "list" to "search", "srnamespace" to "0",
                    "srlimit" to "2", "srsearch" to "haswbstatement:$property=$identifier")
                    .getJSONObject("query").getJSONArray("search")
                if (search.length() != 1) return@withContext null
                val entity = search.getJSONObject(0).getString("title")
                if (!entity.matches(Regex("Q[0-9]+"))) return@withContext null
                val claims = request("action" to "wbgetentities", "ids" to entity, "props" to "claims")
                    .getJSONObject("entities").getJSONObject(entity).getJSONObject("claims")
                if (claim(claims, property) != identifier) return@withContext null
                when (app) {
                    StreamingApp.NETFLIX -> claim(claims, "P1874")?.takeIf(netflixTitleId::matches)
                        ?.let { "https://www.netflix.com/title/$it" }
                    StreamingApp.DISNEY -> claim(claims, "P13902")?.takeIf { it.matches(Regex("entity-[0-9a-f-]{36}")) }
                        ?.let { "https://www.disneyplus.com/browse/$it" }
                    StreamingApp.APPLE -> claim(claims, if (movie) "P9586" else "P9751")
                        ?.takeIf { it.matches(Regex("umc\\.cmc\\.[a-z0-9]{22,25}")) }
                        ?.let { "https://tv.apple.com/br/${if (movie) "movie" else "show"}/$it" }
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun request(vararg params: Pair<String, String>): JSONObject {
        val url = endpoint.newBuilder().addQueryParameter("format", "json").apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "DANPLUS/1.1.0 (https://github.com/danilopagotto82/DANPLUS-Shield-Arc31-Private)")
            .build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val source = checkNotNull(response.body).source()
            check(!source.request(512 * 1024L))
            JSONObject(source.readUtf8())
        }
    }

    private fun claim(claims: JSONObject, property: String): String? {
        val values = claims.optJSONArray(property) ?: return null
        val entries = (0 until values.length()).mapNotNull(values::optJSONObject)
        val active = entries.filter { it.optString("rank") != "deprecated" }
        val preferred = active.filter { it.optString("rank") == "preferred" }
        return preferred.ifEmpty { active }.mapNotNull {
            it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.opt("value") as? String
        }.distinct().singleOrNull()
    }
}
