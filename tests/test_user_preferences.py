"""User preferences and migration in continuous-only build."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/example/orbicrecorder'
ui=root/'app/src/main/assets/ui'
activity=(java/'MainActivity.java').read_text()
service=(java/'RecorderService.java').read_text()
html=(ui/'index.html').read_text()
js=(ui/'app.js').read_text()
assert 'id="backupRaw" type="checkbox" checked' in html
assert 'data.put("backupRaw", cfg.getBoolean("backupRaw", true))' in activity
assert 'final boolean saveRaw = automatic || cfg.getBoolean("backupRaw", true)' in service
assert '.putBoolean("backupRaw", true)' in activity
assert 'continuousModeMigrated' in activity
assert "$('backupRaw').disabled=!!current.running||!!current.starting||!!current.saving;" in js
assert "$('backupRaw').checked=current.backupRaw!==false" in js
assert 'int splitMinutes = cfg.getInt("splitMinutes", 60)' in service
assert 'if (splitMinutes < 1 || splitMinutes > 60) splitMinutes = 60' in service
assert 'data.put("splitMinutes", cfg.getInt("splitMinutes", 60))' in activity
assert 'id="noticeNumbers"' in html
assert 'data.put("noticeFont", cfg.getInt("noticeFont", 100))' in activity
fields=re.search(r'String\[\] numeric = \{(.*?)\};',activity,re.S).group(1)
low=re.search(r'int\[\] low = \{(.*?)\};',activity,re.S).group(1)
high=re.search(r'int\[\] high = \{(.*?)\};',activity,re.S).group(1)
keys=re.findall(r'"(.*?)"',fields)
assert len(keys)==len(low.split(','))==len(high.split(','))
assert 'threshold' not in keys and 'minSpeech' not in keys
print('PASS: continuous migration, RAW default ON, segment setting and numeric bridge bounds')
