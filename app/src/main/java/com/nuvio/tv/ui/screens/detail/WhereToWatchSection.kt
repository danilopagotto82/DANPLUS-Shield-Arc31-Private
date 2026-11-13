@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.tv.ui.screens.detail

import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.data.remote.api.TmdbWatchRegion
import com.nuvio.tv.domain.model.Meta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface Offers {
    data object Loading : Offers
    data object Failed : Offers
    data class Loaded(val region: TmdbWatchRegion?) : Offers
}

@Composable
internal fun WhereToWatchSection(
    load: suspend () -> TmdbWatchRegion?,
    meta: Meta,
    showTitle: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val currentLoad by rememberUpdatedState(load)
    val offers by produceState<Offers>(Offers.Loading, meta.id, meta.apiType) {
        value = Offers.Loading
        value = try {
            Offers.Loaded(currentLoad())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Offers.Failed
        }
    }
    val region = (offers as? Offers.Loaded)?.region
    val groups = region?.let {
        listOf(
            R.string.detail_watch_subscription to it.subscription,
            R.string.detail_watch_free to (it.free + it.ads),
            R.string.detail_watch_rent to it.rent,
            R.string.detail_watch_buy to it.buy
        ).filter { (_, providers) -> providers.isNotEmpty() }
    }.orEmpty()
    val context = LocalContext.current
    val launcher = remember(context) { StreamingAppLauncher(context) }
    val scope = rememberCoroutineScope()
    var opening by remember(meta.id, meta.apiType) { mutableStateOf(false) }
    var status by remember(meta.id, meta.apiType) { mutableStateOf<String?>(null) }
    val currentMeta by rememberUpdatedState(meta)
    val focusModifier = Modifier.focusRequester(focusRequester).focusProperties {
        upFocusRequester?.let { up = it }
        downFocusRequester?.let { down = it }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) Text(stringResource(R.string.detail_tab_where_to_watch))
        if (groups.isEmpty()) {
            val message = when (offers) {
                Offers.Loading -> R.string.detail_watch_loading
                Offers.Failed -> R.string.detail_watch_error
                is Offers.Loaded -> R.string.detail_watch_empty
            }
            Text(stringResource(message), modifier = focusModifier.focusable())
        } else {
            groups.forEachIndexed { groupIndex, (label, providers) ->
                Text(stringResource(label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.distinctBy { it.id }.forEachIndexed { providerIndex, provider ->
                        val logo = provider.logo?.takeIf { it.startsWith("/") }
                        Button(
                            modifier = if (groupIndex == 0 && providerIndex == 0) focusModifier else Modifier.focusProperties {
                                upFocusRequester?.let { up = it }
                            },
                            onClick = {
                                if (!opening) scope.launch {
                                    val title = currentMeta
                                    opening = true
                                    status = context.getString(R.string.detail_watch_opening, provider.name)
                                    try {
                                        val app = StreamingApp.forProvider(provider.id)
                                        val installed = launcher.installed(app)
                                        val direct = if (app != null && installed.isNotEmpty()) {
                                            streamingTitleLink(app, title.links) ?: StreamingTitleLookup.find(
                                                app, title.id, title.imdbId, title.apiType == "movie"
                                            )
                                        } else null
                                        if (currentMeta.id != title.id || currentMeta.apiType != title.apiType) return@launch
                                        status = when {
                                            direct != null && launcher.openTitle(direct, installed) -> null
                                            launcher.openApp(installed) -> context.getString(
                                                R.string.detail_watch_search_in_app, title.name, provider.name
                                            ).also { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                            launcher.openStore(app, provider.name) -> context.getString(
                                                R.string.detail_watch_store_hint, provider.name
                                            )
                                            else -> context.getString(R.string.detail_watch_app_unavailable, provider.name)
                                        }
                                    } finally {
                                        opening = false
                                    }
                                }
                            }
                        ) {
                            if (logo != null) AsyncImage(
                                "https://image.tmdb.org/t/p/w92$logo", null, Modifier.size(26.dp)
                            )
                            Text(provider.name, modifier = Modifier.padding(start = if (logo != null) 8.dp else 0.dp), maxLines = 1)
                        }
                    }
                }
            }
            status?.let { Text(it) }
            Text(stringResource(R.string.detail_watch_attribution))
        }
    }
}
