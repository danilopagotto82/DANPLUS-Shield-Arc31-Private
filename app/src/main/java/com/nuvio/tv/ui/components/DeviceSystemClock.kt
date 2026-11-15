package com.nuvio.tv.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Date

/** System clock for navigation chrome; never takes remote-control focus. */
@Composable
fun DeviceSystemClock(modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var timeLabel by remember(context) { mutableStateOf(deviceTimeLabel(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                timeLabel = deviceTimeLabel(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                timeLabel = deviceTimeLabel(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        timeLabel = deviceTimeLabel(context)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    Text(
        text = timeLabel,
        modifier = modifier.focusProperties { canFocus = false },
        style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize),
        color = NuvioTheme.colors.TextSecondary,
        maxLines = 1
    )
}

private fun deviceTimeLabel(context: Context): String =
    DateFormat.getTimeFormat(context).format(Date())
