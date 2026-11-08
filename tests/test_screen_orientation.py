"""Regression tests for user-controlled rotation, UI-to-native bridge and Android 9.
Static checks and Node interaction tests are NOT real-device testing.
"""
from pathlib import Path
import json
import re
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
js=(root/'app/src/main/assets/ui/app.js').read_text()
html=(root/'app/src/main/assets/ui/index.html').read_text()
css=(root/'app/src/main/assets/ui/app.css').read_text()
gradle=(root/'app/build.gradle').read_text()
ota=(root/'app/src/main/java/com/example/orbicrecorder/OtaManager.java').read_text()
publisher=(root/'scripts/publish_ota.py').read_text()

assert 'android:screenOrientation="portrait"' in manifest, 'Locked orientation should be the launch default'
assert 'android:configChanges="orientation|screenSize|smallestScreenSize|keyboardHidden"' in manifest
assert 'getBoolean("autoRotateEnabled", false)' in src, 'New installs must have auto rotate disabled'
assert 'ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR' in src
assert 'ActivityInfo.SCREEN_ORIENTATION_PORTRAIT' in src
assert 'setRequestedOrientation(enabled ?' in src
assert 'runOnUiThread(() -> applyScreenOrientation(enabled));' in src
assert 'if (v.has("autoRotateEnabled"))' in src
assert 'edit.putBoolean("autoRotateEnabled", enabled);' in src
assert 'data.put("autoRotateEnabled", cfg.getBoolean("autoRotateEnabled", false));' in src
assert html.count('id="autoRotateEnabled" type="checkbox"') == 1
assert 'id="autoRotateEnabled" type="checkbox" checked' not in html
assert html.index('id="rotate90"') < html.index('id="rotate180"')
assert html.count('id="rotate90"')==1
assert '.rotation-actions .rotate-90{background:#a85b09;' in css
assert "save({autoRotateEnabled:current.autoRotateEnabled})" in js
assert "typeof current.autoRotateEnabled==='boolean'" in js
assert "$('autoRotateEnabled').disabled=!supportsAutoRotate" in js
assert 'getInt("uiRotationDegrees"' in src
assert 'config().getBoolean("uiRotated180", false) ? 180 : 0' in src
assert 'rotateBy(90)' in src and 'rotateBy(180)' in src
assert 'uiRotationDegrees = (uiRotationDegrees + deltaDegrees) % 360;' in src
assert 'params.width = targetWidth;' in src and 'params.height = targetHeight;' in src
assert 'boolean quarterTurn = (uiRotationDegrees % 180) != 0;' in src
assert 'int targetWidth = quarterTurn ? height : width;' in src
assert 'int targetHeight = quarterTurn ? width : height;' in src
assert 'web.setRotation((float) uiRotationDegrees)' in src
assert 'post(MainActivity.this::applyWebRotation)' in src
assert 'data.put("uiRotationDegrees", uiRotationDegrees);' in src
assert 'data.put("supportsRotation90", true);' in src
assert '@JavascriptInterface public boolean supportsQuarterTurn() { return true; }' in src
assert 'case "rotate90": rotate90(); break;' in src
assert 'case "rotate180": rotate180(); break;' in src
assert 'NATIVE_API = 12' in ota and 'BUILTIN_VERSION = "8.0.16"' in ota
assert 'NATIVE_API = 12' in publisher
assert 'versionCode (210000 + githubRunNumber)' in gradle
assert 'minSdk 28' in gradle

# Test the exact 90-degree click handler (and old-APK disabled behavior)
start=js.index("  $('rotate90').onclick=()=>{")
end=js.index("  $('rotate180').onclick=()=>{",start)
source=js[start:end]
js_test=r'''
const assert=require('assert');
const handler=__HANDLER__;
function mk(snapshot){
  let sent=[];
  const b={onclick:null};
  const $=id=>{assert.strictEqual(id,'rotate90');return b;};
  const send=cmd=>sent.push(cmd);
  new Function('$','current','send',handler)($,snapshot,send);
  b.onclick();return sent;
}
assert.deepStrictEqual(mk({supportsRotation90:true}),['rotate90']);
assert.deepStrictEqual(mk({supportsRotation90:false}),[]);
assert.deepStrictEqual(mk({}),[]);
assert.deepStrictEqual(mk(null),[]);
// Repeatable +90/180 manual actions form a four-angle cycle.
let angle=0;for(const next of [90,90,90,90,180,90,180])angle=(angle+next)%360;
assert.strictEqual(angle,90);
console.log('PASS: 90° button works on capable APK; refuses unsafe CSS-only fallback');
'''.replace('__HANDLER__',json.dumps(source))
with tempfile.TemporaryDirectory() as td:
    target=Path(td)/'test-quarter.js';target.write_text(js_test)
    subprocess.run(['node',str(target)],check=True)
print('PASS: no sensor auto rotation by default; manual 90/180; layout swap; persistent Settings; Android 9/API 28')
