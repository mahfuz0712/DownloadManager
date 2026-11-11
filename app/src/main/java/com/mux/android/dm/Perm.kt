package com.mux.android.dm

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Storage permission state ("All files access" on Android 11+). [tick] is bumped on resume so the UI re-checks. */
object Perm {
    var tick by mutableIntStateOf(0)

    fun has(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
