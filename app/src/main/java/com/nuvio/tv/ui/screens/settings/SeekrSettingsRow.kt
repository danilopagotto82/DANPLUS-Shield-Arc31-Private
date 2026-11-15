package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.player.thumbnail.SeekrPreviews
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.account.InputField
import kotlinx.coroutines.launch

@Composable
internal fun SeekrSettingsRow(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configured by SeekrPreviews.configured.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.seekr_key_title),
        subtitle = stringResource(R.string.seekr_key_description),
        value = stringResource(if (configured) R.string.seekr_key_configured else R.string.seekr_key_unconfigured),
        enabled = enabled,
        onClick = { open = true }
    )
    if (open) {
        var key by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var checking by remember { mutableStateOf(false) }
        var keyCheck by remember { mutableStateOf<SeekrPreviews.KeyCheck?>(null) }
        fun save(value: String?) {
            saving = true
            failed = false
            scope.launch {
                val saved = SeekrPreviews.save(context, value)
                saving = false
                if (saved) open = false else failed = true
            }
        }
        NuvioDialog(
            title = stringResource(R.string.seekr_key_title),
            subtitle = stringResource(R.string.seekr_key_description),
            onDismiss = { if (!saving && !checking) open = false }
        ) {
            InputField(
                value = key,
                onValueChange = { if (!checking && !saving) { key = it; keyCheck = null; failed = false } },
                placeholder = stringResource(R.string.seekr_key_title),
                keyboardType = KeyboardType.Password,
                isPassword = true
            )
            if (failed) Text(stringResource(R.string.seekr_key_error))
            if (checking) {
                Text(stringResource(R.string.seekr_key_checking))
            } else {
                keyCheck?.let { result ->
                    Text(stringResource(when (result) {
                        SeekrPreviews.KeyCheck.Valid -> R.string.seekr_key_valid
                        SeekrPreviews.KeyCheck.Invalid -> R.string.seekr_key_invalid
                        SeekrPreviews.KeyCheck.Unavailable -> R.string.seekr_key_unavailable
                        SeekrPreviews.KeyCheck.RateLimited -> R.string.seekr_key_rate_limit
                    }))
                }
            }
            SettingsDialogActionButton(
                text = stringResource(R.string.seekr_key_test),
                onClick = {
                    checking = true
                    failed = false
                    scope.launch {
                        keyCheck = SeekrPreviews.checkKey(key)
                        checking = false
                    }
                },
                enabled = !saving && !checking && key.isNotBlank()
            )
            SettingsDialogActionButton(
                text = stringResource(R.string.action_save),
                onClick = { save(key) },
                enabled = !saving && !checking && SeekrPreviews.hasValidFormat(key)
            )
            if (configured) {
                SettingsDialogActionButton(
                    text = stringResource(R.string.seekr_key_remove),
                    onClick = { save(null) },
                    enabled = !saving && !checking
                )
            }
        }
    }
}
