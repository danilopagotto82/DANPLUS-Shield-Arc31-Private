package com.nuvio.tv.ui.v2.profile

import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.nuvio.tv.R
import com.nuvio.tv.core.util.StartupLatencyTrace
import com.nuvio.tv.ui.brand.ArcIntroStage
import com.nuvio.tv.ui.brand.ArcStartHandoff
import com.nuvio.tv.ui.brand.DanPlusIntroTimeline
import com.nuvio.tv.ui.brand.DanPlusIntroView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

object EntryIdentPolicy {
    /** The spark has reached the bottom of the cut by now, so the exit can begin. */
    const val MinimumCycleMs = 4_600L
    const val RecoveryTimeoutMs = 10_000L
    /** The mark fades out first, then the dark stage dissolves into Home; the sound ends as Home is fully there. */
    const val ExitMs = 1_400L
    /** The entrance gets as long as it would have taken to play before the still mark stands in for it. */
    const val FirstFrameLimitMs = MinimumCycleMs
    /** A still mark that stands in late is held this long at least, so it never only flashes up. */
    const val FallbackHoldMs = 1_500L
    /** Art that is ready at the limit is one vsync from its first frame, so it gets a little longer. */
    const val ReadyGraceMs = 250L
    const val FirstFrameRecheckMs = 50L
    fun shouldFinish(elapsedMs: Long, homeReady: Boolean): Boolean =
        elapsedMs >= RecoveryTimeoutMs || (homeReady && elapsedMs >= MinimumCycleMs)

    enum class FirstFrame { Wait, Started, Fallback }

    /** A frame already drawn counts even when its message has not reached the main thread yet. */
    fun firstFrame(elapsedMs: Long, stage: ArcIntroStage?): FirstFrame = when (stage) {
        ArcIntroStage.Drawn -> FirstFrame.Started
        ArcIntroStage.Failed -> FirstFrame.Fallback
        ArcIntroStage.Ready -> if (elapsedMs >= FirstFrameLimitMs + ReadyGraceMs) FirstFrame.Fallback else FirstFrame.Wait
        else -> if (elapsedMs >= FirstFrameLimitMs) FirstFrame.Fallback else FirstFrame.Wait
    }

    /** How long the ident holds once it shows something, counted from then; [begunAtMs] is from the ident appearing. */
    fun holdMs(begunAtMs: Long, fallback: Boolean): Long =
        if (fallback) max(FallbackHoldMs, MinimumCycleMs - begunAtMs) else MinimumCycleMs

    fun exitOpacity(elapsedMs: Long, durationMs: Long = ExitMs): Float =
        if (elapsedMs >= durationMs) 0f else 1f - FastOutSlowInEasing.transform(elapsedMs.coerceAtLeast(0L).toFloat() / durationMs)

    /** Home is about to show the ident: it is pending, or it starts on entry as soon as Home is composed. */
    fun introComing(
        pending: Boolean,
        nuvioV2: Boolean,
        introEnabled: Boolean,
        startsOnEntry: Boolean,
        playbackUiActive: Boolean
    ): Boolean = !playbackUiActive && (pending || (nuvioV2 && introEnabled && startsOnEntry))

    /** The start window's mark stays under the entrance until it has drawn; the still fallback replaces it. */
    fun showsStartPicture(handoff: Boolean, failed: Boolean): Boolean = handoff && !failed

    /** What is left of the entrance's fade-in [sinceStartMs] after it started, with a frame to spare. */
    fun fadeInLeftMs(sinceStartMs: Long): Long =
        ((ArcStartHandoff.CrossfadeSeconds * 1000f).toLong() + FadeInSpareMs - sinceStartMs).coerceAtLeast(0L)
    private const val FadeInSpareMs = 50L
}

/** Fades the still mark out on the frame clock; the animator duration scale can shorten the fade but never stretch it. */
internal suspend fun fadeOutOnFrameClock(onOpacity: (Float) -> Unit) {
    val scale = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f).coerceIn(0f, 1f)
    val durationMs = (EntryIdentPolicy.ExitMs * scale).toLong()
    val startedAtMs = withFrameMillis { it }
    while (true) {
        val opacity = EntryIdentPolicy.exitOpacity(withFrameMillis { it } - startedAtMs, durationMs)
        onOpacity(opacity)
        if (opacity <= 0f) return
    }
}

/**
 * The start window's picture on Android 8 to 11 (the launcher mark where the entrance puts its logo). It stays on
 * screen through the loading boxes until the first real screen or the entrance's first frame takes its place.
 */
@Stable
class StartPicture(private val drawable: Drawable?, private val onEnd: () -> Unit = {}) {
    var showing by mutableStateOf(drawable != null)
        private set

    fun end() {
        if (!showing) return
        showing = false
        onEnd()
    }

    internal fun Modifier.drawPicture(): Modifier = drawBehind {
        val picture = drawable ?: return@drawBehind
        picture.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { picture.draw(it.nativeCanvas) }
    }
}

/** Draws the start window's picture while it is showing, otherwise [color]. */
fun Modifier.startPictureOr(picture: StartPicture?, color: Color): Modifier =
    if (picture != null && picture.showing) with(picture) { drawPicture() } else background(color)

/** Draws the start window's picture while it is showing, otherwise nothing. */
fun Modifier.startPicture(picture: StartPicture?): Modifier =
    if (picture != null && picture.showing) with(picture) { drawPicture() } else this

/** Presentation-only handoff, scoped to this Activity. No profile/session or navigation ownership. */
@Stable
class ProfileEntryState {
    var pending by mutableStateOf(false)
        private set
    var homeReady by mutableStateOf(false)
    var revealRequested by mutableStateOf(false)
        private set
    var revealing by mutableStateOf(false)
        private set
    /** Set once the intro has been on screen (or is switched off), not when it was only asked for. */
    var hasRunThisSession = false
        private set
    fun start(enabled: Boolean = true) {
        if (!enabled) hasRunThisSession = true
        homeReady = false; revealRequested = false; revealing = false; pending = enabled
    }
    fun markShown() { if (pending) hasRunThisSession = true }
    /** An intro that was asked for but ended before its first frame (Home rebuilt under it) is started again. */
    fun shouldStartOnEntry(): Boolean = !hasRunThisSession && !pending
    fun requestReveal() { if (homeReady) revealRequested = true }
    fun beginReveal() { revealing = true }
    fun finish() { pending = false }
}

val LocalProfileEntryState = staticCompositionLocalOf<ProfileEntryState?> { null }

/** Reuses the profile/Home handoff with the DAN+ surface and original stereo cue. */
@Composable
fun ProfileEntryIdent(
    state: ProfileEntryState,
    soundEnabled: Boolean = false,
    startPicture: StartPicture? = null,
    onReturnToProfiles: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    var surface by remember { mutableStateOf<DanPlusIntroView?>(null) }
    var started by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var minimumComplete by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var sound by remember { mutableStateOf<SoundPool?>(null) }
    var readySoundId by remember { mutableStateOf(0) }
    var soundCued by remember { mutableStateOf(false) }

    DisposableEffect(startPicture) { onDispose { startPicture?.end() } }
    DisposableEffect(lifecycle, soundEnabled, reducedMotion) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.finish()
        }
        lifecycle.addObserver(observer)
        val pool = if (soundEnabled && !reducedMotion) runCatching {
            SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            ).build()
        }.getOrNull() else null
        sound = pool
        pool?.setOnLoadCompleteListener { callbackPool, id, status ->
            if (callbackPool === sound && status == 0) readySoundId = id
        }
        if (pool != null) runCatching { pool.load(context, R.raw.danplus_intro_sound, 1) }
        onDispose {
            lifecycle.removeObserver(observer)
            sound = null
            readySoundId = 0
            pool?.setOnLoadCompleteListener(null)
            pool?.release()
        }
    }

    LaunchedEffect(Unit) {
        StartupLatencyTrace.cold("intro_ident", "danplus")
        delay(1_600)
        if (!started && !leaving) failed = true
        delay(EntryIdentPolicy.RecoveryTimeoutMs - 1_600)
        leaving = true
    }
    LaunchedEffect(started, failed) {
        if (!started && !failed) return@LaunchedEffect
        startPicture?.end()
        state.markShown()
        delay(if (reducedMotion) 0L else if (failed) 350L else DanPlusIntroTimeline.HoldMs)
        minimumComplete = true
    }
    LaunchedEffect(minimumComplete, state.homeReady, state.revealRequested) {
        if (state.homeReady && (minimumComplete || state.revealRequested)) leaving = true
    }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        startPicture?.end()
        state.beginReveal()
        val duration = if (reducedMotion) 0L else DanPlusIntroTimeline.ExitMs
        val view = surface
        if (view != null && started && !failed) {
            withTimeoutOrNull(duration + 700L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    view.exit(duration) { if (continuation.isActive) continuation.resume(Unit) }
                }
            }
        } else if (duration > 0 && failed) {
            val start = withFrameMillis { it }
            do {
                val elapsed = withFrameMillis { it } - start
                opacity = 1f - DanPlusIntroTimeline.ease(elapsed.toFloat() / duration)
            } while (opacity > 0f)
        }
        StartupLatencyTrace.cold("intro_finished", "danplus")
        state.finish()
    }
    BackHandler { state.finish(); onReturnToProfiles() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (failed) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = opacity }.background(IdentBackdrop),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.danplus_intro_logo), "DAN PLUS", Modifier.fillMaxWidth(.65f))
            }
        } else {
            if (!started) Box(Modifier.fillMaxSize().startPictureOr(startPicture, IdentBackdrop))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    DanPlusIntroView(
                        context = viewContext,
                        reducedMotion = reducedMotion,
                        onStarted = { if (!leaving && !failed) started = true },
                        onAudioCue = {
                            if (!soundCued && !leaving && !failed && readySoundId != 0 &&
                                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                soundCued = true
                                sound?.play(readySoundId, 1f, 1f, 1, 0, 1f)
                            }
                        },
                        onFailed = { failed = true }
                    ).also { it.contentDescription = "DAN PLUS"; surface = it }
                },
                onRelease = { if (surface === it) surface = null }
            )
        }
    }
}

private val IdentBackdrop = Color(0xFF03070D)
