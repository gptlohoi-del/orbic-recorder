"""Regression checks for Android 9 (API 28) compatibility.

These source checks do NOT replace building and installing the APK on a device.
The build workflow additionally uses aapt to inspect minSdk of the real APK.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
archive = (root / "app/src/main/java/com/example/orbicrecorder/RecordingArchive.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/com/example/orbicrecorder/MainActivity.java").read_text(encoding="utf-8")
js = (root / "app/src/main/assets/ui/app.js").read_text(encoding="utf-8")
css = (root / "app/src/main/assets/ui/app.css").read_text(encoding="utf-8")
manifest = root / "app/src/main/AndroidManifest.xml"

assert re.search(r"\bminSdk\s+28\b", gradle), 'Android 9 requires minSdk <= 28'
assert "Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q" in archive
assert 'exportToAppMusic(context, source, raw)' in archive
assert 'getExternalFilesDir(Environment.DIRECTORY_MUSIC)' in archive
assert 'out.getFD().sync()' in archive and 'pending.renameTo(destination)' in archive
assert 'if (!source.delete())' in archive, 'Never delete staging file before export completes'
assert 'Build.VERSION.SDK_INT <= Build.VERSION_CODES.P' in activity
assert 'ACTION_OPEN_DOCUMENT_TREE' in activity, 'Android 9 needs a durable user-chosen export folder'
assert 'height:100vh;height:100dvh' in css
assert 'max-height:96vh;max-height:96dvh' in css
assert not any(token in js for token in ('?.', '??', 'replaceChildren', 'Object.fromEntries'))
tree = ET.parse(manifest)
app = tree.getroot().find("application")
icon = app.attrib.get("{http://schemas.android.com/apk/res/android}icon")
assert icon == "@mipmap/ic_launcher", f"APK must use local launcher icon for aapt dump badging; found {icon!r}"
for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    png = root / "app/src/main/res" / f"mipmap-{density}" / "ic_launcher.png"
    assert png.is_file() and png.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n", f"Missing PNG launcher icon: {png}"
assert 'com.example.orbicrecorder' in gradle
print('PASS: Android 9 API 28, safe WAV fallback, old WebView syntax, local launcher icon')
