package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nuvio.tv.R

@Composable
internal fun DanPlusWordmark(height: Dp, modifier: Modifier = Modifier) {
    BrandWordmark(
        contentDescription = stringResource(R.string.cd_nuvio_logo),
        modifier = modifier.width(height * BrandWordmarkAspect).height(height)
    )
}
