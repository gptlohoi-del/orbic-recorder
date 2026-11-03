"""No-SDK source regression checks; an actual APK build runs in GitHub Actions."""
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
ui = root / 'app/src/main/assets/ui'
html = (ui / 'index.html').read_text(encoding='utf-8')
js = (ui / 'app.js').read_text(encoding='utf-8')
css = (ui / 'app.css').read_text(encoding='utf-8')
activity = (root / 'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text(encoding='utf-8')
service = (root / 'app/src/main/java/com/example/orbicrecorder/RecorderService.java').read_text(encoding='utf-8')
music = (root / 'app/src/main/java/com/example/orbicrecorder/MusicPlayer.java').read_text(encoding='utf-8')
ota = (root / 'app/src/main/java/com/example/orbicrecorder/OtaManager.java').read_text(encoding='utf-8')
script = (root / 'scripts/publish_ota.py').read_text(encoding='utf-8')
manifest = root / 'app/src/main/AndroidManifest.xml'

ids = set(re.findall(r'\bid="([\w-]+)"', html))
refs = set(re.findall(r"\$\('([\w-]+)'\)", js))
assert refs <= ids, f'HTML IDs missing: {refs-ids}'
assert {'music1','music2','musicPause','musicStop','settingsToggle','start','pause','stop',
        'thresholdMinus','thresholdPlus','thresholdLine','waveBars','noticeMetrics'} <= ids
assert 'id="save"' not in html
assert html.index('id="music1"') < html.index('id="music2"')
assert 'music-stack' in html and 'notice panel' in html
assert html.index('id="musicStop"') < html.index('id="settingsToggle"') < html.index('class="record panel"')
assert 'DỪNG &amp; LƯU' in html and '● START' in html
assert 'grid-template-columns:minmax(0,1fr) 40px 90px' in css
assert 'height:100dvh' in css and 'overflow:hidden' in css
assert 'threshold-line' in css and '--wave-height' in css
assert "default:2000" in js and "key:'pauseStopFade'" in js
assert "key:'waveHeight'" in js and "changeThreshold" in js
assert "'music1').disabled" in js
assert "confirmAction('Dừng" in js

ET.parse(manifest)
man = manifest.read_text(encoding='utf-8')
assert 'screenOrientation="fullSensor"' in man
assert 'configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"' in man
assert 'NATIVE_API = 4' in ota and 'BUILTIN_VERSION = "4.0.0"' in ota
assert 'NATIVE_API = 4' in script
assert 'case "start": startRecording(); break;' in activity
assert 'music.isBlockingMic() || pendingMusic || pendingResume' in activity
assert 'case "musicPause": music.requestPause' in activity
assert 'case "musicResume": resumeMusicSafely()' in activity
assert 'case "musicStop":' in activity and 'music.requestStop' in activity
assert 'onMusicPaused()' in activity and 'waitForResumeMicRelease' in activity
assert 'data.put("pauseStopFade", cfg.getInt("pauseStopFade", 2000))' in activity
assert 'data.put("waveHeight", cfg.getInt("waveHeight", 92))' in activity
assert 'MUSIC_PAUSE' in service and 'MUSIC_RESUME' in service
assert 'releaseMicrophone();' in service and 'if (elapsed % 5 == 0) threshold = cfg.getInt' in service
assert 'cfg.getInt("warnSeconds", 9999)' in activity
assert 'cfg.getInt("warnSeconds", 9999)' in service
assert 'MediaPlayer' in music and 'Math.min(Math.max(0, requestedFadeMs), remaining)' in music
assert 'void requestPause(int requestedFadeMs)' in music
assert 'void requestStop(int requestedFadeMs)' in music
assert 'listener.paused()' in music and 'listener.ended()' in music
# Returning from PAUSE uses the same configurable time as PAUSE/STOP fade-out.
assert 'music.resume(config().getInt("pauseStopFade", 2000))' in activity
assert 'void resume(int requestedFadeMs)' in music
assert 'SystemClock.uptimeMillis()' in music
assert 'progress * progress * (3f - 2f * progress)' in music
assert '450f' not in music
assert 'Giảm âm PAUSE/STOP và tăng âm TIẾP' in js
assert 'takePersistableUriPermission' in activity

subprocess.run(['node', '--check', str(ui / 'app.js')], check=True)
subprocess.run(['python3', '-m', 'py_compile', str(root / 'scripts/publish_ota.py')], check=True)
print('PASS: v8 source, fade pause/stop, OneView, threshold, reverse portrait, OTA API, JS syntax')
