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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CATS = listOf("ALL") + Cat.entries.map { it.name }

@Composable
fun FilesScreen() {
    val c = Hud.colors
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf("ALL") }
    var removing by remember { mutableStateOf<DownloadItem?>(null) }
    val done = Downloads.items.filter { it.status == Status.COMPLETED && (filter == "ALL" || it.category.name == filter) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (f in CATS) {
                HudButton(f, Modifier.width(78.dp).height(34.dp), KeyKind.OP, selected = filter == f, fontSize = 9.sp) { filter = f }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (done.isEmpty()) {
            EmptyNote("No finished downloads yet.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(done, key = { it.id }) { item -> FileRow(item) { removing = item } }
            }
        }
    }

    removing?.let { item ->
        ConfirmDialog("DELETE FILE", "Delete \"${item.fileName}\" from storage? This cannot be undone.",
            onConfirm = { Downloads.remove(item, true); removing = null },
            onCancel = { removing = null })
    }
}

@Composable
private fun FileRow(item: DownloadItem, onDelete: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val shape = HudShape(8.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(c.panel.copy(alpha = 0.6f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.35f), shape)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(36.dp).height(36.dp)
                    .background(c.primary.copy(alpha = 0.1f), HudShape(6.dp))
                    .border(1.dp, c.primary.copy(alpha = 0.5f), HudShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { HudText(item.category.name.take(3), color = c.primary, size = 8.sp, bold = true) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                HudText(item.fileName, color = c.text, size = 12.sp, maxLines = 1)
                HudText("${Fs.human(item.totalBytes)} · ${Fs.dateShort(item.addedTime)}", color = c.dim, size = 9.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            HudButton("OPEN", Modifier.weight(1f).height(36.dp), KeyKind.GOOD, fontSize = 10.sp) {
                if (item.file.exists()) Fs.openExternal(ctx, item.file) else Util.toast(ctx, "FILE NOT FOUND")
            }
            Spacer(Modifier.width(6.dp))
            HudButton("SHARE", Modifier.weight(1f).height(36.dp), KeyKind.OP, fontSize = 10.sp) {
                if (item.file.exists()) Fs.share(ctx, item.file) else Util.toast(ctx, "FILE NOT FOUND")
            }
            Spacer(Modifier.width(6.dp))
            HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.DANGER, fontSize = 11.sp) { onDelete() }
        }
    }
}
