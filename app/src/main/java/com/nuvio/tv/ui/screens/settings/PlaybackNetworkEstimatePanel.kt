package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

private val fileProfiles = listOf(
    R.string.network_estimate_720 to 4,
    R.string.network_estimate_1080 to 8,
    R.string.network_estimate_4k to 20,
    R.string.network_estimate_1080_remux to 25,
    R.string.network_estimate_4k_remux to 60,
    R.string.network_estimate_heavy_remux to 100
)

@Composable
internal fun GeneralPlaybackNetworkEstimate(downloadMbps: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
        Text(
            stringResource(R.string.network_estimate_general),
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 660.dp) 6 else 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fileProfiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, bitrate) ->
                            PlaybackEstimateTile(
                                title = stringResource(name),
                                bitrate = bitrate,
                                estimate = NetworkEstimate.from(downloadMbps, bitrate.toDouble()),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.network_estimate_profiles),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@Composable
private fun PlaybackEstimateTile(title: String, bitrate: Int, estimate: NetworkEstimate, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val (glyph, color) = when (estimate) {
        NetworkEstimate.Favorable -> "✓" to Color(0xFF80DBA8)
        NetworkEstimate.Marginal -> "!" to Color(0xFFF2C578)
        NetworkEstimate.Unfavorable -> "×" to Color(0xFFF5A1A6)
        NetworkEstimate.Unknown -> "–" to NuvioTheme.colors.TextSecondary
    }
    val label = stringResource(estimate.label())
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.07f), shape)
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
        Text(
            glyph,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            modifier = Modifier.semantics { contentDescription = label }
        )
        Text(
            stringResource(R.string.network_estimate_profile_rate, bitrate),
            style = MaterialTheme.typography.labelSmall,
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@Composable
internal fun AssessmentPlaybackNetworkSummary(baselineMbps: Double?) {
    val speed = baselineMbps?.takeIf { it.isFinite() && it > 0 } ?: return
    val profiles = fileProfiles.filter { (name, _) ->
        name == R.string.network_estimate_1080 || name == R.string.network_estimate_4k ||
            name == R.string.network_estimate_4k_remux
    }
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
        Text(stringResource(R.string.assessment_summary_source, speed), style = MaterialTheme.typography.bodySmall)
        for ((name, bitrate) in profiles) {
            val estimate = NetworkEstimate.from(speed, bitrate.toDouble())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                Text(stringResource(name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(estimate.label()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun StreamPlaybackNetworkEstimate(serverMbps: Double?, totalBitrate: Long?, sizeBytes: Long?) {
    val result = NetworkEstimate.from(serverMbps, totalBitrate?.let { it / 1_000_000.0 })
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
        Text(
            stringResource(R.string.network_estimate_file, stringResource(result.label())),
            style = MaterialTheme.typography.titleSmall
        )
        if (sizeBytes != null) {
            Text(stringResource(R.string.network_estimate_file_size, sizeBytes / 1_000_000_000.0), style = MaterialTheme.typography.bodySmall)
        }
        if (result == NetworkEstimate.Unknown) {
            Text(stringResource(R.string.network_estimate_file_missing), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun NetworkEstimate.label(): Int = when (this) {
    NetworkEstimate.Favorable -> R.string.network_estimate_favorable
    NetworkEstimate.Marginal -> R.string.network_estimate_marginal
    NetworkEstimate.Unfavorable -> R.string.network_estimate_unfavorable
    NetworkEstimate.Unknown -> R.string.network_estimate_unknown
}
