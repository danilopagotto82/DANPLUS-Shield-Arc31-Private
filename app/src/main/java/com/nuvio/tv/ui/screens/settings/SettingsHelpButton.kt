package com.nuvio.tv.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.scout.ScoutChip

@Composable
internal fun SettingsHelpButton(@StringRes title: Int, @StringRes message: Int) {
    val topic = stringResource(title)
    val buttonFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var showHelp by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    val dismiss = {
        showHelp = false
        restore = true
    }
    LaunchedEffect(showHelp, restore) {
        if (showHelp) closeFocus.requestFocusAfterFrames()
        else if (restore) {
            buttonFocus.requestFocusAfterFrames()
            restore = false
        }
    }
    ScoutChip(
        label = "",
        modifier = Modifier.focusRequester(buttonFocus),
        onClick = { showHelp = true },
        icon = { Icon(Icons.Outlined.Info, stringResource(R.string.dan_settings_help, topic), Modifier.size(21.dp)) }
    )
    if (showHelp) {
        NuvioDialog(title = topic, onDismiss = dismiss, solidWithoutBlur = true) {
            Text(
                text = stringResource(message),
                modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()).focusable()
            )
            SettingsDialogActionButton(
                text = stringResource(R.string.action_close),
                onClick = dismiss,
                modifier = Modifier.focusRequester(closeFocus)
            )
        }
    }
}
