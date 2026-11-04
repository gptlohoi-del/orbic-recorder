"""Verify v9.5 source contracts and exercise the real destructive swipe UI in Node.

These checks run without an Android SDK; they do NOT certify APK execution on a device.
"""
from pathlib import Path
import json
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/example/orbicrecorder'
ui = root / 'app/src/main/assets/ui'
js = (ui / 'app.js').read_text(encoding='utf-8')
html = (ui / 'index.html').read_text(encoding='utf-8')
activity = (java / 'MainActivity.java').read_text(encoding='utf-8')
service = (java / 'RecorderService.java').read_text(encoding='utf-8')
ota = (java / 'OtaManager.java').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')

assert 'versionCode (150000 + githubRunNumber)' in gradle
assert 'versionName (githubRunNumber > 0 ? "9.5.0.${githubRunNumber}"' in gradle
assert 'ORBIC RECORDER v9.5.0' in activity
assert 'public static final String BUILTIN_VERSION = "8.0.7"' in ota
assert '(!versionValid(current) || !newer(current, BUILTIN_VERSION) ||' in ota
assert 'newer(BUILTIN_VERSION, previous)' in ota
assert 'newer(BUILTIN_VERSION, old)' in ota
assert 'data.put("supportsThirdTrack", true)' in activity
assert 'data.put("supportsSwipeDiscard", true)' in activity
assert 'data.put("song3Name", musicName(3))' in activity
assert 'STOP_DISCARD' in service and 'RecordingArchive.markDiscard' in service
assert 'RecordingArchive.discardSession' in service
assert 'if (code == REQUEST_MIC)' in activity
assert 'REQUEST_MIC_MUSIC' not in activity  # Music needs no RECORD_AUDIO permission
assert 'addAction(android.R.drawable.ic_media_pause' in service
assert 'addAction(android.R.drawable.ic_menu_save' in service
assert 'featureCompatibility' in html
assert 'discardPending=true;' in js

start = js.index("  const swipeTrack=$('discardTrack')")
end = js.index('  function confirmAction(', start)
handler = js[start:end]
script = r'''
'use strict';
const assert = require('assert');
const handler = __SWIPE_HANDLER__;
function fixture(native, rotated=false) {
  const callbacks = {};
  const elements = {};
  let commandCount=0, now=1000;
  const mk = id => ({
    hidden: id==='discardModal',
    clientWidth: 320, offsetWidth: 50, style: {},
    classList: {add(){},remove(){}},
    addEventListener(type, fn) {callbacks[id+':'+type]=fn;},
    setPointerCapture() {},
  });
  for (const id of ['discardTrack','discardThumb','swipeFill','discardModal',
                   'discard','cancelDiscard','discardBackdrop']) elements[id]=mk(id);
  const document = {body: {classList: {contains: v => rotated && v==='software-rotated-180'}}};
  const $ = id => elements[id];
  const current = native;
  const send = cmd => {assert.strictEqual(cmd,'discard');commandCount++;};
  const cap = (x,min,max)=>Math.min(max,Math.max(min,x));
  const Date = {now:()=>now};
  const f = new Function('$','document','current','send','cap','Date',
    `let discardPending=false,discardRequestedAt=0;\n`+handler+
    `\nreturn {open:()=>$('discard').onclick(),modal:()=>$('discardModal').hidden};`);
  const state = f($,document,current,send,cap,Date);
  const event=(type,x,id=1)=>{
    const cb = callbacks['discardThumb:'+type]; assert(cb, type);
    cb({pointerId:id, pointerType:'touch',button:0,clientX:x,
        preventDefault(){}});
  };
  return {...state,event,sent:()=>commandCount,advance:(ms)=>{now+=ms;}};
}
const yes = {running:true,saving:false,starting:false,
    supportsSwipeDiscard:true,nativeLabel:'ORBIC RECORDER v9.5.0'};
// No command should be issued by tap-only, partial, rapid, or canceled drag.
let a=fixture(yes);a.open();a.event('pointerdown',40);a.advance(240);a.event('pointerup',40);
assert.strictEqual(a.sent(),0);
a.event('pointerdown',40);a.advance(240);a.event('pointermove',130);a.event('pointerup',130);
assert.strictEqual(a.sent(),0);
a.event('pointerdown',40);a.event('pointermove',303);a.advance(60);a.event('pointerup',303);
assert.strictEqual(a.sent(),0);
a.event('pointerdown',40);a.advance(200);a.event('pointermove',303);a.event('pointercancel',303);
assert.strictEqual(a.sent(),0);
// A completed deliberate drag sends EXACTLY ONE native command.
a.event('pointerdown',40);a.advance(220);a.event('pointermove',303);a.event('pointerup',303);
assert.strictEqual(a.sent(),1);assert.strictEqual(a.modal(),true);
a.open();assert.strictEqual(a.modal(),true); // pending guard
assert.strictEqual(a.sent(),1);
// An older unsupported APK cannot open the destructive swipe at all.
a=fixture({running:true,saving:false,starting:false,nativeLabel:'ORBIC RECORDER v9.3.0'});
a.open();assert.strictEqual(a.modal(),true);assert.strictEqual(a.sent(),0);
// The user's earlier v9.4.2 native source already includes the delete engine.
a=fixture({running:true,saving:false,starting:false,nativeLabel:'ORBIC RECORDER v9.4.2'});
a.open();assert.strictEqual(a.modal(),false);
// With CSS fallback rotated 180°, the physical swipe direction is mirrored.
a=fixture(yes,true);a.open();a.event('pointerdown',304);a.advance(230);
a.event('pointermove',41);a.event('pointerup',41);assert.strictEqual(a.sent(),1);
console.log('PASS: swipe only submits after a deliberate full drag, once; legacy gate and 180° work');
'''.replace('__SWIPE_HANDLER__', json.dumps(handler))
with tempfile.TemporaryDirectory() as temp:
    path = Path(temp) / 'test-swipe.js'
    path.write_text(script, encoding='utf-8')
    subprocess.run(['node', str(path)], check=True)
print('PASS: native v9.5 capabilities, OTA baseline upgrade, independent music permission, notification controls')
