package com.nuvio.tv.ui.screens.settings

internal enum class NetworkEstimate {
    Favorable, Marginal, Unfavorable, Unknown;

    companion object {
        fun from(speedMbps: Double?, fileMbps: Double?): NetworkEstimate {
            if (speedMbps == null || fileMbps == null || !speedMbps.isFinite() ||
                !fileMbps.isFinite() || speedMbps <= 0 || fileMbps <= 0
            ) return Unknown
            return when {
                speedMbps >= fileMbps * 2 -> Favorable
                speedMbps >= fileMbps -> Marginal
                else -> Unfavorable
            }
        }
    }
}
