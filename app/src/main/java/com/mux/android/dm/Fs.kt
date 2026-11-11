package com.mux.android.dm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Cat { VIDEO, DOCUMENT, IMAGE, AUDIO, ARCHIVE, APK, OTHER }

/** Small file helpers shared by the queue and the downloaded-files screen. */
object Fs {
    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts")
    private val AUD = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "epub", "txt", "csv")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun category(name: String): Cat {
        val e = ext(name)
        return when {
            e == "apk" -> Cat.APK
            e in IMG -> Cat.IMAGE
            e in VID -> Cat.VIDEO
            e in AUD -> Cat.AUDIO
            e in DOC -> Cat.DOCUMENT
            e in ARC -> Cat.ARCHIVE
            else -> Cat.OTHER
        }
    }

    fun mime(name: String): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(name)) ?: "*/*"

    fun human(b: Long): String {
        if (b < 0) return "?"
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        do { v /= 1024; i++ } while (v >= 1024 && i < units.size - 1)
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun date(t: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", Locale.US).format(Date(t))
    fun dateShort(t: Long): String = SimpleDateFormat("d MMM yy", Locale.US).format(Date(t))

    /** Picks a filename from a URL, falling back to a timestamp when the URL has no useful name. */
    fun nameFromUrl(url: String): String {
        return try {
            val u = Uri.parse(url)
            val last = u.lastPathSegment?.substringBefore('?')?.trim()
            if (!last.isNullOrBlank() && last.contains('.')) last else "download_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    fun uniqueTarget(dir: File, name: String): File {
        if (!dir.exists()) dir.mkdirs()
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val suf = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$suf"); i++ }
        return f
    }

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "com.mux.android.dm.provider", f)

    fun openExternal(ctx: Context, f: File) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri(ctx, f), mime(f.name))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Util.toast(ctx, "NO APP CAN OPEN THIS FILE")
        }
    }

    fun share(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = mime(f.name)
        i.putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(i, "SHARE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAppSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
}
