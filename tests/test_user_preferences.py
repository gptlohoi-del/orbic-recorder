"""New defaults/UI regression tests; Android 9 run still requires a device."""
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

assert '<strong>ORBIC RECORDER</strong>' not in html
assert html.count('id="rotate180"') == 1
assert html.index('♫ PHÁT NHẠC') < html.index('id="rotate180"') < html.index('id="music1"')
assert 'class="app-titlebar"' not in html
assert 'grid-template-rows:minmax(0,1.1fr) minmax(0,1fr) auto' in css
assert '.music-stack{grid-column:1;grid-row:1;' in css
assert '.record{grid-column:2;grid-row:1}' in css

assert 'id="noticeNumbers"' in html and '🔔 VÙNG THÔNG BÁO' in html
assert "{key:'noticeFont',label:'Kích thước chữ vùng THÔNG BÁO',unit:'%',default:100,min:75,max:200,step:5,group:'notice'}" in js
assert 'data.put("noticeFont", cfg.getInt("noticeFont", 100))' in activity
assert 'data.noticeFont' in js and "['uiFont','noticeFont','uiOpacity','waveHeight','theme']" in js
assert '--notice-font-size' in css and '--notice-small-font-size' in css
assert "style.setProperty('--notice-font-size'" in js
assert "style.setProperty('--notice-small-font-size'" in js
assert "current.waveStyle||data.waveStyle||'line'" in js
assert "d.waveStyle||'line'" in js
assert 'data-style="line"' in html
assert 'cfg.getString("waveStyle", "line")' in activity
assert "?choice:'line'" in js

assert 'id="thresholdEnabled" type="checkbox" checked' not in html
assert 'id="backupRaw" type="checkbox" checked' not in html
assert '<option value="60" selected>60 phút</option>' in html
assert 'data.put("thresholdEnabled", cfg.getBoolean("thresholdEnabled", false))' in activity
assert 'data.put("backupRaw", cfg.getBoolean("backupRaw", false))' in activity
assert 'data.put("splitMinutes", cfg.getInt("splitMinutes", 60))' in activity
assert 'boolean thresholdEnabled = cfg.getBoolean("thresholdEnabled", false)' in service
assert service.count('getBoolean("thresholdEnabled", false)') >= 5
assert 'cfg.getBoolean("backupRaw", false)' in service
assert 'int splitMinutes = cfg.getInt("splitMinutes", 60)' in service
assert 'if (splitMinutes < 1 || splitMinutes > 60) splitMinutes = 60' in service
assert "$('backupRaw').checked=current.backupRaw===true" in js
assert "String(coalesce(current.splitMinutes,60))" in js
# Native reads existing persisted preferences before applying new fallback values.
assert 'getSharedPreferences("config", MODE_PRIVATE)' in activity

# The bridge must accept the new numeric preference and enforce a safe bound.
fields = re.search(r'String\[\] numeric = \{(.*?)\};',activity,re.S).group(1)
lo = re.search(r'int\[\] low = \{(.*?)\};',activity,re.S).group(1)
hi = re.search(r'int\[\] high = \{(.*?)\};',activity,re.S).group(1)
keys = re.findall(r'"(.*?)"',fields)
lows = [int(n.strip()) for n in lo.split(',')]
highs = [int(n.strip()) for n in hi.split(',')]
assert len(keys) == len(lows) == len(highs)
index = keys.index('noticeFont')
assert lows[index] == 75 and highs[index] == 200

assert "const details=document.createElement('button');details.type='button'" in js
assert 'details.onclick=play.onclick' in js
assert 'details.setAttribute(\'aria-label\',\'Nghe bản ghi \'+item.name)' in js
assert 'recording-file-details:focus-visible' in css
assert 'recordings.sort((a,b)' in js

assert 'BUILTIN_VERSION = "8.0.8"' in ota
assert 'NATIVE_API = 8' in ota
assert 'minSdk 28' in (root/'app/build.gradle').read_text(encoding='utf-8')
subprocess.run(['node','--check',str(ui/'app.js')],check=True)
print('PASS: single heading, settings font, 4 native defaults, tap filename, OTA, API 28')
