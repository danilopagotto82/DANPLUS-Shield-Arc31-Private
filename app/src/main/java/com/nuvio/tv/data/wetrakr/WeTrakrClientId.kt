package com.nuvio.tv.data.wetrakr

import android.content.Context
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeTrakrClientId @Inject constructor(@ApplicationContext context: Context) {
    private val store = ProtectedProfilePreferences(context, "danplus_wetrakr_client", "danplus_wetrakr_client_v1")
    private val saved = MutableStateFlow(store.read(0))
    val changes = saved.asStateFlow()

    internal fun current(): String? = saved.value

    internal fun save(value: String) {
        val clean = value.trim()
        require(clean.isNotEmpty() && clean.length <= 256 && clean.none(Char::isWhitespace))
        store.write(0, clean)
        saved.value = clean
    }
}
