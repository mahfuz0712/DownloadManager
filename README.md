# Download Manager

Sci-fi HUD (Jarvis style) download manager for Android 8.0+. Same design language as Dialer, Scanner, Keyboard, Launcher, Messages, Weather, Terminal, Code and File Manager.

- Package: `com.mux.android.dm` - Version: `1.0.0`

## How a download gets in
Android does not let a normal app silently grab every link from every browser the way IDM/FDM do on a PC (that needs root or a traffic-inspecting VPN, which we are not doing — see Limits). Two honest ways in instead:
- **Share**: in any browser, long-press a link or use the page's Share button, pick **Download Manager**. The link is read and an "Add download" box opens with the file name pre-filled.
- **+ ADD**: paste a link in yourself from the QUEUE tab.

## Features
- **QUEUE**: live status per download (connecting, downloading with speed + progress bar, paused, queued, failed), pause/resume, remove.
- **Engine**: multi-threaded segmented downloads (splits a file into up to 8 pieces and pulls them in parallel when the server supports `Range` requests), automatic fallback to a single stream when it doesn't. Resume continues from where each piece stopped, even after the app was closed.
- **Queueing**: a settable number of downloads run at once; the rest wait as QUEUED.
- **Speed limiter**: cap the app's total transfer rate (shared across all active downloads) so other apps aren't starved.
- **Wi-Fi only**: hold everything on mobile data.
- **FILES**: finished downloads grouped by category (video, document, image, audio, archive, APK, other) with open / share / delete.
- **SET**: HUD colour, start-immediately toggle, Wi-Fi only, segments per file, parallel downloads, speed limit, download folder name.
- **SYS**: app info, update check, author.
- Downloads keep going in the background via a foreground service with a progress notification; closing the app does not stop them.

## Limits (Android, honestly)
- No automatic, invisible interception of every link in every browser — that is not something a normal Android app is allowed to do. Share or + ADD is the way in.
- A server that does not send `Content-Length` or `Accept-Ranges: bytes` cannot be split into segments or resumed after a pause; it downloads single-stream and restarts from 0 if interrupted.
- Not tested on a device yet: if a build or runtime error appears, send the log.
- Needs "All files access" to save into a shared Download folder.

## Build
This project includes the Gradle wrapper, so you don't need Gradle installed separately:
```
./gradlew assembleDebug
```
(Windows: `gradlew.bat assembleDebug`.) Needs JDK 17 and the Android SDK (`local.properties` with `sdk.dir=...`, or open the project in Android Studio once so it writes that file for you).
`app/debug.keystore` is shared with the other apps. Updates are read from GitHub releases of `mahfuz0712/DownloadManager` (edit `AppInfo.kt` if you rename the repo).
