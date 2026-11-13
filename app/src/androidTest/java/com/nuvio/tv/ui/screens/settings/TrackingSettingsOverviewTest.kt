package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingSettingsOverviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun providerRowsAreOrderedAndShowDisconnectedStatus() {
        setOverview()

        composeRule.onNodeWithTag(TrackingSettingsTestTags.TRAKT_PROVIDER).assertIsDisplayed()
        composeRule.onNodeWithTag("tracking_provider_simkl").assertDoesNotExist()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.MDBLIST_PROVIDER).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            context.getString(R.string.tracking_status_disconnected)
        ).assertCountEquals(3)

        val traktTop = composeRule
            .onNodeWithTag(TrackingSettingsTestTags.TRAKT_PROVIDER)
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        val mdblistTop = composeRule.onNodeWithTag(TrackingSettingsTestTags.MDBLIST_PROVIDER)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(traktTop < mdblistTop)
    }

    @Test
    fun connectedProviderRowsShowUsernamesAndStatus() {
        setOverview(
            traktState = TraktUiState(
                mode = TraktConnectionMode.CONNECTED,
                username = "trakt-user"
            ),
            mdbListState = MdbListTrackerUiState(isConnected = true, username = "mdb-user"),
            trackingState = TrackingSettingsUiState(
                connectedProviderIds = setOf(
                    TrackingProviderId.TRAKT,
                    TrackingProviderId.MDBLIST
                ),
                isReady = true
            )
        )

        composeRule.onAllNodesWithText(
            context.getString(R.string.tracking_status_connected)
        ).assertCountEquals(2)
        composeRule.onNodeWithText(
            context.getString(R.string.trakt_connected_as, "trakt-user")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.mdblist_connected_as, "mdb-user")
        ).assertIsDisplayed()
    }

    @Test
    fun connectedMdbListDisplaysUsernameAndSelectedWatchSource() {
        setOverview(
            mdbListState = MdbListTrackerUiState(isConnected = true, username = "mdb-viewer"),
            trackingState = TrackingSettingsUiState(
                watchProgressSource = WatchProgressSource.MDBLIST,
                connectedProviderIds = setOf(TrackingProviderId.MDBLIST), isReady = true
            )
        )
        composeRule.onNodeWithText(context.getString(R.string.mdblist_connected_as, "mdb-viewer")).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.tracking_status_connected)).assertCountEquals(1)
        composeRule.onNodeWithTag(TrackingSettingsTestTags.OVERVIEW_LIST).performScrollToIndex(1)
        composeRule.onNodeWithTag(TrackingSettingsTestTags.WATCH_PROGRESS_SOURCE).assertIsEnabled()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.LIBRARY_SOURCE).assertIsEnabled()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.CONTINUE_WATCHING).assertDoesNotExist()
    }

    @Test
    fun disconnectedTraktHidesItsProviderFeatures() {
        setOverview()

        composeRule.onNodeWithTag(TrackingSettingsTestTags.CONTINUE_WATCHING).assertDoesNotExist()
        composeRule.onNodeWithTag("tracking_trakt_comments").assertDoesNotExist()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.MORE_LIKE_THIS).assertDoesNotExist()
    }

    @Test
    fun connectedTraktRequiresTraktProgressOnlyForContinueWatchingWindow() {
        setOverview(
            traktState = TraktUiState(mode = TraktConnectionMode.CONNECTED),
            trackingState = TrackingSettingsUiState(
                watchProgressSource = WatchProgressSource.NUVIO_SYNC,
                connectedProviderIds = setOf(TrackingProviderId.TRAKT),
                isReady = true
            )
        )
        scrollToTraktFeatures()

        composeRule.onNodeWithTag(TrackingSettingsTestTags.CONTINUE_WATCHING)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("tracking_trakt_comments")
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.MORE_LIKE_THIS)
            .assertIsEnabled()
    }

    @Test
    fun TraktProgressEnablesEveryConnectedTraktFeature() {
        setOverview(
            traktState = TraktUiState(mode = TraktConnectionMode.CONNECTED),
            trackingState = TrackingSettingsUiState(
                watchProgressSource = WatchProgressSource.TRAKT,
                librarySourceMode = LibrarySourceMode.TRAKT,
                connectedProviderIds = setOf(TrackingProviderId.TRAKT),
                isReady = true
            )
        )
        scrollToTraktFeatures()

        composeRule.onNodeWithTag(TrackingSettingsTestTags.CONTINUE_WATCHING)
            .assertIsEnabled()
        composeRule.onNodeWithTag("tracking_trakt_comments")
            .assertDoesNotExist()
        composeRule.onNodeWithTag(TrackingSettingsTestTags.MORE_LIKE_THIS)
            .assertIsEnabled()
    }

    @Test
    fun remoteFocusMovesFromTraktThroughSimklToMdbList() {
        lateinit var traktFocusRequester: FocusRequester
        composeRule.setContent {
            NuvioTheme {
                traktFocusRequester = remember { FocusRequester() }
                TrackingSettingsOverview(
                    traktState = TraktUiState(),
                    mdbListState = MdbListTrackerUiState(),
                    trackingState = TrackingSettingsUiState(isReady = true),
                    traktFocusRequester = traktFocusRequester,
                    mdbListFocusRequester = remember { FocusRequester() },
                    libraryFocusRequester = remember { FocusRequester() },
                    watchProgressFocusRequester = remember { FocusRequester() },
                    continueWatchingFocusRequester = remember { FocusRequester() },
                    moreLikeThisFocusRequester = remember { FocusRequester() },
                    onTraktClick = {},
                    onMdbListClick = {},
                    onLibrarySourceClick = {},
                    onWatchProgressClick = {},
                    onContinueWatchingWindowClick = {},
                    onMoreLikeThisClick = {},
                )
            }
        }
        composeRule.runOnIdle {
            traktFocusRequester.requestFocus()
        }

        composeRule.onNodeWithTag(TrackingSettingsTestTags.TRAKT_PROVIDER)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(TrackingSettingsTestTags.MDBLIST_PROVIDER).assertIsFocused()
    }

    @Test
    fun disconnectedDeviceAuthorizationShowsRetryAction() {
        composeRule.setContent {
            NuvioTheme {
                TraktAccountDialog(
                    state = TraktUiState(),
                    onStartConnection = {},
                    onRetryPolling = {},
                    onDisconnect = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_retry)).assertExists()
    }

    @Test
    fun awaitingDeviceAuthorizationShowsCodeAndCancelAction() {
        composeRule.setContent {
            NuvioTheme {
                TraktAccountDialog(
                    state = TraktUiState(
                        mode = TraktConnectionMode.AWAITING_APPROVAL,
                        deviceUserCode = "ABCD1234",
                        verificationUrl = "https://trakt.tv/activate",
                        deviceCodeExpiresAtMillis = System.currentTimeMillis() + 60_000L
                    ),
                    onStartConnection = {},
                    onRetryPolling = {},
                    onDisconnect = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("ABCD1234").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).assertExists()
    }

    private fun setOverview(
        traktState: TraktUiState = TraktUiState(),
        mdbListState: MdbListTrackerUiState = MdbListTrackerUiState(),
        trackingState: TrackingSettingsUiState = TrackingSettingsUiState(isReady = true)
    ) {
        composeRule.setContent {
            NuvioTheme {
                TrackingSettingsOverview(
                    traktState = traktState,
                    mdbListState = mdbListState,
                    trackingState = trackingState,
                    traktFocusRequester = remember { FocusRequester() },
                    mdbListFocusRequester = remember { FocusRequester() },
                    libraryFocusRequester = remember { FocusRequester() },
                    watchProgressFocusRequester = remember { FocusRequester() },
                    continueWatchingFocusRequester = remember { FocusRequester() },
                    moreLikeThisFocusRequester = remember { FocusRequester() },
                    onTraktClick = {},
                    onMdbListClick = {},
                    onLibrarySourceClick = {},
                    onWatchProgressClick = {},
                    onContinueWatchingWindowClick = {},
                    onMoreLikeThisClick = {},
                )
            }
        }
    }

    private fun scrollToTraktFeatures() {
        composeRule.onNodeWithTag(TrackingSettingsTestTags.OVERVIEW_LIST)
            .performScrollToIndex(2)
    }
}
