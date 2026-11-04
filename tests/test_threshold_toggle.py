"""Regression guards for v9.4.2 threshold bypass and OTA/native compatibility.

These source checks do not replace listening to captured WAV files on a device.
"""
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/example/orbicrecorder'
ui = root / 'app/src/main/assets/ui'
activity = (java / 'MainActivity.java').read_text(encoding='utf-8')
service = (java / 'RecorderService.java').read_text(encoding='utf-8')
html = (ui / 'index.html').read_text(encoding='utf-8')
js = (ui / 'app.js').read_text(encoding='utf-8')
css = (ui / 'app.css').read_text(encoding='utf-8')
ota = (java / 'OtaManager.java').read_text(encoding='utf-8')

# First-time configuration records continuously by default. Stored user choices take precedence.
assert 'data.put("thresholdEnabled", cfg.getBoolean("thresholdEnabled", false));' in activity
assert 'edit.putBoolean("thresholdEnabled", v.getBoolean("thresholdEnabled"))' in activity
assert 'boolean thresholdEnabled = cfg.getBoolean("thresholdEnabled", false);' in service
assert service.count('cfg.getBoolean("thresholdEnabled", false)') >= 2  # at start and every 5 frames
assert re.search(r'if \(elapsed % 5 == 0\) \{\s*threshold = cfg.getInt\("threshold", -38\);\s*thresholdEnabled = cfg.getBoolean', service)

# Disabling VAD bypasses even a completely zeroed PCM frame, does not require
# minSpeech, and flushes any candidate/pre-roll/tail buffers before direct write.
gate = service.index('if (!thresholdEnabled) {')
regular = service.index('} else if (active) {', gate)
bypass = service[gate:regular]
for action in ('active = true;', 'segments++;', 'flush(current, pre)',
               'flush(current, candidate)', 'flush(current, tail)',
               'current.voice.write(block);', 'current.voiceBytes += block.length;',
               'kept += block.length;'):
    assert action in bypass, action
assert 'boolean loud = !thresholdEnabled || db >= threshold;' in service
assert '● GHI LIÊN TỤC – ĐÃ TẮT NGƯỠNG' in service
assert service.index('current.raw.write(block)') < service.index('if (!thresholdEnabled) {')
assert 'if (musicHold)' in service and 'if (paused)' in service

# UI presents the option and clearly explains old APKs cannot support it.
assert 'id="thresholdEnabled" type="checkbox"' in html
assert 'id="thresholdSupport"' in html
assert "$('thresholdEnabled').disabled=!supported;" in js
assert "typeof d.thresholdEnabled==='boolean'" in js
assert 'save({thresholdEnabled:enabled})' in js
assert "$('thresholdMinus').disabled=$('thresholdPlus').disabled=!enabled" in js
assert "current && current.thresholdEnabled===false?'ĐÃ TẮT'" in js
assert 'classList.toggle(\'threshold-disabled\',!enabled)' in js
assert '.waveform.threshold-disabled .threshold-line' in css
assert 'Cần cài APK v9.4.2' in js

# OTA is still signed UI-only, using the same native API as existing APKs;
# the old APK sees an inactive switch rather than a misleading promise.
assert 'public static final int NATIVE_API = 8;' in ota
assert 'public static final String BUILTIN_VERSION = "8.0.8";' in ota
assert 'private static final String[] FILES = {"index.html", "app.css", "app.js"};' in ota
assert 'validSignature(manifest)' in ota
subprocess.run(['node', '--check', str(ui / 'app.js')], check=True)
print('PASS: threshold defaults, real zero-audio bypass, hot toggle, legacy OTA behavior')
