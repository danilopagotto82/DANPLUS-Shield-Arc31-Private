package com.nuvio.tv.ui.brand

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.nuvio.tv.R
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal object DanPlusIntroTimeline {
    const val AudioStartMs = 450L
    const val SweepStartMs = 605L
    const val SweepEndMs = 1_190L
    const val GlintStartMs = 1_220L
    const val GlintEndMs = 1_660L
    const val HoldMs = 1_880L
    const val ExitMs = 560L

    fun ease(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** DAN+ entry surface. Home owns navigation and the decision to reveal. */
@SuppressLint("ViewConstructor")
internal class DanPlusIntroView(
    context: Context,
    private val reducedMotion: Boolean,
    private val onStarted: () -> Unit,
    private val onAudioCue: () -> Unit,
    private val onFailed: () -> Unit
) : SurfaceView(context), SurfaceHolder.Callback {
    private val main = Handler(Looper.getMainLooper())
    private var renderer: Renderer? = null

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val scale = minOf(1f, 1280f / w, 720f / h)
        holder.setFixedSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderer = Renderer(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer?.invalidate()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderer?.close()
        renderer = null
    }

    fun exit(durationMs: Long, done: () -> Unit) {
        renderer?.exit(durationMs, done) ?: done()
    }

    private inner class Renderer(private val target: SurfaceHolder) : Choreographer.FrameCallback {
        private val thread = HandlerThread("DanPlusIntro").apply { start() }
        private val handler = Handler(thread.looper)
        private lateinit var clock: Choreographer
        private var scene: Scene? = null
        private var startedAt = -1L
        private var exitAt = -1L
        private var exitDuration = 0L
        private var exitDone: (() -> Unit)? = null
        private var audioCued = false
        private var scheduled = false
        private var finished = false
        private val drawLock = ReentrantLock()
        @Volatile private var closed = false

        init {
            handler.post {
                if (closed) return@post
                clock = Choreographer.getInstance()
                scene = runCatching { Scene(context) }.getOrNull()
                if (scene == null) fail() else schedule()
            }
        }

        fun invalidate() { handler.post { schedule() } }

        fun exit(durationMs: Long, done: () -> Unit) {
            handler.post {
                if (finished || closed || startedAt < 0L) {
                    finished = true
                    main.post { done() }
                    return@post
                }
                exitDuration = durationMs
                exitDone = done
                schedule()
            }
        }

        fun close() {
            closed = true
            if (drawLock.tryLock(250L, TimeUnit.MILLISECONDS)) drawLock.unlock()
            handler.post {
                if (::clock.isInitialized) clock.removeFrameCallback(this)
                scene = null
                exitDone?.let { main.post { it() } }
                exitDone = null
                thread.quitSafely()
            }
        }

        private fun schedule() {
            if (!::clock.isInitialized || scheduled || closed || finished) return
            scheduled = true
            clock.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            scheduled = false
            if (closed || finished) return
            val art = scene ?: return
            if (!target.surface.isValid) {
                fail()
                return
            }
            val now = frameTimeNanos / 1_000_000L
            val elapsed = if (startedAt < 0L) 0L else now - startedAt
            if (exitDone != null && exitAt < 0L) exitAt = now
            val exitProgress = when {
                exitAt < 0L -> 0f
                exitDuration <= 0L -> 1f
                else -> (now - exitAt).toFloat() / exitDuration
            }
            val alpha = 1f - DanPlusIntroTimeline.ease(exitProgress)
            val rendered = try {
                drawLock.lock()
                if (closed) return
                runCatching {
                    val surface = target.surface
                    val canvas = surface.lockHardwareCanvas()
                    try {
                        art.draw(canvas, if (reducedMotion) DanPlusIntroTimeline.HoldMs else elapsed, alpha)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                }.isSuccess
            } finally {
                drawLock.unlock()
            }
            if (!rendered) {
                fail()
                return
            }
            if (startedAt < 0L) {
                startedAt = now
                main.post { if (!closed) onStarted() }
            }
            if (!reducedMotion && exitDone == null && !audioCued && elapsed >= DanPlusIntroTimeline.AudioStartMs) {
                audioCued = true
                if (elapsed <= DanPlusIntroTimeline.AudioStartMs + 45L) {
                    main.post { if (!closed) onAudioCue() }
                }
            }
            if (exitProgress >= 1f) {
                finished = true
                exitDone?.let { main.post { it() } }
                exitDone = null
            } else if (exitAt >= 0L || (!reducedMotion && elapsed < DanPlusIntroTimeline.HoldMs)) {
                schedule()
            }
        }

        private fun fail() {
            finished = true
            main.post { if (!closed) onFailed() }
            exitDone?.let { main.post { it() } }
            exitDone = null
        }
    }

    private class Scene(context: Context) {
        private val resources = context.resources
        private val logo: Bitmap = requireNotNull(BitmapFactory.decodeResource(
            resources, R.drawable.danplus_intro_logo, BitmapFactory.Options().apply { inSampleSize = 2 }
        ))
        private val mask = BitmapShader(
            requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.danplus_intro_crown_mask)),
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
        )
        private val backdrop = Paint().apply {
            shader = RadialGradient(640f, 340f, 742f, intArrayOf(0xFF16293C.toInt(), 0xFF091522.toInt(), 0xFF03070D.toInt()),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        }
        private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val logoRect = RectF(224f, 221.333f, 1056f, 498.667f)
        private val sweep = LinearGradient(-65f, 0f, 40f, 70f,
            intArrayOf(Color.TRANSPARENT, 0x35FFE1A4, 0xEBFFF3CE.toInt(), 0x65FFCC68, Color.TRANSPARENT),
            floatArrayOf(0f, .50f, .58f, .68f, 1f), Shader.TileMode.CLAMP)
        private val sweepMatrix = Matrix()
        private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = ComposeShader(mask, sweep, PorterDuff.Mode.SRC_IN)
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 25f, intArrayOf(0xFFFFF8DF.toInt(), 0xAAFFEBBC.toInt(), 0x38FFD182, 0x00FFCC70),
                floatArrayOf(0f, .15f, .44f, 1f), Shader.TileMode.CLAMP)
        }
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF5D9.toInt() }
        private val star = Path().apply {
            moveTo(0f, -17f)
            quadTo(1f, -2f, 11f, 0f)
            quadTo(1f, 1f, 0f, 16f)
            quadTo(-1f, 1f, -11f, 0f)
            quadTo(-1f, -2f, 0f, -17f)
            close()
        }

        fun draw(canvas: Canvas, elapsed: Long, exitAlpha: Float) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            if (exitAlpha <= 0f) return
            val save = canvas.save()
            canvas.scale(canvas.width / 1280f, canvas.height / 720f)
            backdrop.alpha = (255 * exitAlpha).roundToInt()
            canvas.drawRect(0f, 0f, 1280f, 720f, backdrop)
            val alpha = exitAlpha * DanPlusIntroTimeline.ease(elapsed / 490f)
            logoPaint.alpha = (alpha * 255).roundToInt()
            canvas.drawBitmap(logo, null, logoRect, logoPaint)
            if (elapsed in DanPlusIntroTimeline.SweepStartMs until DanPlusIntroTimeline.SweepEndMs) {
                val progress = (elapsed - DanPlusIntroTimeline.SweepStartMs) / 585f
                val crownSave = canvas.save()
                canvas.translate(logoRect.left, logoRect.top)
                canvas.scale(832f / 2172f, 832f / 2172f)
                sweepMatrix.setTranslate(36f + 546f * progress, 0f)
                sweep.setLocalMatrix(sweepMatrix)
                sweepPaint.alpha = (sin(PI * progress) * .91 * alpha * 255).roundToInt()
                canvas.drawRect(0f, 0f, 543f, 399f, sweepPaint)
                canvas.restoreToCount(crownSave)
            }
            if (elapsed in DanPlusIntroTimeline.GlintStartMs until DanPlusIntroTimeline.GlintEndMs) {
                val t = elapsed - DanPlusIntroTimeline.GlintStartMs
                val intensity = DanPlusIntroTimeline.ease(t / 48f) *
                    (1f - DanPlusIntroTimeline.ease((t - 48L) / 392f))
                val glintSave = canvas.save()
                canvas.translate(logoRect.left + 832f * 493f / 2172f, logoRect.top + 832f * 155f / 2172f)
                glow.alpha = (intensity * alpha * 255).roundToInt()
                canvas.drawCircle(0f, 0f, 25f, glow)
                canvas.rotate(-10.3f)
                starPaint.alpha = glow.alpha
                canvas.drawPath(star, starPaint)
                canvas.restoreToCount(glintSave)
            }
            canvas.restoreToCount(save)
        }
    }
}
