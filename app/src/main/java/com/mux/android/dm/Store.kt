package com.mux.android.dm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

object Prefs {
    private var sp: SharedPreferences? = null
    var amber by mutableStateOf(false)
    var segments by mutableIntStateOf(4)          // threads per download, 1-8
    var concurrent by mutableIntStateOf(2)        // downloads running at once, 1-5
    var speedLimitKBps by mutableIntStateOf(0)    // 0 = unlimited
    var wifiOnly by mutableStateOf(false)
    var autoStart by mutableStateOf(true)         // start a shared link immediately, else just queue it
    var folder by mutableStateOf("Download/MUX")  // under external storage root

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("mux_dm", Context.MODE_PRIVATE)
        sp = p
        amber = p.getBoolean("amber", false)
        segments = p.getInt("segments", 4)
        concurrent = p.getInt("concurrent", 2)
        speedLimitKBps = p.getInt("speed", 0)
        wifiOnly = p.getBoolean("wifiOnly", false)
        autoStart = p.getBoolean("autoStart", true)
        folder = p.getString("folder", "Download/MUX") ?: "Download/MUX"
    }

    fun commit() {
        sp?.edit()
            ?.putBoolean("amber", amber)?.putInt("segments", segments)?.putInt("concurrent", concurrent)
            ?.putInt("speed", speedLimitKBps)?.putBoolean("wifiOnly", wifiOnly)?.putBoolean("autoStart", autoStart)
            ?.putString("folder", folder)
            ?.apply()
    }

    fun downloadDir(): File {
        val root = android.os.Environment.getExternalStorageDirectory()
        val d = File(root, folder)
        if (!d.exists()) d.mkdirs()
        return d
    }
}
