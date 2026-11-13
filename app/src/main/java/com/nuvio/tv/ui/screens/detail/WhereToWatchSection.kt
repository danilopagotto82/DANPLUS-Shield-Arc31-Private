@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.data.remote.api.TmdbWatchRegion
import kotlinx.coroutines.CancellationException

private sealed interface Offers {
    data object Loading : Offers
    data object Failed : Offers
    data class Loaded(val region: TmdbWatchRegion?) : Offers
}

@Composable
internal fun WhereToWatchSection(
    load: suspend () -> TmdbWatchRegion?,
    titleId: String,
    showTitle: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val currentLoad by rememberUpdatedState(load)
    val offers by produceState<Offers>(Offers.Loading, titleId) {
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
    val link = region?.link?.takeIf { it.startsWith("https://www.themoviedb.org/") }
    val uriHandler = LocalUriHandler.current
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
            groups.forEach { (label, providers) ->
                Text(stringResource(label))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.distinctBy { it.id }.forEach { provider ->
                        val logo = provider.logo?.takeIf { it.startsWith("/") }
                        if (logo != null) {
                            AsyncImage("https://image.tmdb.org/t/p/w92$logo", provider.name, Modifier.size(36.dp))
                        } else {
                            Text(provider.name, maxLines = 1)
                        }
                    }
                }
            }
            Text(stringResource(R.string.detail_watch_attribution))
            if (link != null) {
                Button(
                    onClick = { runCatching { uriHandler.openUri(link) } },
                    modifier = focusModifier
                ) {
                    Text(stringResource(R.string.detail_watch_open_tmdb))
                }
            } else {
                Text(stringResource(R.string.detail_watch_brazil), modifier = focusModifier.focusable())
            }
        }
    }
}
