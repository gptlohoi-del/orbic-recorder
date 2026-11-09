"""v9.5.7 tab persistence, safe default, and numeric setting bounds."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1];java=root/'app/src/main/java/com/example/orbicrecorder';ui=root/'app/src/main/assets/ui'
activity=(java/'MainActivity.java').read_text();service=(java/'RecorderService.java').read_text()
html=(ui/'index.html').read_text();js=(ui/'app.js').read_text()
assert 'modeTabsMigrated' in activity
assert '.putString("recordMode", "safe")' in activity
assert '.putInt("silenceSeconds", 60)' in activity
assert '.putBoolean("backupRaw", false)' in activity
assert 'data.put("recordMode", cfg.getString("recordMode", "safe"))' in activity
assert 'final boolean saveRaw = false;' in service
assert '"silenceSeconds".equals(numeric[i]) && (number - 20) % 10 != 0' in activity
assert 'id="backupRaw"' not in html
assert 'id="tabSafe"' in html and 'id="tabEconomy"' in html
assert "save({recordMode:mode})" in js
assert "$('tabSafe').disabled=$('tabEconomy').disabled=locked;" in js
assert 'int splitMinutes = cfg.getInt("splitMinutes", 60)' in service
assert 'data.put("splitMinutes", cfg.getInt("splitMinutes", 60))' in activity
assert 'id="noticeNumbers"' in html
fields=re.search(r'String\[\] numeric = \{(.*?)\};',activity,re.S).group(1)
low=re.search(r'int\[\] low = \{(.*?)\};',activity,re.S).group(1)
high=re.search(r'int\[\] high = \{(.*?)\};',activity,re.S).group(1)
assert len(re.findall(r'"(.*?)"',fields))==len(low.split(','))==len(high.split(','))
print('PASS: safe first-install default, tab remembers selection, no RAW, 20..1000s / step10')
