package com.nuvio.tv.ui.screens.calendar

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.v2.appearance.LocalV2Appearance
import com.nuvio.tv.ui.v2.components.GlassRole
import com.nuvio.tv.ui.v2.components.NuvioGlassSurface
import com.nuvio.tv.ui.v2.components.nuvioGlass
import com.nuvio.tv.ui.v2.components.nuvioV2Focus
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun CalendarMonthView(
    viewModel: CalendarViewModel,
    uiState: CalendarUiState,
    formats: CalendarFormats,
    performance: Boolean,
    onOpen: (CalendarRow) -> Unit,
    onWatch: (CalendarRow) -> Unit,
    onOptions: (CalendarRow) -> Unit,
    tabsRequester: FocusRequester
) {
    val today = uiState.today ?: return
    val monthState by viewModel.month.collectAsState()
    val rowFocus by viewModel.focus.collectAsState()
    val month = monthState.month(today)
    val focusedDay = monthState.focusedDay(today)
    val gridRequester = remember { FocusRequester() }
    val dayListRequester = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val grid = remember(month, formats.firstDayOfWeek) { calendarMonthGrid(month, formats.firstDayOfWeek) }
    val dayRows = uiState.days[focusedDay].orEmpty()

    LaunchedEffect(Unit) { gridRequester.requestFocusAfterFrames() }
    LaunchedEffect(focusedDay, dayRows.firstOrNull()) {
        val first = dayRows.firstOrNull()
        if (first == null) viewModel.clearFocus() else viewModel.onRowFocused(first, fromList = false)
    }

    // Back anywhere in the month goes straight up to Week / List / Month; Back there leaves the Calendar as usual.
    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.keyCode != AndroidKeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
            if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP) runCatching { tabsRequester.requestFocus() }
            true
        }
    ) {
        Column(Modifier.weight(0.62f).fillMaxHeight().padding(bottom = NuvioTheme.spacing.xxl)) {
            // Up from the grid turns the month, so the month row is how focus gets back up to Week / List / Month;
            // Back from the grid jumps here.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                modifier = Modifier.focusRestorer()
            ) {
                CalendarRoundButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = formats.month(month.minusMonths(1)),
                    performance = performance,
                    onClick = { viewModel.focusDay(focusedDay.minusMonths(1)) },
                    modifier = Modifier.focusProperties { up = tabsRequester }
                )
                Text(
                    text = formats.month(month),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 150.dp)
                )
                CalendarRoundButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = formats.month(month.plusMonths(1)),
                    performance = performance,
                    onClick = { viewModel.focusDay(focusedDay.plusMonths(1)) },
                    modifier = Modifier.focusProperties { up = tabsRequester }
                )
            }
            Spacer(Modifier.height(NuvioTheme.spacing.sm))
            Row(Modifier.fillMaxWidth()) {
                grid.take(7).forEach { date ->
                    Text(
                        text = formats.weekday(date.dayOfWeek),
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextTertiary,
                        modifier = Modifier.weight(1f).padding(start = NuvioTheme.spacing.sm),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(NuvioTheme.spacing.xs))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(gridRequester)
                    .onFocusChanged { gridFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val key = when (native.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (rtl) CalendarMonthKey.RIGHT else CalendarMonthKey.LEFT
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (rtl) CalendarMonthKey.LEFT else CalendarMonthKey.RIGHT
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> CalendarMonthKey.UP
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> CalendarMonthKey.DOWN
                            else -> null
                        }
                        val select = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            native.keyCode == AndroidKeyEvent.KEYCODE_ENTER || native.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        when {
                            select -> {
                                if (native.action == AndroidKeyEvent.ACTION_UP) {
                                    viewModel.openDayInWeek(focusedDay, calendarWeekStart(focusedDay, formats.firstDayOfWeek))
                                }
                                true
                            }
                            key == null || native.action != AndroidKeyEvent.ACTION_DOWN -> false
                            else -> when (val move = calendarMonthMove(focusedDay, month, formats.firstDayOfWeek, key)) {
                                is CalendarMonthMove.To -> {
                                    viewModel.focusDay(move.date)
                                    true
                                }
                                is CalendarMonthMove.Leave -> if (move.key == CalendarMonthKey.RIGHT) {
                                    if (dayRows.isNotEmpty()) runCatching { dayListRequester.requestFocus() }
                                    true
                                } else false
                            }
                        }
                    }
                    .focusable()
            ) {
                grid.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        week.forEach { date ->
                            CalendarMonthCell(
                                date = date,
                                inMonth = YearMonth.from(date) == month,
                                today = date == today,
                                focused = gridFocused && date == focusedDay,
                                selected = date == focusedDay,
                                mosaic = calendarMosaic(uiState.days[date]),
                                hasPersonalTitle = uiState.days[date].orEmpty().any { it.personal },
                                performance = performance,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(NuvioTheme.spacing.xl))
        CalendarDayPanel(
            date = focusedDay,
            today = today,
            rows = dayRows,
            rowFocus = rowFocus,
            onBell = viewModel::toggleBell,
            onWatch = onWatch,
            formats = formats,
            performance = performance,
            firstRequester = dayListRequester,
            modifier = Modifier.weight(0.38f).fillMaxHeight().padding(bottom = NuvioTheme.spacing.xxl),
            onFocused = { viewModel.onRowFocused(it, fromList = false) },
            onOpen = onOpen,
            onOptions = onOptions
        )
    }
}

@Composable
private fun CalendarMonthCell(
    date: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    focused: Boolean,
    selected: Boolean,
    mosaic: CalendarMosaic,
    hasPersonalTitle: Boolean,
    performance: Boolean,
    modifier: Modifier
) {
    val v2 = LocalV2Appearance.current != null
    val shape = RoundedCornerShape(NuvioTheme.radii.sm)
    Box(modifier.padding(NuvioTheme.spacing.xxs)) {
        Box(
            Modifier
                .fillMaxSize()
                .nuvioV2Focus(focused, shape, stationary = true)
                .then(
                    when {
                        v2 && focused -> Modifier.nuvioGlass(GlassRole.CONTROL, focused = true, shape = shape)
                        focused -> Modifier.border(NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape)
                        else -> Modifier
                    }
                )
                .clip(shape)
                .background(
                    when {
                        selected && !focused -> NuvioTheme.colors.FocusBackground.copy(alpha = .5f)
                        inMonth -> NuvioTheme.colors.BackgroundCard.copy(alpha = .35f)
                        else -> Color.Transparent
                    }
                )
        ) {
            if (inMonth || mosaic.layout != CalendarMosaicLayout.NONE) {
                CalendarMosaicArt(mosaic, performance, Modifier.fillMaxSize().alpha(if (inMonth) 1f else 0.45f))
            }
            if (inMonth && hasPersonalTitle) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(NuvioTheme.spacing.xs)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NuvioTheme.colors.Secondary)
                )
            }
            val number = date.dayOfMonth.toString()
            Box(
                Modifier
                    .padding(NuvioTheme.spacing.xxs)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            today -> NuvioTheme.colors.Secondary
                            mosaic.layout != CalendarMosaicLayout.NONE -> Color.Black.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (today) FontWeight.Bold else FontWeight.SemiBold,
                    color = when {
                        today -> NuvioTheme.colors.OnSecondary
                        inMonth -> NuvioTheme.colors.TextPrimary
                        else -> NuvioTheme.colors.TextDisabled
                    }
                )
            }
        }
    }
}

/** One title fills the day, two share it side by side, three or four make a 2 x 2 grid with "+N" for the rest. */
@Composable
private fun CalendarMosaicArt(mosaic: CalendarMosaic, performance: Boolean, modifier: Modifier) {
    if (mosaic.layout == CalendarMosaicLayout.NONE) return
    val more = if (performance) mosaic.more + mosaic.tiles.size - 1 else mosaic.more
    val layout = if (performance) CalendarMosaicLayout.FULL else mosaic.layout
    Box(modifier) {
        when (layout) {
            CalendarMosaicLayout.FULL -> CalendarMosaicTile(mosaic.tiles.first(), 18.dp, Modifier.fillMaxSize())
            CalendarMosaicLayout.SPLIT -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                mosaic.tiles.forEach { CalendarMosaicTile(it, 11.dp, Modifier.weight(1f).fillMaxHeight()) }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                listOf(0, 2).forEach { start ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        for (index in start until start + 2) {
                            val tile = mosaic.tiles.getOrNull(index)
                            val cell = Modifier.weight(1f).fillMaxHeight()
                            when {
                                tile != null -> CalendarMosaicTile(tile, 9.dp, cell)
                                index == 3 && mosaic.more > 0 -> CalendarMoreTile(mosaic.more, cell)
                                else -> Spacer(cell)
                            }
                        }
                    }
                }
            }
        }
        if (performance && more > 0) {
            Text(
                text = stringResource(R.string.calendar_month_more, more),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(NuvioTheme.spacing.xxs)
                    .clip(RoundedCornerShape(NuvioTheme.radii.sm))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = NuvioTheme.spacing.xs)
            )
        }
    }
}

@Composable
private fun CalendarMosaicTile(row: CalendarRow, logoHeight: Dp, modifier: Modifier) {
    Box(modifier) {
        CalendarArtImage(url = row.backdrop, fallback = row.poster, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(calendarArtScrim()))
        if (!row.logo.isNullOrBlank() || logoHeight >= 18.dp) {
            CalendarLogo(
                logo = row.logo,
                name = row.name,
                height = logoHeight,
                maxWidth = 160.dp,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 3.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CalendarMoreTile(more: Int, modifier: Modifier) {
    Box(modifier.background(NuvioTheme.colors.BackgroundElevated), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.calendar_month_more, more),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NuvioTheme.colors.TextPrimary
        )
    }
}

@Composable
private fun CalendarDayPanel(
    date: LocalDate,
    today: LocalDate,
    rows: List<CalendarRow>,
    rowFocus: CalendarFocus?,
    onBell: (CalendarRow) -> Unit,
    onWatch: (CalendarRow) -> Unit,
    formats: CalendarFormats,
    performance: Boolean,
    firstRequester: FocusRequester,
    modifier: Modifier,
    onFocused: (CalendarRow) -> Unit,
    onOpen: (CalendarRow) -> Unit,
    onOptions: (CalendarRow) -> Unit
) {
    val first = rows.firstOrNull()
    val day = formats.longDate(date)
    if (first == null) {
        Column(modifier.padding(NuvioTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            Text(day, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary)
            Text(stringResource(R.string.calendar_month_day_empty), style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary)
        }
        return
    }
    val focus = rowFocus?.takeIf { focused -> rows.any { it.key == focused.row.key } } ?: CalendarFocus(first)
    CalendarSidePanel(
        focus = focus,
        formats = formats,
        today = today,
        next = emptyList(),
        performance = performance,
        onWatch = onWatch,
        onOpen = onOpen,
        onBell = onBell,
        modifier = modifier,
        footer = {
            val summary = calendarDaySummary(rows)
            val counts = listOfNotNull(
                summary.episodes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.calendar_month_episodes, it, it) },
                summary.films.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.calendar_month_films, it, it) }
            )
            Text(
                text = (listOf(day) + counts).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.padding(top = NuvioTheme.spacing.sm)
            )
            rows.forEachIndexed { index, row ->
                val press = remember(row.key) { CalendarPress() }
                val shape = RoundedCornerShape(NuvioTheme.radii.sm)
                CalendarFocusCard(
                    onClick = { press.click { onOpen(row) } },
                    shape = shape,
                    performance = performance,
                    focusedScale = 1f,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstRequester) else Modifier)
                        .calendarOptionsKeys(press) { onOptions(row) },
                    onFocus = { if (it) onFocused(row) }
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(NuvioTheme.colors.Secondary.copy(alpha = if (row.personal) 0.12f else 0.03f), shape)
                            .padding(NuvioTheme.spacing.sm)
                    ) {
                        Text(row.name ?: stringResource(R.string.calendar_unknown_title), style = MaterialTheme.typography.titleSmall,
                            color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(calendarRowLine(row), style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    )
}
