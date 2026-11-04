"""v9.4 source guardrails (not a substitute for installing a real APK)."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/example/orbicrecorder'
web = root / 'app/src/main/assets/ui'
activity = (java / 'MainActivity.java').read_text()
service = (java / 'RecorderService.java').read_text()
archive = (java / 'RecordingArchive.java').read_text()
html = (web / 'index.html').read_text()
js = (web / 'app.js').read_text()
css = (web / 'app.css').read_text()
ota = (java / 'OtaManager.java').read_text()
assert 'web.setRotation(webRotated180 ? 180f : 0f);' in activity
assert 'supportsReliableRotation()' in activity
assert 'uiRotated180' in activity
assert 'case "rotate180": rotate180();' in activity
for marker in ('music3','chooseSong3','song3','rotate180','discard','discardModal','discardTrack','discardThumb'):
    assert f'id="{marker}"' in html, marker
assert html.index('id="music1"') < html.index('id="music2"') < html.index('id="music3"')
assert 'REQUEST_MUSIC_3' in activity and 'case "music3": playMusic(3)' in activity
assert 'song3Name' in activity and 'activeSlot===3' in js
assert 'pointerdown' in js and 'pointermove' in js and 'pointerup' in js
assert 'swipeDistance>=maxSwipe()*.92' in js
assert "send('discard')" in js and "$('discard').onclick" in js
assert 'STOP_DISCARD' in activity and 'STOP_DISCARD' in service
assert 'RecordingArchive.markDiscard(this, session);' in service
assert service.index('RecordingArchive.markDiscard(this, session);') < service.index('discardRequested = true;')
assert service.index('Future<?> done = RecordingArchive.barrier();') < service.index('RecordingArchive.discardSession(this, session)')
assert 'sessionFile(context, session, ".discard")' in archive
assert '.exports' in archive and 'rememberExport(context, session, destination)' in archive
assert 'if (!session.isEmpty() && isDiscardMarked(context, session))' in archive
assert 'removeFromMediaStore(context, prefix)' in archive
assert 'removeFromChosenFolder(context, session, prefix)' in archive
assert 'restore.remove("priorUri")' in archive
assert 'public static final int NATIVE_API = 8;' in ota
assert 'public static final String BUILTIN_VERSION = "8.0.8";' in ota
assert 'versionCode (160000 + githubRunNumber)' in (root/'app/build.gradle').read_text()
print('PASS: backward compatibility for v9.4.2 reliable rotation, 3 tracks, swipe confirm, crash-safe session discard, OTA boundary')
