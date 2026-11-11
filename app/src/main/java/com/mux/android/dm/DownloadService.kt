package com.mux.android.dm

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Keeps the app process (and running downloads) alive while the app is in the background, with a single summary notification. */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    private val ticker = object : Runnable {
        override fun run() {
            try { startForeground(Notify.SUMMARY_ID, Notify.summary(this@DownloadService)) } catch (e: Exception) { }
            if (ticking) handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(applicationContext)
        Downloads.init(applicationContext)
        Util.appCtx = applicationContext
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(Notify.SUMMARY_ID, Notify.summary(this)) } catch (e: Exception) { }
        if (!ticking) { ticking = true; handler.post(ticker) }
        return START_STICKY
    }

    override fun onDestroy() {
        ticking = false
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
