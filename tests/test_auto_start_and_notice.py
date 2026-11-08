"""v9.5.5 contracts: arm->start exactly once, continuous output, settings, right swipe."""
from pathlib import Path
import re, subprocess
r=Path(__file__).resolve().parents[1]
java=r/'app/src/main/java/com/example/orbicrecorder'
ui=r/'app/src/main/assets/ui'
s=(java/'RecorderService.java').read_text()
m=(java/'MainActivity.java').read_text()
j=(ui/'app.js').read_text()
h=(ui/'index.html').read_text()
c=(ui/'app.css').read_text()
assert 'case "ARM":' in s and 'case "DISARM":' in s
assert 'if (!recording && !armed && !stopping' in s
assert 'if (armed && !recording && !stopping) armed = false;' in s
assert 'final int triggerDb' in s and 'new InputSignalTrigger(triggerDb, 50, 5)' in s
assert 'boolean detected = signal.offer(block);' in s
assert 'if (!automatic) current = RecordingArchive.open' in s
assert 'if (current.raw != null)' in s and 'current.raw.write(buffered);' in s
assert 'dynamics.processInPlace(processed);' in s
assert 'current.voice.write(processed);' in s
assert 'signal.drainPreRoll()' in s
assert s.index('recording = true;\n                    armed = false;') < s.index('for (byte[] buffered : signal.drainPreRoll())')
assert 'if (!recording) break;' in s
assert 'current.voice.write(block);' in s
assert 'if (partFrames >= splitFrames)' in s
assert 'RecordingArchive.markDiscard' in s
assert 'if (!stopping && !armed) stopSelf()' in s
assert 'final boolean saveRaw = automatic || cfg.getBoolean("backupRaw", true);' in s
assert 'data.put("armed",' in m and 'data.put("supportsInputTrigger", true)' in m
assert 'data.put("autoStarted",' in m and 'data.put("triggerDb",' in m
assert 'case "arm":' in m and 'sendRecorder("DISARM")' in m
assert 'pendingArmPermission' in m and 'startRecording(pendingArmPermission);' in m
assert 'if (automatic) config().edit().putBoolean("backupRaw", true).apply();' in m
assert 'return recorder().getBoolean("running", false) || recorder().getBoolean("armed", false)' in m
assert 'v.has("noticeColor")' in m and 'color.matches("#[0-9A-Fa-f]{6}")' in m
assert 'noticeWeight' in m and 'triggerDb' in m
assert 'id="autoStart"' in h and 'id="noticeColor"' in h
assert 'id="noticeNumbers"' in h and 'id="recordNumbers"' in h
assert 'noticeWeight' in j and 'triggerDb' in j
assert "send('arm');" in j
assert "$('autoStart').classList.toggle('active'" in j
assert "document.documentElement.style.setProperty('--notice-color'" in j
assert 'var(--notice-color)' in c and 'var(--notice-weight)' in c
assert '.auto-start-toggle.active{background:#117958' in c
assert 'VUỐT SANG PHẢI ĐỂ LƯU' in h and 'VUỐT SANG PHẢI ĐỂ HỦY' in h
assert "setupFinishSwipe('save')" in j and "setupFinishSwipe('discard')" in j
assert 'NATIVE_API = 11' in (java/'OtaManager.java').read_text()
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('PASS: one-shot armed trigger, preroll, uninterrupted WAV, native permissions, notice typography, right swipes')
