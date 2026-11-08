"""Pure Java regression for disk-backed long-silence trimming, no Android SDK required."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as folder:
    subprocess.run(['javac','-d',folder,
                    str(root/'app/src/main/java/com/example/orbicrecorder/SilenceCompactor.java'),
                    str(root/'tests/SilenceCompactorTest.java')],check=True)
    subprocess.run(['java','-cp',folder,'SilenceCompactorTest'],check=True)
