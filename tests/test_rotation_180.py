"""Regression test for the v9.4.1 native View flip and v9.4 OTA fallback.

These tests do not require the Android SDK. They exercise the real JS handler in
Node and inspect the native entrypoint; physical Android testing is still needed.
"""
from pathlib import Path
import subprocess
import json
import tempfile

root = Path(__file__).resolve().parents[1]
activity = (root / 'app/src/main/java/com/example/orbicrecorder/MainActivity.java').read_text()
js = (root / 'app/src/main/assets/ui/app.js').read_text()
css = (root / 'app/src/main/assets/ui/app.css').read_text()
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()

assert 'webRotated180 = config().getBoolean("uiRotated180", false)' in activity
assert 'web.setRotation(webRotated180 ? 180f : 0f);' in activity
assert 'webRotated180 = !webRotated180;' in activity
assert 'putBoolean("uiRotated180", webRotated180)' in activity
assert 'data.put("uiRotated180", webRotated180);' in activity
assert '@JavascriptInterface public boolean supportsReliableRotation() { return true; }' in activity
assert 'case "rotate180": rotate180(); break;' in activity
assert '    setRequestedOrientation(' not in activity  # The previous unreliable mechanism must not return.
assert 'android:screenOrientation="fullSensor"' in manifest  # Sensor rotation still works.
assert 'body.software-rotated-180' in css and 'rotate(180deg)' in css
assert 'aria-pressed' in js
assert "document.body.classList.contains('software-rotated-180')?-1:1" in js
assert 'swipeDistance=cap((e.clientX-swipeStart)*direction,0,maxSwipe());' in js

start = js.index("  $('rotate180').onclick=()=>{")
stop = js.index("  for(const id of ['music1'", start)
handler = js[start:stop]
# Execute exactly the event-handler code shipped in app.js with simulated
# Android bridges, not a copied/reimplemented algorithm.
node_test = r'''
'use strict';
const assert = require('assert');
const handler = __ROTATION_HANDLER__;
function fixture(bridge) {
  let flipped = false;
  let command = '';
  const attrs = {};
  const button = {
    textContent: '⤾ XOAY 180°',
    setAttribute: (k, v) => { attrs[k] = v; }
  };
  const document = {body: {classList: {toggle: (n) => {
    assert.strictEqual(n, 'software-rotated-180');
    flipped = !flipped;
    return flipped;
  }}}};
  const window = {Orbic: bridge};
  const $ = id => {assert.strictEqual(id, 'rotate180'); return button;};
  const send = msg => {command = msg;};
  new Function('$', 'window', 'document', 'send', handler)($, window, document, send);
  return {button, attrs, clicked: () => button.onclick(),
          flipped: () => flipped, command: () => command};
}
// New APK must use the Android View flip, never the CSS fallback simultaneously.
const native = fixture({supportsReliableRotation: () => true});
native.clicked();
assert.strictEqual(native.command(), 'rotate180');
assert.strictEqual(native.flipped(), false);
// Original v9.4 APK supports only action(); CSS fallback must not call it.
const old = fixture({action: () => {throw new Error('must never call legacy orientation');}});
old.clicked();
assert.strictEqual(old.command(), '');
assert.strictEqual(old.flipped(), true);
assert.match(old.button.textContent, /TRỞ LẠI/);
assert.strictEqual(old.attrs['aria-pressed'], 'true');
old.clicked();
assert.strictEqual(old.flipped(), false);
assert.strictEqual(old.attrs['aria-pressed'], 'false');
// A corrupted bridge should not make the rotation button silently inert.
const broken = fixture({supportsReliableRotation: () => {throw new Error('blocked');}});
broken.clicked();
assert.strictEqual(broken.flipped(), true);
console.log('PASS: Android View handler + original v9.4 OTA UI fallback (click, undo, error)');
'''
node_test = node_test.replace('__ROTATION_HANDLER__', json.dumps(handler))
with tempfile.TemporaryDirectory() as tmp:
    script = Path(tmp) / 'check.js'
    script.write_text(node_test, encoding='utf-8')
    subprocess.run(['node', str(script)], check=True)
print('PASS: source keeps 180° native rotation, saves state, exposes OTA fallback')
