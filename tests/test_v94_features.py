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
assert 'web.setRotation((float) uiRotationDegrees);' in activity
assert 'supportsReliableRotation()' in activity
assert 'uiRotated180' in activity
assert 'case "rotate180": rotate180();' in activity
for marker in ('music3','chooseSong3','song3','rotate180','tabSafe','tabEconomy','saveTrack','saveThumb'):
    assert f'id="{marker}"' in html, marker
assert html.index('id="music1"') < html.index('id="music2"') < html.index('id="music3"')
assert 'REQUEST_MUSIC_3' in activity and 'case "music3": playMusic(3)' in activity
assert 'song3Name' in activity and 'activeSlot===3' in js
assert 'pointerdown' in js and 'pointermove' in js and 'pointerup' in js
assert 'distance>=limit()*.92' in js
assert "window.Orbic.finishRecording('save')" in js and "$('stop').onclick" in js
assert 'sendRecorder("STOP")' in activity and 'case "STOP_DISCARD": break;' in service
assert 'Future<?> done = RecordingArchive.barrier();' in service
assert 'sessionFile(context, session, ".discard")' in archive
assert '.exports' in archive and 'rememberExport(context, session, destination)' in archive
assert 'if (!session.isEmpty() && isDiscardMarked(context, session))' in archive
assert 'removeFromMediaStore(context, prefix)' in archive
assert 'removeFromChosenFolder(context, session, prefix)' in archive
assert 'restore.remove("priorUri")' in archive
assert 'public static final int NATIVE_API = 12;' in ota
assert 'public static final String BUILTIN_VERSION = "8.0.16";' in ota
assert 'versionCode (210000 + githubRunNumber)' in (root/'app/build.gradle').read_text()
print('PASS: backward compatibility for v9.4.2 reliable rotation, 3 tracks, swipe confirm, retained crash-safe archive recovery, OTA boundary')
