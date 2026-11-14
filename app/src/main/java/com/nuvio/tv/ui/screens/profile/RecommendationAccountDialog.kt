@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.profile

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.account.InputField

@Composable
internal fun RecommendationAccountDialog(viewModel: DanexusProfilesViewModel, onDismiss: () -> Unit) {
    val email by viewModel.accountEmail.collectAsStateWithLifecycle()
    val profile by viewModel.viewer.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pin by remember(profile?.id) { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.clearMessage() }
    LaunchedEffect(locked, busy) { if (!locked && profile != null && !busy) onDismiss() }

    NuvioDialog(
        title = stringResource(if (locked) R.string.d12_unlock_action else R.string.d12_account_action),
        subtitle = email,
        onDismiss = { if (!busy) onDismiss() },
        width = 600.dp,
        solidWithoutBlur = true
    ) {
        message?.let { Text(stringResource(it)) }
        if (locked) {
            Text(stringResource(R.string.d12_pin_for_profile, profile?.name.orEmpty()))
            InputField(pin, { pin = it.filter(Char::isDigit).take(4) }, stringResource(R.string.d12_pin),
                KeyboardType.NumberPassword, isPassword = true)
            Button(onClick = { viewModel.unlockProfile(pin); pin = "" }, enabled = !busy && pin.length == 4) {
                Text(stringResource(R.string.d12_unlock_action))
            }
        } else {
            Text(stringResource(if (email == null) R.string.d12_setup_hint else R.string.d12_profile_sync_hint))
        }
        if (busy) Text(stringResource(R.string.d12_working))
    }
}
