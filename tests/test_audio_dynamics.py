"""Compile and exercise the pure Java limiter without a full Android SDK."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root = Path(__file__).resolve().parents[1]
helper = root / 'app/src/main/java/com/example/orbicrecorder/AudioDynamics.java'
test = root / 'tests/AudioDynamicsTest.java'
with TemporaryDirectory(prefix='orbic-pcm-test-') as t:
    tmp = Path(t)
    pkg = tmp / 'com/example/orbicrecorder'
    pkg.mkdir(parents=True)
    (pkg / 'RecordingArchive.java').write_text('''package com.example.orbicrecorder;
final class RecordingArchive { static final int SAMPLE_RATE = 16000; }
''', encoding='utf-8')
    subprocess.run(['javac', '-d', str(tmp), str(pkg / 'RecordingArchive.java'),
                    str(helper), str(test)], check=True)
    subprocess.run(['java', '-cp', str(tmp),
                    'com.example.orbicrecorder.AudioDynamicsTest'], check=True)
