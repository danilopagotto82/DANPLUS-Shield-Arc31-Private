package com.nuvio.tv.core.build

import com.nuvio.tv.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * DAN+ builds use their own inbox. Builds with a separately configured private Arc relay can still use it.
 */
object IssueReports {
    val available: Boolean get() = danplusBaseUrl != null || isConfigured(BuildConfig.PLAYBACK_REPORTS_BASE_URL, BuildConfig.REPORTS_SIGNING_KEY)
    internal const val DANPLUS_MAX_JSON_BYTES = 800_000

    internal val danplusBaseUrl: String?
        get() {
            val url = BuildConfig.DANPLUS_D12_SUPABASE_URL.trim().trimEnd('/')
            if (!DANPLUS_SUPABASE_HOST.matches(url)) return null
            if (BuildConfig.DANPLUS_REPORTS_ANON_KEY.isBlank()) return null
            return "$url/functions/v1/danplus-reports/"
        }

    internal fun isConfigured(baseUrl: String?, signingKey: String? = "set"): Boolean {
        if (baseUrl.isNullOrBlank() || signingKey.isNullOrBlank()) return false
        val url = baseUrl.trim().toHttpUrlOrNull() ?: return false
        return url.isHttps && !isOfficialHost(url.host)
    }

    internal fun isOfficialHost(host: String): Boolean {
        val name = host.trimEnd('.').lowercase()
        return OFFICIAL_DOMAINS.any { name == it || name.endsWith(".$it") }
    }

    private val OFFICIAL_DOMAINS = listOf("nuvio.tv", "nuvio.app", "nuvioapp.space", "nuviomedia.com", "nuviomedia.app")
    private val DANPLUS_SUPABASE_HOST = Regex("https://[a-z0-9-]+\\.supabase\\.co")
}
