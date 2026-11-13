@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.danexus.RecommendationProfile
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun RecommendationAccountDialog(viewModel: DanexusProfilesViewModel, onDismiss: () -> Unit) {
    val email by viewModel.accountEmail.collectAsStateWithLifecycle()
    val profiles by viewModel.recommendationProfiles.collectAsStateWithLifecycle()
    val viewer by viewModel.viewer.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val localProfiles by viewModel.profileManager.profiles.collectAsStateWithLifecycle()
    val active by viewModel.profileManager.activeProfileId.collectAsStateWithLifecycle()
    val local = localProfiles.firstOrNull { it.id == active }
    val pictures by viewModel.profilePictures.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember(active, viewer?.id, locked) { mutableStateOf("") }
    var target by remember(active, email) { mutableStateOf<RecommendationProfile?>(null) }
    var creating by remember(active, email) { mutableStateOf(false) }
    var confirmSignOut by remember(email) { mutableStateOf(false) }
    var linking by remember(active, email) { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.clearMessage(); viewModel.loadProfiles() }
    LaunchedEffect(viewer?.id, locked) {
        if (viewer != null && !locked) {
            creating = false
            linking = false
            target = null
            pin = ""
        }
    }

    NuvioDialog(
        title = stringResource(R.string.d12_account_action),
        subtitle = email?.let { account ->
            local?.let { stringResource(R.string.d12_local_profile, it.name, account) } ?: account
        },
        onDismiss = { if (!busy) onDismiss() },
        width = 600.dp,
        solidWithoutBlur = true
    ) {
        message?.let { Text(stringResource(it), color = NuvioTheme.colors.TextSecondary) }
        if (busy) Text(stringResource(R.string.d12_working), color = NuvioTheme.colors.TextSecondary)
        when {
            email == null -> {
                Text(stringResource(R.string.d12_login_hint))
                InputField(address, { address = it.take(254) }, stringResource(R.string.d12_email), KeyboardType.Email)
                InputField(password, { password = it.take(128) }, stringResource(R.string.d12_password),
                    KeyboardType.Password, isPassword = true)
                val canLogin = !busy && address.trim().contains('@') && password.isNotBlank()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.login(address, password, false); password = "" }, enabled = canLogin) {
                        Text(stringResource(R.string.d12_login))
                    }
                    Button(onClick = { viewModel.login(address, password, true); password = "" },
                        enabled = canLogin && password.length >= 8) {
                        Text(stringResource(R.string.d12_register))
                    }
                }
            }
            confirmSignOut -> {
                Text(stringResource(R.string.d12_signout_confirm))
                Button(onClick = { viewModel.signOut(); confirmSignOut = false }, enabled = !busy) {
                    Text(stringResource(R.string.d12_signout))
                }
            }
            local != null && (creating || target != null || (locked && !linking)) -> {
                val chosen = target
                if (creating) Text(stringResource(R.string.d12_create_named_profile, local.name))
                else Text(stringResource(
                    if (chosen?.pinEnabled == false) R.string.d12_confirm_link else R.string.d12_pin_for_profile,
                    chosen?.name ?: viewer?.name.orEmpty()
                ))
                if (creating || chosen?.pinEnabled != false) {
                    InputField(
                        pin, { value -> pin = value.filter { it in '0'..'9' }.take(4) },
                        stringResource(if (creating) R.string.d12_pin_optional else R.string.d12_pin),
                        KeyboardType.NumberPassword, isPassword = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        when {
                            creating -> viewModel.createProfile(local, pin)
                            chosen != null -> viewModel.bindProfile(local, chosen, pin)
                            else -> viewModel.unlockProfile(pin)
                        }
                        pin = ""
                    }, enabled = !busy && (chosen?.pinEnabled == false || pin.length == 4 || (creating && pin.isEmpty()))) {
                        Text(stringResource(when {
                            creating -> R.string.d12_create_profile
                            chosen != null -> R.string.d12_bind_profile
                            else -> R.string.d12_unlock_action
                        }))
                    }
                    if (creating || chosen != null) Button(onClick = {
                        creating = false; target = null; pin = ""
                    }, enabled = !busy) { Text(stringResource(R.string.d12_cancel)) }
                }
            }
            local != null && (viewer == null || linking) -> {
                Text(stringResource(R.string.d12_link_hint))
                val available = profiles.filter { profile ->
                    viewModel.localProfileFor(profile)?.id.let { it == null || it == local.id }
                }
                if (available.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 165.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(available, key = { it.id }) { profile ->
                        Button(onClick = { target = profile }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            IndicationProfileName(profile, viewModel.localProfileFor(profile)?.id?.let(pictures::get))
                        }
                    }
                }
                if (profiles.size < 6) Button(onClick = { creating = true }, enabled = !busy) {
                    Text(stringResource(R.string.d12_create_profile))
                }
            }
            viewer != null -> {
                Text(stringResource(R.string.d12_linked_profile, viewer?.name.orEmpty()))
                Button(onClick = { linking = true }, enabled = !busy) {
                    Text(stringResource(R.string.d12_change_link))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.d12_close)) }
            if (email != null && !confirmSignOut) Button(onClick = { confirmSignOut = true }, enabled = !busy) {
                Text(stringResource(R.string.d12_signout))
            }
        }
    }
}
