package com.mux.android.dm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Nav {
    const val QUEUE = 0
    const val FILES = 1
    const val SET = 2
    const val SYS = 3
    var tab by mutableIntStateOf(QUEUE)
}

private val TABS = listOf("QUEUE", "FILES", "SET", "SYS")

@Composable
fun DMRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (Prefs.amber) AmberPalette else CyanPalette) {
        val c = Hud.colors
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        val tick = Perm.tick
        val granted = remember(tick) { Perm.has(ctx) }

        LaunchedEffect(Unit) {
            if (isOnline(ctx)) update = checkUpdate(ctx).second
        }

        update?.let { u ->
            UpdateDialog(u, appVersion(ctx), onUpdate = { Util.open(ctx, u.apkUrl); update = null }, onCancel = { update = null })
        }

        BackHandler(enabled = true) {
            when {
                Share.pendingUrl != null -> Share.pendingUrl = null
                Nav.tab != Nav.QUEUE -> Nav.tab = Nav.QUEUE
                else -> (ctx as? android.app.Activity)?.finish()
            }
        }

        HudBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    HudText("◈ DOWNLOAD MANAGER", color = c.primary, size = 15.sp, bold = true)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (!granted) {
                        GrantScreen()
                    } else {
                        when (Nav.tab) {
                            Nav.QUEUE -> QueueScreen()
                            Nav.FILES -> FilesScreen()
                            Nav.SET -> DmSettingsScreen()
                            else -> SystemScreen(false) { update = it }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    TABS.forEachIndexed { i, t ->
                        HudButton(t, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = i == Nav.tab, fontSize = 11.sp) { Nav.tab = i }
                    }
                }
            }
        }

        Share.pendingUrl?.let { url ->
            AddDownloadDialog(url) { Share.pendingUrl = null }
        }
    }
}

@Composable
private fun GrantScreen() {
    val ctx = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Perm.tick++ }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PermissionPanel("Download Manager needs storage access to save files where you can find them.") {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                legacy.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }
}
