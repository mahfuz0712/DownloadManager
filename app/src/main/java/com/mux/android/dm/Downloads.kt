package com.mux.android.dm

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persisted registry of all downloads (queued, running, paused, completed...). Survives app restarts. */
object Downloads {
    val items = mutableStateListOf<DownloadItem>()
    private lateinit var regFile: File
    private val lock = Any()

    fun init(ctx: Context) {
        if (::regFile.isInitialized) return
        regFile = File(ctx.filesDir, "downloads.json")
        load()
    }

    fun byId(id: String): DownloadItem? = items.firstOrNull { it.id == id }

    fun add(item: DownloadItem) {
        items.add(0, item)
        save()
    }

    fun remove(item: DownloadItem, deleteFile: Boolean) {
        items.remove(item)
        if (deleteFile) try { item.file.delete() } catch (e: Exception) { }
        save()
    }

    private fun load() {
        try {
            if (!regFile.exists()) return
            val arr = JSONArray(regFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val item = DownloadItem(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    fileName = o.getString("fileName"),
                    path = o.getString("path"),
                    addedTime = o.optLong("addedTime", System.currentTimeMillis())
                )
                item.totalBytes = o.optLong("totalBytes", -1)
                item.resumable = o.optBoolean("resumable", true)
                item.downloaded = o.optLong("downloaded", 0)
                item.error = o.optString("error", "").ifBlank { null }
                val st = try { Status.valueOf(o.optString("status", "QUEUED")) } catch (e: Exception) { Status.QUEUED }
                // A download that was RUNNING/CONNECTING when the app last closed was interrupted: resume as paused.
                item.status = if (st == Status.RUNNING || st == Status.CONNECTING) Status.PAUSED else st
                val segs = o.optJSONArray("segments")
                if (segs != null) {
                    val list = ArrayList<Seg>()
                    for (j in 0 until segs.length()) {
                        val s = segs.getJSONObject(j)
                        list.add(Seg(s.getInt("i"), s.getLong("start"), s.getLong("end"), s.optLong("done", 0)))
                    }
                    item.segments = list
                }
                items.add(item)
            }
        } catch (e: Exception) {
        }
    }

    fun save() {
        synchronized(lock) {
            try {
                val arr = JSONArray()
                for (item in items.toList()) {
                    val o = JSONObject()
                    o.put("id", item.id)
                    o.put("url", item.url)
                    o.put("fileName", item.fileName)
                    o.put("path", item.path)
                    o.put("addedTime", item.addedTime)
                    o.put("totalBytes", item.totalBytes)
                    o.put("resumable", item.resumable)
                    o.put("downloaded", item.downloaded)
                    o.put("status", item.status.name)
                    o.put("error", item.error ?: "")
                    val segs = JSONArray()
                    for (s in item.segments) {
                        val so = JSONObject()
                        so.put("i", s.index); so.put("start", s.start); so.put("end", s.end); so.put("done", s.done)
                        segs.put(so)
                    }
                    o.put("segments", segs)
                    arr.put(o)
                }
                val tmp = File(regFile.parentFile, regFile.name + ".tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(regFile)
            } catch (e: Exception) {
            }
        }
    }
}
