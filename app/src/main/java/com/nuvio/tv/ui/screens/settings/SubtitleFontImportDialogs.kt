@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.player.ImportedSubtitleFont
import com.nuvio.tv.core.player.ImportedSubtitleFonts
import com.nuvio.tv.core.server.SubtitleFontUploadServer
import com.nuvio.tv.ui.components.NuvioDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun ImportedSubtitleFontsDialog(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fonts by remember { mutableStateOf(ImportedSubtitleFonts.all(context)) }
    var remove by remember { mutableStateOf<ImportedSubtitleFont?>(null) }
    var removalFailed by remember { mutableStateOf(false) }
    val selected = fonts.firstOrNull { it.id == selectedId }

    NuvioDialog(title = stringResource(R.string.subtitle_import_manage), onDismiss = onDismiss, solidWithoutBlur = true) {
        Button(onClick = onUpload) { Text(stringResource(R.string.subtitle_import_font)) }
        if (removalFailed) Text(stringResource(R.string.subtitle_import_remove_failed))
        if (fonts.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(fonts, key = { it.id }) { font ->
                    Button(onClick = { onSelect(font.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (font.id == selectedId) "✓ ${font.family}" else font.family, maxLines = 1)
                    }
                }
            }
        }
        if (selected != null) {
            Button(onClick = { onSelect(null); onDismiss() }) { Text(stringResource(R.string.subtitle_import_builtin)) }
            Button(onClick = { remove = selected }) { Text(stringResource(R.string.subtitle_import_remove)) }
        }
    }
    remove?.let { font ->
        NuvioDialog(
            title = stringResource(R.string.subtitle_import_remove),
            subtitle = font.family,
            onDismiss = { remove = null },
            solidWithoutBlur = true
        ) {
            Button(onClick = { remove = null }) { Text(stringResource(R.string.d12_cancel)) }
            Button(onClick = {
                removalFailed = !ImportedSubtitleFonts.remove(context, font.id)
                if (!removalFailed) {
                    onSelect(null)
                    fonts = ImportedSubtitleFonts.all(context)
                }
                remove = null
            }) { Text(stringResource(R.string.d12_remove)) }
        }
    }
}

@Composable
internal fun SubtitleFontUploadDialog(
    onUploaded: (ImportedSubtitleFont) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val latestUpload by rememberUpdatedState(onUploaded)
    var url by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(context) {
        var server: SubtitleFontUploadServer? = null
        try {
            withContext(Dispatchers.IO) {
                server = runCatching { SubtitleFontUploadServer.open(context) }.getOrNull()
            }
            val active = server
            if (active == null) {
                message = R.string.error_network_required
                return@LaunchedEffect
            }
            url = active.url
            val font = withTimeoutOrNull(SubtitleFontUploadServer.LIFETIME_MS) { active.result.await() }
            url = null
            if (font == null) {
                message = R.string.subtitle_import_expired
            } else {
                latestUpload(font)
                message = R.string.subtitle_import_success
                delay(1_000)
            }
        } finally {
            withContext(Dispatchers.IO + NonCancellable) { server?.stop() }
        }
    }
    NuvioDialog(title = stringResource(R.string.subtitle_import_font), onDismiss = onDismiss, solidWithoutBlur = true) {
        url?.let { address ->
            Text(stringResource(R.string.subtitle_import_from_phone))
            SiloQrCode(address, stringResource(R.string.subtitle_import_font))
            Text(address)
        }
        message?.let { Text(stringResource(it)) }
        Button(onClick = onDismiss) { Text(stringResource(R.string.d12_close)) }
    }
}
