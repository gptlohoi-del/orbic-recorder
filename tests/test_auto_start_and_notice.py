"""v9.5.7 auto trigger + economy settings + warning display contracts."""
from pathlib import Path
import re,subprocess
r=Path(__file__).resolve().parents[1];java=r/'app/src/main/java/com/example/orbicrecorder';ui=r/'app/src/main/assets/ui'
s=(java/'RecorderService.java').read_text();m=(java/'MainActivity.java').read_text();j=(ui/'app.js').read_text();h=(ui/'index.html').read_text();c=(ui/'app.css').read_text()
assert 'case "ARM":' in s and 'case "DISARM":' in s
assert 'new InputSignalTrigger(triggerDb, 50, 5)' in s
assert 'boolean detected = signal.offer(block);' in s
assert 'signal.drainPreRoll()' in s and 'recording = true;\n                    armed = false;' in s
assert 'final boolean saveRaw = false;' in s
assert 'compactor.accept(processed,' in s
assert 'compactor.accept(block,' in s
assert 'compactor.flushTail(wavSink(current));' in s
assert 'if (partFrames >= splitFrames && (!economy || !compactor.inQuietSpan()))' in s
assert 'getRoutedDevice()' in s and 'getUsableSpace()' in s
assert 'data.put("recordMode",' in m and 'data.put("silenceSeconds",' in m
assert 'data.put("micWarning",' in m and 'data.put("storageWarning",' in m
assert 'if (captureActive() || recorder().getBoolean("starting", false))' in m
assert 'edit.putString("recordMode", mode);' in m
assert 'id="tabSafe"' in h and 'id="tabEconomy"' in h
assert 'id="noticeColor"' in h and 'id="noticeNumbers"' in h
assert 'silenceSeconds' in j and 'step:10' in j
assert "send('arm');" in j and "$('autoStart').classList.toggle('active'" in j
assert "window.Orbic.finishRecording('save')" in j
assert "setText('systemNote'" in j
assert 'var(--notice-color)' in c and 'var(--notice-weight)' in c
assert 'NATIVE_API = 13' in (java/'OtaManager.java').read_text()
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('PASS: auto trigger, long-silence trimming, preference, mic and disk warning, notice typography')
