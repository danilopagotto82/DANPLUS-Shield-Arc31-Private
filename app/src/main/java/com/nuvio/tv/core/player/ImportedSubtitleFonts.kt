package com.nuvio.tv.core.player

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

internal data class ImportedSubtitleFont(val id: String, val family: String, val file: File)

internal object ImportedSubtitleFonts {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val SELECTION_KEY = "subtitle_imported_font_id"
    private val safeId = Regex("[a-f0-9]{32}")

    private fun root(context: Context) = File(context.noBackupFilesDir, "subtitle-fonts-v2")

    fun rendererDirectory(context: Context): File = File(root(context), "faces").also {
        check(it.isDirectory || it.mkdirs())
    }

    private fun labels(context: Context) = File(root(context), "labels")

    fun all(context: Context): List<ImportedSubtitleFont> =
        labels(context).listFiles().orEmpty().mapNotNull { get(context, it.name) }.sortedBy { it.family }

    fun get(context: Context, id: String?): ImportedSubtitleFont? {
        if (id == null || !safeId.matches(id)) return null
        return runCatching {
            val file = listOf("ttf", "otf").map { File(root(context), "faces/local-$id.$it") }.first { it.isFile }
            val label = File(labels(context), id).readText().trim().takeIf(String::isNotBlank) ?: return null
            ImportedSubtitleFont(id, label, file)
        }.getOrNull()
    }

    /** Stages a complete font before it is visible to either renderer. */
    fun read(context: Context, input: InputStream, length: Int): ImportedSubtitleFont {
        require(length in 1..MAX_BYTES)
        val base = root(context)
        check(base.isDirectory || base.mkdirs())
        val temporary = File.createTempFile("incoming-", null, base)
        try {
            temporary.outputStream().buffered().use { out ->
                val buffer = ByteArray(32 * 1024)
                var left = length
                while (left > 0) {
                    val count = input.read(buffer, 0, minOf(left, buffer.size))
                    require(count > 0)
                    out.write(buffer, 0, count)
                    left -= count
                }
            }
            val (extension, name) = readOpenType(temporary)
            val id = UUID.randomUUID().toString().replace("-", "")
            val validated = File(base, "$id.$extension")
            Typeface.createFromFile(temporary)
            check(temporary.renameTo(validated))
            return ImportedSubtitleFont(id, name, validated)
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun install(context: Context, staged: ImportedSubtitleFont): ImportedSubtitleFont {
        val dir = labels(context)
        check(dir.isDirectory || dir.mkdirs())
        val marker = File(dir, staged.id)
        val target = File(rendererDirectory(context), "local-${staged.file.name}")
        marker.writeText(staged.family)
        if (!staged.file.renameTo(target)) {
            marker.delete()
            error("Could not install subtitle font")
        }
        return staged.copy(file = target)
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        val font = get(context, id) ?: return false
        if (!font.file.delete()) return false
        File(labels(context), id).delete()
        return true
    }

    private fun readOpenType(file: File): Pair<String, String> = RandomAccessFile(file, "r").use { font ->
        val extension = when (font.readInt()) {
            0x00010000, 0x74727565 -> "ttf"
            0x4F54544F -> "otf"
            else -> error("Invalid OpenType header")
        }
        val count = font.readUnsignedShort()
        require(count in 1..256)
        val directoryEnd = 12L + 16L * count
        require(directoryEnd <= font.length())
        var nameStart = 0L
        var nameLength = 0L
        font.seek(12)
        repeat(count) {
            val tag = font.readInt()
            font.readInt()
            val start = font.readInt().toLong() and 0xffffffffL
            val length = font.readInt().toLong() and 0xffffffffL
            require(start >= directoryEnd && start + length <= font.length())
            if (tag == 0x6E616D65) {
                require(nameStart == 0L)
                nameStart = start
                nameLength = length
            }
        }
        require(nameLength >= 6)
        font.seek(nameStart)
        val format = font.readUnsignedShort()
        val records = font.readUnsignedShort()
        val stringsOffset = font.readUnsignedShort()
        require(format in 0..1 && records > 0 && 6L + 12L * records <= stringsOffset && stringsOffset <= nameLength)
        var family = ""
        var familyScore = 0
        repeat(records) {
            val platform = font.readUnsignedShort()
            font.readUnsignedShort()
            val language = font.readUnsignedShort()
            val nameId = font.readUnsignedShort()
            val size = font.readUnsignedShort()
            val offset = font.readUnsignedShort()
            val score = when (nameId) {
                16 -> 30
                1 -> 20
                4 -> 10
                else -> 0
            } + if (language == 0x409 || platform == 0) 1 else 0
            if ((nameId != 1 && nameId != 4 && nameId != 16) || (platform != 0 && platform != 3) ||
                score <= familyScore || size !in 2..512 || size % 2 != 0 ||
                stringsOffset + offset + size > nameLength
            ) return@repeat
            val next = font.filePointer
            val bytes = ByteArray(size)
            font.seek(nameStart + stringsOffset + offset)
            font.readFully(bytes)
            font.seek(next)
            val value = bytes.toString(Charsets.UTF_16BE).trim()
            if (value.isNotBlank() && value.none(Char::isISOControl)) {
                family = value
                familyScore = score
            }
        }
        require(family.isNotBlank())
        extension to family
    }
}
