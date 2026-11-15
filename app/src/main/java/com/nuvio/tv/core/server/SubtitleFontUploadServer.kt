package com.nuvio.tv.core.server

import android.content.Context
import android.os.SystemClock
import com.nuvio.tv.R
import com.nuvio.tv.core.player.ImportedSubtitleFont
import com.nuvio.tv.core.player.ImportedSubtitleFonts
import fi.iki.elonen.NanoHTTPD
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred

internal class SubtitleFontUploadServer private constructor(
    private val context: Context,
    private val localIp: String,
    private val token: String = UUID.randomUUID().toString().replace("-", "")
) : NanoHTTPD(localIp, 0) {
    private val createdAt = SystemClock.elapsedRealtime()
    private val path = "/font/$token"
    private val importing = AtomicBoolean()
    private var closed = false
    val result = CompletableDeferred<ImportedSubtitleFont>()
    val url: String get() = "http://$localIp:$listeningPort$path"

    @Synchronized
    private fun isOpen(): Boolean =
        !closed && !result.isCompleted && SystemClock.elapsedRealtime() - createdAt < LIFETIME_MS

    override fun stop() {
        synchronized(this) { closed = true }
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        val host = "$localIp:$listeningPort"
        if (session.uri != path || session.headers["host"] != host || !isOpen()) {
            return reply(Response.Status.NOT_FOUND, "Not found")
        }
        return when (session.method) {
            Method.GET -> reply(Response.Status.OK, page(), "text/html; charset=UTF-8")
            Method.POST -> upload(session, host)
            else -> reply(Response.Status.METHOD_NOT_ALLOWED, "Method not allowed")
        }
    }

    private fun upload(session: IHTTPSession, host: String): Response {
        if (session.headers["origin"] != "http://$host" ||
            session.headers["content-type"] != "application/octet-stream" ||
            session.headers.containsKey("transfer-encoding")
        ) return reply(Response.Status.FORBIDDEN, "Invalid upload")

        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 1..ImportedSubtitleFonts.MAX_BYTES) {
            return reply(Response.Status.BAD_REQUEST, "Invalid size")
        }
        if (!importing.compareAndSet(false, true)) return reply(Response.Status.FORBIDDEN, "Upload in progress")
        try {
            val staged = ImportedSubtitleFonts.read(context, session.inputStream, length)
            try {
                synchronized(this) {
                    if (!isOpen()) return reply(Response.Status.FORBIDDEN, "Upload closed")
                    result.complete(ImportedSubtitleFonts.install(context, staged))
                }
                return reply(Response.Status.OK, "OK")
            } finally {
                staged.file.delete()
            }
        } catch (_: Exception) {
            return reply(Response.Status.BAD_REQUEST, "Invalid font")
        } finally {
            importing.set(false)
        }
    }

    private fun reply(status: Response.Status, body: String, type: String = "text/plain; charset=UTF-8"): Response =
        newFixedLengthResponse(status, type, body).apply {
            closeConnection(true)
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
        }

    private fun page(): String = """
        <!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${context.getString(R.string.subtitle_import_font)}</title>
        <style>
          body{margin:0;background:#0d1620;color:#eaf4fa;font:1.1rem system-ui}
          main{box-sizing:border-box;max-width:32rem;margin:10vh auto;padding:1.5rem;border:1px solid #344858;border-radius:1rem;background:#162534}
          button,input{font:inherit}button{border:0;border-radius:.5rem;padding:.7rem 1.15rem;background:#7dd3e6;color:#0d1620}
          input{display:block;max-width:100%;margin:1.3rem 0}
        </style>
        <main><h1>${context.getString(R.string.subtitle_import_font)}</h1>
        <p>${context.getString(R.string.subtitle_import_formats)}</p>
        <input id="font" type="file" accept=".ttf,.otf">
        <button id="send" type="button">${context.getString(R.string.subtitle_import_send)}</button>
        <p id="status" role="status" data-failed="${context.getString(R.string.subtitle_import_failed)}"
           data-success="${context.getString(R.string.subtitle_import_success)}"></p></main>
        <script>
          const send = document.getElementById('send'), status = document.getElementById('status');
          send.onclick = async () => {
            const file = document.getElementById('font').files[0];
            if (!file || file.size > ${ImportedSubtitleFonts.MAX_BYTES} || !/\.(otf|ttf)$/i.test(file.name)) {
              status.textContent = status.dataset.failed; return;
            }
            send.disabled = true;
            try {
              const response = await fetch(location.pathname, {method:'POST',redirect:'error',
                headers:{'Content-Type':'application/octet-stream'},body:file});
              if (!response.ok) throw Error();
              status.textContent = status.dataset.success;
            } catch (_) { status.textContent = status.dataset.failed; send.disabled = false; }
          };
        </script>
    """.trimIndent()

    companion object {
        const val LIFETIME_MS = 180_000L

        fun open(context: Context): SubtitleFontUploadServer? {
            val address = DeviceIpAddress.get(context)?.takeIf {
                InetAddress.getByName(it).isSiteLocalAddress
            } ?: return null
            val server = SubtitleFontUploadServer(context.applicationContext, address)
            return try {
                server.start(SOCKET_READ_TIMEOUT, true)
                server
            } catch (_: Exception) {
                server.stop()
                null
            }
        }
    }
}
