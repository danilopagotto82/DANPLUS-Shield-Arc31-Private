package com.nuvio.tv.core.tmdb

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object TmdbApiKey {
    @Volatile private var override: String? = null
    private val _configured = MutableStateFlow(BuildConfig.TMDB_API_KEY.isNotBlank())
    val configured = _configured.asStateFlow()
    val value: String get() = override ?: BuildConfig.TMDB_API_KEY

    fun load(context: Context) {
        override = store(context).read(0)
        _configured.value = value.isNotBlank()
    }

    fun save(context: Context, key: String?) {
        val clean = key?.trim()?.takeIf(String::isNotBlank)
        store(context).write(0, clean)
        override = clean
        _configured.value = value.isNotBlank()
    }

    private fun store(context: Context) =
        ProtectedProfilePreferences(context, "danplus_tmdb_key", "danplus_tmdb_key_v1")
}
