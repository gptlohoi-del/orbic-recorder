"""v9.5.7 native/UI contract checks."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/example/orbicrecorder'; ui=root/'app/src/main/assets/ui'
html=(ui/'index.html').read_text();js=(ui/'app.js').read_text();css=(ui/'app.css').read_text()
activity=(java/'MainActivity.java').read_text(); service=(java/'RecorderService.java').read_text()
gradle=(root/'app/build.gradle').read_text();ota=(java/'OtaManager.java').read_text()
for name in ('start','stop','recordPause','pauseTrack','pauseModal','autoStart','tabSafe','tabEconomy','safeActions','economyActions','saveTrack','waveBars','waveLineSvg','openRecordings','playNewest'):
    assert f'id="{name}"' in html,name
for missing in ('id="backupRaw"','id="discard"','id="discardModal"','id="pause"','id="videoMode"'):
    assert missing not in html,missing
assert 'GHI ÂM LIÊN TỤC' in html
assert 'final boolean saveRaw = false;' in service
assert 'current.voice.write(block);' in service
assert 'SilenceCompactor compactor' in service
assert 'RecordingArchive.queueExport(this, current)' in service
assert 'RecordingArchive.sync(current)' in service
assert 'AudioDynamics dynamics = new AudioDynamics();' in service
assert 'music.requestPause' in activity and 'music.requestStop' in activity
assert 'supportsRecordTabs' in activity
assert 'minSdk 28' in gradle and '220000 + githubRunNumber' in gradle
assert 'NATIVE_API = 13' in ota and 'BUILTIN_VERSION = "8.0.17"' in ota
assert '.record-tab.selected' in css
assert 'data.put("supportsRecordingPause", true)' in activity
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('PASS: v9.5.7 tabs, single WAV, Java silence compactor, limiter, Android 9')
