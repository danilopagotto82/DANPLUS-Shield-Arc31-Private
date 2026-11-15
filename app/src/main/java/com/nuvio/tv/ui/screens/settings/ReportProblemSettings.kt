@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.repository.AccountReport
import com.nuvio.tv.data.repository.AppReportChoices
import com.nuvio.tv.data.repository.AppReportKind
import com.nuvio.tv.data.repository.SentReport
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.components.NuvioScrollDefaults
import com.nuvio.tv.ui.screens.player.PlaybackIssueReportStatus
import com.nuvio.tv.ui.screens.player.ReadyToSendStep
import com.nuvio.tv.ui.screens.player.ReportEyebrow
import com.nuvio.tv.ui.screens.player.ReportHeading
import com.nuvio.tv.ui.screens.player.ReportNoteDialog
import com.nuvio.tv.ui.screens.player.ReportSentDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.CancellationException

internal enum class ReportWhere(val key: String, @StringRes val label: Int) {
    Home("home_rows", R.string.report_where_home),
    Search("search", R.string.report_where_search),
    TitlePage("title_or_sources", R.string.report_where_title_page),
    Settings("settings", R.string.report_where_settings),
    SignIn("sign_in_or_sync", R.string.report_where_sign_in),
    Addons("addons", R.string.report_where_addons),
    MediaServers("media_servers", R.string.report_where_servers),
    WatchParty("watch_party", R.string.report_where_party),
    Elsewhere("somewhere_else", R.string.report_where_other)
}

private enum class ReportDialogKind { PlaybackHint, Bug, Idea, SentList, AccountList }

private enum class ReportFormStep { Where, Note, Ready }

private fun reportAge(timeMs: Long): String =
    DateUtils.getRelativeTimeSpanString(timeMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReportProblemSettingsContent(
    initialFocusRequester: FocusRequester?,
    viewModel: ReportProblemSettingsViewModel = hiltViewModel(),
    crashViewModel: CrashReportViewModel = hiltViewModel()
) {
    val recorderEnabled by viewModel.recorderEnabled.collectAsStateWithLifecycle()
    val sentReports by viewModel.sentReports.collectAsStateWithLifecycle()
    val reportAccount by viewModel.reportAccount.collectAsStateWithLifecycle()
    val send by viewModel.send.collectAsStateWithLifecycle()
    val askAfterExit by crashViewModel.promptEnabled.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ReportDialogKind?>(null) }
    val open: (ReportDialogKind) -> Unit = { kind ->
        viewModel.resetSend()
        dialog = kind
    }
    val close: () -> Unit = {
        viewModel.resetSend()
        dialog = null
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.report_panel_title),
            subtitle = stringResource(R.string.report_settings_subtitle)
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides NuvioScrollDefaults.smoothScrollSpec) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                contentPadding = PaddingValues(vertical = NuvioTheme.spacing.sm)
            ) {
                item(key = "report") {
                    SettingsGroupCard(title = stringResource(R.string.report_group_choose)) {
                        SettingsActionRow(
                            title = stringResource(R.string.report_row_playback),
                            subtitle = stringResource(R.string.report_row_playback_hint),
                            onClick = { open(ReportDialogKind.PlaybackHint) },
                            modifier = if (initialFocusRequester != null) Modifier.focusRequester(initialFocusRequester) else Modifier
                        )
                        SettingsActionRow(
                            title = stringResource(R.string.report_row_bug),
                            subtitle = stringResource(R.string.report_row_bug_hint),
                            onClick = { open(ReportDialogKind.Bug) }
                        )
                        SettingsActionRow(
                            title = stringResource(R.string.report_row_idea),
                            subtitle = stringResource(R.string.report_row_idea_hint),
                            onClick = { open(ReportDialogKind.Idea) }
                        )
                        SettingsActionRow(
                            title = stringResource(R.string.report_row_crash),
                            subtitle = stringResource(R.string.report_row_crash_hint),
                            onClick = crashViewModel::openFromSettings
                        )
                    }
                }
                if (reportAccount != null) {
                    item(key = "accountReports") {
                        SettingsActionRow(
                            title = stringResource(R.string.report_account_title),
                            subtitle = stringResource(R.string.report_account_hint),
                            onClick = { open(ReportDialogKind.AccountList) }
                        )
                    }
                }
                item(key = "sentReports") {
                    val latest = sentReports.firstOrNull()
                    SettingsActionRow(
                        title = stringResource(R.string.report_sent_list_title),
                        subtitle = latest?.let {
                            stringResource(R.string.report_sent_list_latest, it.code, reportAge(it.timeMs))
                        } ?: stringResource(R.string.report_sent_list_empty),
                        value = sentReports.size.takeIf { it > 0 }?.toString(),
                        onClick = { open(ReportDialogKind.SentList) }
                    )
                }
                item(key = "options") {
                    SettingsGroupCard(title = stringResource(R.string.report_group_options)) {
                        SettingsToggleRow(
                            title = stringResource(R.string.report_recorder_title),
                            subtitle = stringResource(R.string.report_recorder_hint),
                            checked = recorderEnabled,
                            onToggle = { viewModel.setRecorderEnabled(!recorderEnabled) }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.report_exit_prompt_title),
                            subtitle = stringResource(R.string.report_exit_prompt_hint),
                            checked = askAfterExit,
                            onToggle = { crashViewModel.setPromptEnabled(!askAfterExit) }
                        )

                    }
                }
            }
        }
    }

    when (dialog) {
        ReportDialogKind.PlaybackHint -> ReportHintDialog(
            title = stringResource(R.string.report_row_playback),
            message = stringResource(R.string.report_playback_dialog_message),
            onClose = close
        )
        ReportDialogKind.Bug -> ReportFormDialog(AppReportKind.Bug, send, viewModel::send, close)
        ReportDialogKind.Idea -> ReportFormDialog(AppReportKind.Idea, send, viewModel::send, close)
        ReportDialogKind.SentList -> SentReportsDialog(sentReports, close)
        ReportDialogKind.AccountList -> {
            val account = reportAccount
            if (account == null) {
                LaunchedEffect(Unit) { close() }
            } else {
                key(account) {
                    AccountReportsDialog(load = { viewModel.loadAccountReports(account) }, onClose = close)
                }
            }
        }
        null -> Unit
    }

    CrashReportDialogs(crashViewModel)
    val crashAnswer by crashViewModel.answer.collectAsStateWithLifecycle()
    crashAnswer?.let { answer ->
        ReportHintDialog(
            title = stringResource(R.string.report_row_crash),
            message = stringResource(
                if (answer == CrashCheckAnswer.AndroidCannotTell) R.string.report_exit_cannot_tell else R.string.report_exit_none
            ),
            onClose = crashViewModel::close
        )
    }
}

@Composable
private fun ReportHintDialog(title: String, message: String, onClose: () -> Unit) {
    val closeButton = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeButton.requestFocus() }
    }
    NuvioDialog(onDismiss = onClose, title = title, subtitle = message, solidWithoutBlur = true) {
        SettingsDialogActionRow {
            SettingsDialogActionButton(
                text = stringResource(R.string.action_close),
                onClick = onClose,
                primary = true,
                modifier = Modifier.focusRequester(closeButton)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportFormDialog(
    kind: AppReportKind,
    send: ReportSendState,
    onSend: (AppReportChoices) -> Unit,
    onClose: () -> Unit
) {
    if (send.isFinished()) {
        ReportSentDialog(send.code.takeUnless { send.status == PlaybackIssueReportStatus.Saved }, send.followUrl, started = false, onClose = onClose)
        return
    }
    val firstStep = if (kind == AppReportKind.Bug) ReportFormStep.Where else ReportFormStep.Note
    var step by remember { mutableStateOf(firstStep) }
    var where by remember { mutableStateOf<ReportWhere?>(null) }
    var note by remember { mutableStateOf("") }
    val includeLog = kind == AppReportKind.Bug
    var noteOpen by remember { mutableStateOf(false) }
    val sending = send.status == PlaybackIssueReportStatus.Sending

    if (step == ReportFormStep.Note) {
        ReportNoteDialog(
            current = note,
            onSave = {
                note = it
                step = ReportFormStep.Ready
            },
            onDismiss = onClose
        )
        return
    }

    NuvioDialog(
        onDismiss = dismiss@{
            if (sending) return@dismiss
            if (step == ReportFormStep.Ready) step = firstStep else onClose()
        },
        title = "",
        width = 560.dp,
        usePlatformDefaultWidth = false,
        solidWithoutBlur = true
    ) {
        if (step == ReportFormStep.Where) {
            ReportWhereStep(
                selected = where,
                onPick = {
                    where = it
                    step = ReportFormStep.Ready
                }
            )
        } else {
            ReportEyebrow()
            ReadyToSendStep(
                playbackRowTitle = null,
                titleLabel = null,
                showLogRow = includeLog,
                logHint = stringResource(R.string.report_item_log_hint_app),
                note = note,
                noteHint = stringResource(
                    if (kind == AppReportKind.Bug) R.string.report_item_note_bug_hint else R.string.report_item_note_idea_hint
                ),
                focusNote = note.isBlank(),
                onOpenNote = { noteOpen = true },
                sending = sending,
                failed = send.status == PlaybackIssueReportStatus.Failed,
                error = send.error,
                onSend = {
                    onSend(AppReportChoices(kind, where?.key, note.takeIf { it.isNotBlank() }, includeLog))
                },
                onCancel = onClose
            )
        }
    }

    if (noteOpen) {
        ReportNoteDialog(
            current = note,
            onSave = {
                note = it
                noteOpen = false
            },
            onDismiss = { noteOpen = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.ReportWhereStep(selected: ReportWhere?, onPick: (ReportWhere) -> Unit) {
    val places = ReportWhere.entries
    val focusPlace = selected ?: places.first()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (places.indexOf(focusPlace) - 2).coerceAtLeast(0))
    val focusRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRow.requestFocus() }
    }
    ReportHeading(stringResource(R.string.report_where_title))
    CompositionLocalProvider(LocalBringIntoViewSpec provides NuvioScrollDefaults.smoothScrollSpec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
            contentPadding = PaddingValues(vertical = NuvioTheme.spacing.sm)
        ) {
            items(places, key = { it.key }) { place ->
                SettingsActionRow(
                    title = stringResource(place.label),
                    subtitle = null,
                    trailingIcon = null,
                    jumpAnchor = false,
                    onClick = { onPick(place) },
                    modifier = if (place == focusPlace) Modifier.focusRequester(focusRow) else Modifier
                )
            }
        }
    }
}

@StringRes
private fun sentKindLabel(kind: String): Int? = when (kind) {
    "playback" -> R.string.report_sent_kind_playback
    AppReportKind.Bug.key -> R.string.report_sent_kind_bug
    AppReportKind.Idea.key -> R.string.report_sent_kind_idea
    AppReportKind.Crash.key -> R.string.report_row_crash
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentReportsDialog(reports: List<SentReport>, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    NuvioDialog(
        onDismiss = onClose,
        title = stringResource(R.string.report_sent_list_title),
        subtitle = stringResource(
            if (reports.isEmpty()) R.string.report_sent_list_empty else R.string.report_sent_copy_hint
        ),
        width = 560.dp,
        usePlatformDefaultWidth = false,
        solidWithoutBlur = true
    ) {
        if (reports.isNotEmpty()) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides NuvioScrollDefaults.smoothScrollSpec) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
                    contentPadding = PaddingValues(vertical = NuvioTheme.spacing.sm)
                ) {
                    items(reports, key = { it.code }) { report ->
                        SettingsActionRow(
                            title = report.code,
                            subtitle = listOfNotNull(
                                sentKindLabel(report.kind)?.let { stringResource(it) },
                                stringResource(R.string.report_sent_title)
                            ).joinToString(" · "),
                            value = dateFormat.format(java.util.Date(report.timeMs)),
                            trailingIcon = null,
                            jumpAnchor = false,
                            onClick = {
                                clipboard?.let {
                                    it.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.report_sent_list_title), report.code))
                                    Toast.makeText(context, R.string.report_sent_copied, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = if (report == reports.first()) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        }
        SettingsDialogActionRow {
            SettingsDialogActionButton(
                text = stringResource(R.string.action_close),
                onClick = onClose,
                primary = true,
                modifier = if (reports.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@StringRes
private fun accountReportStatus(status: String): Int = when (status) {
    "received" -> R.string.report_sent_title
    "read" -> R.string.report_account_read
    "cause_found" -> R.string.report_account_cause_found
    "fixed" -> R.string.report_account_fixed
    "not_danplus" -> R.string.report_account_not_danplus
    "closed" -> R.string.report_account_closed
    else -> R.string.report_account_unknown
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountReportsDialog(
    load: suspend () -> List<AccountReport>,
    onClose: () -> Unit
) {
    var reports by remember { mutableStateOf<List<AccountReport>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AccountReport?>(null) }
    val firstFocus = remember { FocusRequester() }
    val current = selected
    val context = LocalContext.current
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }
    fun date(value: String): String = runCatching {
        dateFormat.format(java.util.Date.from(java.time.OffsetDateTime.parse(value).toInstant()))
    }.getOrDefault("")

    LaunchedEffect(Unit) {
        try {
            reports = load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        }
    }
    LaunchedEffect(reports, loadFailed, current) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    val rows = reports
    NuvioDialog(
        onDismiss = { if (current == null) onClose() else selected = null },
        title = stringResource(if (current == null) R.string.report_account_title else R.string.report_account_replies),
        subtitle = current?.id,
        width = 560.dp,
        usePlatformDefaultWidth = false,
        solidWithoutBlur = true
    ) {
        if (current == null) {
            when {
                loadFailed -> Text(stringResource(R.string.report_account_unavailable))
                rows == null -> Text(stringResource(R.string.report_account_loading))
                rows.isEmpty() -> Text(stringResource(R.string.report_account_empty))
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(rows, key = { it.id }) { report ->
                        val label = sentKindLabel(report.kind)?.let { stringResource(it) } ?: report.id
                        SettingsActionRow(
                            title = label,
                            subtitle = listOf(date(report.receivedAt), stringResource(accountReportStatus(report.status)))
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { selected = report },
                            modifier = if (report == rows.first()) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        } else {
            Text(stringResource(accountReportStatus(current.status)))
            if (current.replies.isEmpty()) {
                Text(stringResource(R.string.report_account_no_reply))
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(current.replies) { reply ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(date(reply.at), color = NuvioTheme.colors.TextSecondary)
                            Text(reply.text)
                        }
                    }
                }
            }
        }
        SettingsDialogActionRow {
            if (current != null) {
                SettingsDialogActionButton(
                    text = stringResource(R.string.report_account_copy_protocol),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.let {
                            it.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.report_account_title), current.id))
                            Toast.makeText(context, R.string.report_sent_copied, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            SettingsDialogActionButton(
                text = stringResource(R.string.action_close),
                primary = true,
                onClick = { if (current == null) onClose() else selected = null },
                modifier = if (current != null || rows.isNullOrEmpty()) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}
