"""Core native/UI source contracts for Orbic Recorder v9.5.5."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/example/orbicrecorder'
ui=root/'app/src/main/assets/ui'
html=(ui/'index.html').read_text()
js=(ui/'app.js').read_text()
css=(ui/'app.css').read_text()
activity=(java/'MainActivity.java').read_text()
service=(java/'RecorderService.java').read_text()
gradle=(root/'app/build.gradle').read_text()
ota=(java/'OtaManager.java').read_text()
for id in ('music1','music2','music3','musicPause','musicStop','settingsToggle',
           'start','stop','discard','openRecordings','playNewest','saveTrack','discardTrack',
           'waveBars','waveLineSvg','recordingsList','recordingPause','recordingStop'):
 assert f'id="{id}"' in html,id
for removed in ('id="pause"','id="thresholdEnabled"','id="thresholdLine"',
                'id="videoMode"','id="thresholdMinus"','id="thresholdPlus"'):
 assert removed not in html,removed
assert 'GHI ÂM LIÊN TỤC' in html
assert 'current.voice.write(block);' in service and 'current.raw.write(block);' in service
assert 'RecordingArchive.queueExport(this, current)' in service
assert 'RecordingArchive.sync(current)' in service
assert 'AudioDynamics dynamics = new AudioDynamics();' in service
assert 'music.requestPause' in activity and 'music.requestStop' in activity
assert 'supportsRecordingLibrary' in activity
assert 'minSdk 28' in gradle and '200000 + githubRunNumber' in gradle
assert 'NATIVE_API = 11' in ota and 'BUILTIN_VERSION = "8.0.15"' in ota
assert 'wave-style-picker' in css
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('PASS: v9.5.5 native/UI contract, continuous WAV, limiter, library, Android 9')
