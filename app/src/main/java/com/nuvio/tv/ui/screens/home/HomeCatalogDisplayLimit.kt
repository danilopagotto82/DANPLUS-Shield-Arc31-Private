package com.nuvio.tv.ui.screens.home

import android.app.ActivityManager
import android.content.Context

@Volatile private var cachedCatalogLimit: Int? = null

internal fun homeCatalogDisplayLimit(context: Context): Int = cachedCatalogLimit ?: run {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
    val limited = manager?.isLowRamDevice == true || memory.totalMem in 1 until 2_560L * 1024 * 1024
    (if (limited) 96 else 150).also { cachedCatalogLimit = it }
}
