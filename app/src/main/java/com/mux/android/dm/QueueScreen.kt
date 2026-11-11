package com.mux.android.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun statusColor(s: Status): Color {
    val c = Hud.colors
    return when (s) {
        Status.RUNNING, Status.CONNECTING -> c.primary
        Status.QUEUED -> c.dim
        Status.PAUSED -> c.accent
        Status.COMPLETED -> c.good
        Status.FAILED -> c.danger
        Status.CANCELED -> c.dim
    }
}

@Composable
fun QueueScreen() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val list = Downloads.items.filter { it.status != Status.COMPLETED }
    var removing by remember { mutableStateOf<DownloadItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HudText("${list.size} IN QUEUE", color = c.primary, size = 12.sp, bold = true, modifier = Modifier.weight(1f))
            HudButton("+ ADD", Modifier.width(76.dp).height(36.dp), KeyKind.GOOD, fontSize = 11.sp) { Share.pendingUrl = "" }
        }
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            EmptyNote("No downloads queued.\nShare a link from any browser, or tap + ADD.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { item -> QueueRow(item) { removing = item } }
            }
        }
    }

    removing?.let { item ->
        ConfirmDialog(
            "REMOVE DOWNLOAD",
            "Remove \"${item.fileName}\" from the queue? Downloaded data will be deleted too.",
            onConfirm = { Engine.cancel(ctx, item, true); removing = null },
            onCancel = { removing = null }
        )
    }
}

@Composable
private fun QueueRow(item: DownloadItem, onRemove: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val sc = statusColor(item.status)
    val shape = HudShape(8.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(c.panel.copy(alpha = 0.6f), shape)
            .border(1.dp, sc.copy(alpha = 0.5f), shape)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HudText(item.fileName, color = c.text, size = 12.sp, maxLines = 1)
                val sub = when (item.status) {
                    Status.CONNECTING -> "CONNECTING..."
                    Status.QUEUED -> "QUEUED"
                    Status.RUNNING -> "${Fs.human(item.downloaded)} / ${if (item.totalBytes > 0) Fs.human(item.totalBytes) else "?"} · ${Fs.human(item.speedBps)}/s"
                    Status.PAUSED -> "PAUSED · ${Fs.human(item.downloaded)}" + (if (item.totalBytes > 0) " / ${Fs.human(item.totalBytes)}" else "")
                    Status.FAILED -> "FAILED" + (item.error?.let { " · $it" } ?: "")
                    Status.CANCELED -> "CANCELED"
                    Status.COMPLETED -> "DONE"
                }
                HudText(sub, color = sc, size = 10.sp, maxLines = 1)
            }
            when (item.status) {
                Status.RUNNING, Status.CONNECTING -> HudButton("PAUSE", Modifier.width(66.dp).height(36.dp), KeyKind.ACCENT, fontSize = 10.sp) { Engine.pause(item) }
                Status.PAUSED, Status.FAILED, Status.CANCELED -> HudButton("RESUME", Modifier.width(70.dp).height(36.dp), KeyKind.GOOD, fontSize = 10.sp) { Engine.resumeItem(ctx, item) }
                Status.QUEUED -> HudButton("PAUSE", Modifier.width(66.dp).height(36.dp), KeyKind.FN, fontSize = 10.sp) { Engine.pause(item) }
                else -> {}
            }
            Spacer(Modifier.width(6.dp))
            HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.DANGER, fontSize = 11.sp) { onRemove() }
        }
        if (item.status == Status.RUNNING || item.status == Status.CONNECTING || item.status == Status.PAUSED) {
            Spacer(Modifier.height(6.dp))
            val frac = if (item.totalBytes > 0) item.progress else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.primary.copy(alpha = 0.15f))) {
                if (item.totalBytes > 0) {
                    Box(Modifier.fillMaxWidth(frac).height(6.dp).background(sc))
                } else {
                    Box(Modifier.width(40.dp).height(6.dp).background(sc))
                }
            }
        }
    }
}
