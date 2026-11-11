package com.mux.android.dm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

enum class Status { QUEUED, CONNECTING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

/** One contiguous byte range of a file. [done] bytes of it (from [start]) are already on disk. */
class Seg(val index: Int, val start: Long, val end: Long, @Volatile var done: Long = 0L) {
    val size: Long get() = end - start + 1
}

class DownloadItem(
    val id: String,
    val url: String,
    var fileName: String,
    val path: String,
    val addedTime: Long
) {
    var totalBytes by mutableLongStateOf(-1L)
    var resumable by mutableStateOf(true)
    var status by mutableStateOf(Status.QUEUED)
    var downloaded by mutableLongStateOf(0L)
    var speedBps by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    @Volatile var segments: List<Seg> = emptyList()

    val file: File get() = File(path)
    val category: Cat get() = Fs.category(fileName)
    val progress: Float get() = if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}
