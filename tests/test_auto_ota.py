"""Source/UI contract regression for automatic OTA. Device/network test still required."""
from pathlib import Path
import re
import subprocess
root = Path(__file__).resolve().parents[1]
activity = (root / 'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text(encoding='utf-8')
manager = (root / 'app/src/main/java/com/example/orbicrecorder/OtaManager.java').read_text(encoding='utf-8')
html = (root / 'app/src/main/assets/ui/index.html').read_text(encoding='utf-8')
js = (root / 'app/src/main/assets/ui/app.js').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
publisher = (root / 'scripts/publish_ota.py').read_text(encoding='utf-8')

assert 'id="autoOtaEnabled" type="checkbox" checked' in html
assert 'TỰ ĐỘNG CẬP NHẬT OTA' in html
assert 'preferences.getBoolean("autoUpdate", true)' in manager
assert 'preferences.edit().putBoolean("autoUpdate", enabled).apply()' in manager
assert 'data.put("autoOtaEnabled", ota.isAutoUpdateEnabled())' in activity
assert 'if (v.has("autoOtaEnabled"))' in activity
assert 'ota.setAutoUpdateEnabled(enabled)' in activity
assert "save({autoOtaEnabled:current.autoOtaEnabled})" in js
assert "typeof current.autoOtaEnabled==='boolean'" in js
assert "$('autoOtaEnabled').disabled=!supportsAutoOta" in js
assert 'ota.check();' in activity
assert 'private void maybeInstallAutoOta()' in activity
assert 'if (!otaForeground || ota == null || !ota.canAutoInstall()) return;' in activity
assert 'captureActive() || state.getBoolean("starting", false)' in activity
assert 'RecorderService.isProcessCapturing() || RecordingArchive.isRecovering()' in activity
assert 'music == null || music.isActive() || pendingMusic || pendingResume' in activity
assert 'state.getBoolean("musicSuspended", false)' in activity
assert 'mainThread.postDelayed(this, 3000L)' in activity
assert 'mainThread.removeCallbacks(autoOtaRetry)' in activity
assert 'ota.install();' in activity
assert 'private volatile boolean busy = false;' in manager
assert 'offeredManifest != null && !offeredVersion.isEmpty()' in manager
assert '!offeredVersion.equals(preferences.getString("autoSkippedVersion", ""))' in manager
assert '.putString("autoSkippedVersion", rejectedVersion).apply()' in manager
assert '.remove("autoSkippedVersion").commit()' in manager
assert 'validSignature(manifest)' in manager
assert 'if (!hash(archive).equals(manifest.getString("sha256")))' in manager
assert '"downloading".equals(ota.getStatus())' in activity
assert 'public static final String BUILTIN_VERSION = "8.0.15"' in manager
assert 'public static final int NATIVE_API = 11' in manager
assert 'minSdk 28' in gradle and 'versionCode (200000 + githubRunNumber)' in gradle
assert 'max(newest, version_tuple(older))' in publisher
assert 'ota/latest.json' in (root / 'HUONG-DAN-TU-DONG-OTA-ANDROID9.md').read_text()
# Automatic updater cannot start when the UI/activity is paused.
assert re.search(r'protected void onPause\(\)\s*\{\s*otaForeground = false;', activity)
subprocess.run(['node', '--check', str(root / 'app/src/main/assets/ui/app.js')], check=True)
print('PASS: auto OTA default ON, persistence, safe deferred install, signed-only, rollback exclusion, Android 9')
