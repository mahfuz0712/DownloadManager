package com.mux.android.dm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Shared, app-wide byte-rate limiter. 0 (Prefs.speedLimitKBps) means unlimited. */
private class SpeedLimiter {
    private var windowStart = System.nanoTime()
    private var windowBytes = 0L

    @Synchronized
    fun throttle(n: Int) {
        val kbps = Prefs.speedLimitKBps
        if (kbps <= 0) return
        val limitBps = kbps.toLong() * 1024
        val now = System.nanoTime()
        var elapsedMs = (now - windowStart) / 1_000_000
        if (elapsedMs > 1000) { windowStart = now; windowBytes = 0; elapsedMs = 0 }
        windowBytes += n
        val allowed = limitBps * (elapsedMs + 1) / 1000
        if (windowBytes > allowed) {
            val neededMs = (windowBytes - allowed) * 1000 / limitBps
            if (neededMs in 1..500) Thread.sleep(neededMs)
        }
    }
}

/** Multi-threaded (segmented) HTTP downloader: queue, pause/resume, cancel, speed limit. */
object Engine {
    private class CancelFlag { @Volatile var paused = false; @Volatile var canceled = false }

    private val cancelFlags = ConcurrentHashMap<String, CancelFlag>()
    private val runningIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val speedTrack = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val limiter = SpeedLimiter()
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }

    fun newItem(url: String, suggestedName: String? = null): DownloadItem {
        val name = (suggestedName?.takeIf { it.isNotBlank() } ?: Fs.nameFromUrl(url))
        val target = Fs.uniqueTarget(Prefs.downloadDir(), name)
        return DownloadItem(UUID.randomUUID().toString(), url, target.name, target.absolutePath, System.currentTimeMillis())
    }

    fun enqueue(ctx: Context, item: DownloadItem) {
        Downloads.add(item)
        pump(ctx)
    }

    fun resumeItem(ctx: Context, item: DownloadItem) {
        if (item.status == Status.COMPLETED || item.status == Status.QUEUED || item.status == Status.RUNNING) return
        item.status = Status.QUEUED
        item.error = null
        Downloads.save()
        pump(ctx)
    }

    fun pause(item: DownloadItem) {
        cancelFlags[item.id]?.paused = true
        if (item.status == Status.QUEUED) item.status = Status.PAUSED
    }

    fun cancel(ctx: Context, item: DownloadItem, deleteFile: Boolean) {
        cancelFlags[item.id]?.canceled = true
        if (item.id !in runningIds) {
            item.status = Status.CANCELED
            Downloads.remove(item, deleteFile)
        }
        pump(ctx)
    }

    fun pauseAll() { Downloads.items.forEach { if (it.status == Status.RUNNING || it.status == Status.CONNECTING || it.status == Status.QUEUED) pause(it) } }

    fun pump(ctx: Context) {
        synchronized(runningIds) {
            while (runningIds.size < Prefs.concurrent.coerceAtLeast(1)) {
                if (Prefs.wifiOnly && !Util.isWifi(ctx)) break
                val next = Downloads.items.firstOrNull { it.status == Status.QUEUED && it.id !in runningIds } ?: break
                runningIds.add(next.id)
                ensureService(ctx)
                val flag = CancelFlag()
                cancelFlags[next.id] = flag
                next.status = Status.CONNECTING
                pool.execute { runItem(ctx, next, flag) }
            }
        }
    }

    private fun buildSegments(total: Long, count: Int): List<Seg> {
        if (total <= 0 || count <= 1) return listOf(Seg(0, 0, (total - 1).coerceAtLeast(0)))
        val per = total / count
        val list = ArrayList<Seg>()
        var start = 0L
        for (i in 0 until count) {
            val end = if (i == count - 1) total - 1 else start + per - 1
            list.add(Seg(i, start, end))
            start = end + 1
        }
        return list
    }

    private fun probeIfNeeded(item: DownloadItem) {
        if (item.totalBytes > 0 && item.segments.isNotEmpty()) return
        var len = -1L
        var acceptsRanges = false
        try {
            val c = URL(item.url).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", Util.UA)
            c.instanceFollowRedirects = true
            if (c.responseCode in 200..299) {
                len = c.contentLengthLong
                acceptsRanges = c.getHeaderField("Accept-Ranges")?.contains("bytes", true) == true
            }
            c.disconnect()
        } catch (e: Exception) {
        }
        if (len <= 0) {
            try {
                val c = URL(item.url).openConnection() as HttpURLConnection
                c.setRequestProperty("Range", "bytes=0-0")
                c.setRequestProperty("User-Agent", Util.UA)
                c.connectTimeout = 15000
                if (c.responseCode == 206) {
                    val cr = c.getHeaderField("Content-Range")
                    len = cr?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1
                    acceptsRanges = true
                } else if (c.responseCode in 200..299) {
                    len = c.contentLengthLong
                }
                c.disconnect()
            } catch (e: Exception) {
            }
        }
        item.totalBytes = len
        item.resumable = len > 0 && acceptsRanges
        val wanted = Prefs.segments.coerceIn(1, 8)
        val segCount = if (item.resumable && len >= wanted * 256_000L) wanted else if (item.resumable) 2.coerceAtMost(wanted) else 1
        item.segments = buildSegments(if (len > 0) len else 0, if (len > 0) segCount else 1)
    }

    private fun onProgress(item: DownloadItem) {
        val total = item.segments.sumOf { it.done }
        item.downloaded = total
        val now = System.currentTimeMillis()
        val prev = speedTrack[item.id]
        if (prev == null) { speedTrack[item.id] = total to now; return }
        val dt = now - prev.second
        if (dt >= 600) {
            item.speedBps = ((total - prev.first) * 1000 / dt).coerceAtLeast(0)
            speedTrack[item.id] = total to now
            Downloads.save()
        }
    }

    private fun downloadSegment(item: DownloadItem, seg: Seg, flag: CancelFlag) {
        if (!item.resumable) seg.done = 0
        val conn = URL(item.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", Util.UA)
        conn.instanceFollowRedirects = true
        if (item.resumable) conn.setRequestProperty("Range", "bytes=${seg.start + seg.done}-${seg.end}")
        conn.connect()
        val code = conn.responseCode
        if (item.resumable && code != 206 && code != 200) throw IOException("HTTP $code")
        if (!item.resumable && code !in 200..299) throw IOException("HTTP $code")
        conn.inputStream.use { ins ->
            RandomAccessFile(item.file, "rw").use { raf ->
                raf.seek(seg.start + seg.done)
                val buf = ByteArray(32 * 1024)
                while (true) {
                    if (flag.canceled || flag.paused) return
                    val n = ins.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    seg.done += n
                    limiter.throttle(n)
                    onProgress(item)
                    if (item.resumable && seg.done >= seg.size) break
                }
            }
        }
        conn.disconnect()
    }

    private fun runItem(ctx: Context, item: DownloadItem, flag: CancelFlag) {
        try {
            probeIfNeeded(item)
            if (item.totalBytes > 0) RandomAccessFile(item.file, "rw").use { it.setLength(item.totalBytes) }
            else item.file.parentFile?.mkdirs()
            if (flag.canceled || flag.paused) throw InterruptedException()

            item.status = Status.RUNNING
            Downloads.save()
            Notify.update(ctx, item)

            val pending = item.segments.filter { it.done < it.size || !item.resumable }
            if (pending.isNotEmpty()) {
                val latch = CountDownLatch(pending.size)
                val failedMsg = AtomicReference<String?>(null)
                for (seg in pending) {
                    pool.execute {
                        try { downloadSegment(item, seg, flag) }
                        catch (e: Exception) { failedMsg.compareAndSet(null, e.message ?: "Network error") }
                        finally { latch.countDown() }
                    }
                }
                latch.await()
                if (failedMsg.get() != null && !flag.canceled && !flag.paused) throw IOException(failedMsg.get())
            }

            when {
                flag.canceled -> { item.status = Status.CANCELED; Downloads.remove(item, true); Notify.cancel(ctx, item) }
                flag.paused -> { item.status = Status.PAUSED; Downloads.save(); Notify.cancel(ctx, item) }
                else -> {
                    val allDone = item.segments.all { it.done >= it.size }
                    if (allDone) {
                        item.status = Status.COMPLETED
                        if (item.totalBytes <= 0) item.totalBytes = item.segments.sumOf { it.done }
                        item.downloaded = item.totalBytes
                        Downloads.save()
                        Notify.done(ctx, item)
                    } else {
                        item.status = Status.FAILED; item.error = "Incomplete transfer"
                        Downloads.save(); Notify.failed(ctx, item)
                    }
                }
            }
        } catch (e: InterruptedException) {
            if (flag.canceled) { item.status = Status.CANCELED; Downloads.remove(item, true); Notify.cancel(ctx, item) }
            else { item.status = Status.PAUSED; Downloads.save(); Notify.cancel(ctx, item) }
        } catch (e: Exception) {
            if (flag.canceled) { item.status = Status.CANCELED; Downloads.remove(item, true); Notify.cancel(ctx, item) }
            else if (flag.paused) { item.status = Status.PAUSED; Downloads.save(); Notify.cancel(ctx, item) }
            else { item.status = Status.FAILED; item.error = e.message ?: "Error"; Downloads.save(); Notify.failed(ctx, item) }
        } finally {
            runningIds.remove(item.id)
            cancelFlags.remove(item.id)
            speedTrack.remove(item.id)
            item.speedBps = 0
            pump(ctx)
            if (runningIds.isEmpty()) maybeStopService(ctx)
        }
    }

    private fun ensureService(ctx: Context) {
        try { ContextCompat.startForegroundService(ctx.applicationContext, Intent(ctx, DownloadService::class.java)) } catch (e: Exception) { }
    }

    private fun maybeStopService(ctx: Context) {
        try { ctx.applicationContext.stopService(Intent(ctx, DownloadService::class.java)) } catch (e: Exception) { }
    }
}
