"""Guard against a visible but inert manual START button."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
html = (root / "app/src/main/assets/ui/index.html").read_text()
js = (root / "app/src/main/assets/ui/app.js").read_text()
java = (root / "app/src/main/java/com/example/orbicrecorder/MainActivity.java").read_text()

assert 'id="start"' in html
assert re.search(r"\$\('start'\)\.onclick\s*=\s*\(\)\s*=>\s*\{[^}]*send\('start'\);", js, re.S)
assert 'case "start": startRecording(false); break;' in java
assert 'case "arm":' in java
assert "$('autoStart').onclick" in js
print("PASS: manual START button binds to native start command, preserving auto-trigger")
