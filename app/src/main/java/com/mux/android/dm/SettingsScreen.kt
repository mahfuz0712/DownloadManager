package com.mux.android.dm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SPEEDS = listOf(0, 100, 250, 500, 1000, 2000, 5000) // KB/s, 0 = unlimited

@Composable
fun DmSettingsScreen() {
    val ctx = LocalContext.current
    var folderDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "APPEARANCE") {
            SettingRow("HUD colour", null, if (Prefs.amber) "AMBER" else "CYAN") { Prefs.amber = !Prefs.amber; Prefs.commit() }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "CAPTURE") {
            HudText("Links come in via the system Share sheet (share a page or a long-pressed link from any browser into this app), or the + ADD button.", color = Hud.colors.dim, size = 10.sp)
            Spacer(Modifier.height(6.dp))
            SettingRow("Start immediately", "Off: new links sit as QUEUED until you tap RESUME", if (Prefs.autoStart) "ON" else "OFF") { Prefs.autoStart = !Prefs.autoStart; Prefs.commit() }
            SettingRow("Wi-Fi only", "Pause everything on mobile data", if (Prefs.wifiOnly) "ON" else "OFF") { Prefs.wifiOnly = !Prefs.wifiOnly; Prefs.commit() }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "ENGINE") {
            SettingRow("Segments per file", "More segments can be faster on fast links, but not every server allows it", "${Prefs.segments}") {
                Prefs.segments = if (Prefs.segments >= 8) 1 else Prefs.segments + 1; Prefs.commit()
            }
            SettingRow("Parallel downloads", "How many files download at the same time", "${Prefs.concurrent}") {
                Prefs.concurrent = if (Prefs.concurrent >= 5) 1 else Prefs.concurrent + 1; Prefs.commit()
            }
            SettingRow("Speed limit", null, if (Prefs.speedLimitKBps == 0) "UNLIMITED" else "${Prefs.speedLimitKBps} KB/s") {
                val i = SPEEDS.indexOf(Prefs.speedLimitKBps).let { if (it < 0) 0 else it }
                Prefs.speedLimitKBps = SPEEDS[(i + 1) % SPEEDS.size]; Prefs.commit()
            }
        }
        Spacer(Modifier.height(6.dp))
        HudPanel(Modifier.fillMaxWidth(), "STORAGE") {
            HudText("Saving to", color = Hud.colors.dim, size = 10.sp)
            HudText("/${Prefs.folder}", color = Hud.colors.text, size = 13.sp)
            Spacer(Modifier.height(6.dp))
            HudButton("CHANGE FOLDER NAME", Modifier.fillMaxWidth().height(42.dp), KeyKind.OP, fontSize = 11.sp) { folderDialog = true }
        }
    }

    if (folderDialog) {
        var v by remember { mutableStateOf(Prefs.folder) }
        HudDialog("DOWNLOAD FOLDER", { folderDialog = false }) {
            HudText("Relative to internal storage, e.g. Download/MUX", color = Hud.colors.dim, size = 10.sp)
            Spacer(Modifier.height(6.dp))
            HudField(v, "Download/MUX") { v = it }
            Spacer(Modifier.height(10.dp))
            HudButton("SAVE", Modifier.fillMaxWidth().height(44.dp), KeyKind.GOOD, fontSize = 12.sp) {
                Prefs.folder = v.trim('/').ifBlank { "Download/MUX" }; Prefs.commit(); folderDialog = false
            }
        }
    }
}
