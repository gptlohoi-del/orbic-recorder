"""Fast, SDK-independent checks. Full Android build runs in GitHub Actions."""
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
ui = root / 'app/src/main/assets/ui'
html = (ui / 'index.html').read_text(encoding='utf-8')
js = (ui / 'app.js').read_text(encoding='utf-8')
activity = (root / 'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text(encoding='utf-8')
service = (root / 'app/src/main/java/com/example/orbicrecorder/RecorderService.java').read_text(encoding='utf-8')
music = (root / 'app/src/main/java/com/example/orbicrecorder/MusicPlayer.java').read_text(encoding='utf-8')
ota = (root / 'app/src/main/java/com/example/orbicrecorder/OtaManager.java').read_text(encoding='utf-8')
ids = set(re.findall(r'\bid="([\w-]+)"', html))
refs = set(re.findall(r"\$\('([\w-]+)'\)", js))
assert refs <= ids, f'Missing elements: {refs-ids}'
ET.parse(root / 'app/src/main/AndroidManifest.xml')
assert 'NATIVE_API = 2' in ota
assert 'BUILTIN_VERSION = "2.0.0"' in ota
assert 'MUSIC_PAUSE' in service and 'MUSIC_RESUME' in service
assert 'releaseMicrophone();' in service
assert 'saveRequested' in service and 'queueExport(this, current)' in service
assert 'takePersistableUriPermission' in activity
assert 'MediaPlayer' in music and 'fadeInMs' in music and 'fadeOutMs' in music
assert "confirmAction('Tạm dừng" in js and "confirmAction('Dừng" in js
assert "confirmAction('Lưu" in js
assert 'overflow:hidden' in (ui/'app.css').read_text(encoding='utf-8')
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('OK: HTML IDs, Android manifest, VAD/music/confirm/OTA contracts, JavaScript syntax')
