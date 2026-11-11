package com.mux.android.dm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Holds a URL that just arrived (from a browser's Share, or a manually pasted link) until the Add dialog is shown. */
object Share {
    var pendingUrl by mutableStateOf<String?>(null)
}

private val URL_RX = Regex("""https?://[^\s"'<>]+""")

/** Pulls the first http(s) URL out of free text (a browser's Share often sends the page title + link together). */
fun extractUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return URL_RX.find(text)?.value
}
