package com.mux.android.dm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown after a link arrives via Share, or when the user taps "+ ADD" to paste one in. */
@Composable
fun AddDownloadDialog(initialUrl: String, onDone: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(initialUrl) }
    var name by remember(initialUrl) { mutableStateOf(if (initialUrl.isNotBlank()) Fs.nameFromUrl(initialUrl) else "") }

    HudDialog("NEW DOWNLOAD", onDone) {
        HudText("LINK", color = c.dim, size = 10.sp)
        Spacer(Modifier.height(4.dp))
        HudField(url, "https://example.com/file.zip") { url = it; if (name.isBlank() || name == Fs.nameFromUrl(url.dropLast(1))) name = Fs.nameFromUrl(it) }
        Spacer(Modifier.height(10.dp))
        HudText("SAVE AS", color = c.dim, size = 10.sp)
        Spacer(Modifier.height(4.dp))
        HudField(name, "filename") { name = it }
        if (url.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            HudText("Category: ${Fs.category(name.ifBlank { Fs.nameFromUrl(url) })}  ·  Saves to /${Prefs.folder}", color = c.dim, size = 10.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            HudButton("CANCEL", Modifier.weight(1f).height(46.dp), KeyKind.FN, fontSize = 12.sp) { onDone() }
            Spacer(Modifier.width(8.dp))
            HudButton(
                "ADD TO QUEUE", Modifier.weight(1f).height(46.dp), KeyKind.GOOD, fontSize = 12.sp,
                enabled = url.trim().startsWith("http")
            ) {
                val finalUrl = url.trim()
                val finalName = name.trim().ifBlank { Fs.nameFromUrl(finalUrl) }
                val item = Engine.newItem(finalUrl, finalName)
                if (Prefs.autoStart) Engine.enqueue(ctx, item) else { Downloads.add(item) }
                Util.toast(ctx, "ADDED: ${item.fileName}")
                onDone()
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, danger: Boolean = true, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = Hud.colors
    HudDialog(title, onCancel) {
        HudText(message, color = c.text, size = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            HudButton("CANCEL", Modifier.weight(1f).height(44.dp), KeyKind.FN, fontSize = 12.sp) { onCancel() }
            Spacer(Modifier.width(8.dp))
            HudButton("CONFIRM", Modifier.weight(1f).height(44.dp), if (danger) KeyKind.DANGER else KeyKind.GOOD, fontSize = 12.sp) { onConfirm() }
        }
    }
}
