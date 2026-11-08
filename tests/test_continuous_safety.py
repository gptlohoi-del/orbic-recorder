"""V9.5.5 regression: every captured frame is retained and both endings require a right swipe.

Node executes the actual shipped swipe handler; this is not a device-level test.
"""
from pathlib import Path
import json
import re
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'app/src/main/assets/ui'
native=root/'app/src/main/java/com/example/orbicrecorder'
html=(ui/'index.html').read_text()
js=(ui/'app.js').read_text()
css=(ui/'app.css').read_text()
activity=(native/'MainActivity.java').read_text()
service=(native/'RecorderService.java').read_text()
ota=(native/'OtaManager.java').read_text()

# No sound gate: each frame is written, and RAW precedes limiter processing.
for forbidden in ('boolean thresholdEnabled', 'threshold = cfg.getInt', 'candidate.addLast',
                  'tail.addLast', 'minSpeech', 'preFrames', 'tailFrames', 'boolean active',
                  'case "PAUSE"', 'case "RESUME"', 'case "MUSIC_PAUSE"', 'case "START_HELD"',
                  'videoModeSession'):
    assert forbidden not in service, forbidden
assert 'current.raw.write(block);' in service
assert 'dynamics.processInPlace(block);' in service
assert 'current.voice.write(block);' in service
assert service.index('current.raw.write(block);') < service.index('dynamics.processInPlace(block);') < service.index('current.voice.write(block);')
assert service.count('current.voice.write(block);') == 1
assert 'case "STOP_DISCARD"' in service and 'case "STOP"' in service
assert 'RecordingArchive.markDiscard(this, session);' in service
assert 'RecordingArchive.discardSession(this, session)' in service
assert 'builder.addAction(' not in service and 'addAction(' not in service
assert 'setContentIntent(PendingIntent.getActivity' in service
assert 'if (music.isActive() || music.isBlockingMic() || pendingMusic || pendingResume)' in activity
assert 'if (captureActive()) { toast("Không phát nhạc trong lúc ghi liên tục"); return; }' in activity
assert 'case "pause":' not in activity and 'case "resume":' not in activity
assert 'case "stop":' not in activity and 'case "discard":' not in activity
assert '@JavascriptInterface public void finishRecording(String choice)' in activity
assert '"save".equals(choice) ? "STOP" : "STOP_DISCARD"' in activity
assert 'NATIVE_API = 11' in ota
for gone in ('id="pause"', 'id="videoMode"', 'id="thresholdEnabled"',
             'id="thresholdLine"', 'id="thresholdMinus"', 'id="thresholdPlus"'):
    assert gone not in html, gone
assert 'id="saveModal"' in html and 'id="discardModal"' in html
assert 'VUỐT SANG PHẢI ĐỂ LƯU' in html and 'VUỐT SANG PHẢI ĐỂ HỦY' in html
assert '.swipe-thumb{position:absolute;left:4px' in css and '.swipe-fill{position:absolute;left:0;' in css
assert '.record-controls{grid-template-columns:repeat(2,minmax(0,1fr))}' in css
assert 'send(\'stop\')' not in js and 'send(\'discard\')' not in js
assert 'window.Orbic.finishRecording(kind)' in js
assert not re.search(r'\b(changeThreshold|updateThresholdSupport|refreshThreshold)\b',js)

begin=js.index('  function setupFinishSwipe(kind){')
end=js.index('  function confirmAction(',begin)
handler=js[begin:end]
node=r'''
const assert=require('assert');
const handler=__HANDLER__;
function fixture(){
  const events={},nodes={},sent=[];
  let time=5000;
  for(const id of ['saveModal','saveBackdrop','saveTrack','saveThumb','saveFill','cancelSave','stop',
                  'discardModal','discardBackdrop','discardTrack','discardThumb','discardFill','cancelDiscard','discard']){
    nodes[id]={hidden:id.endsWith('Modal'),style:{},clientWidth:300,offsetWidth:50,disabled:false,
      classList:{add(){},remove(){}},addEventListener(n,fn){events[id+':'+n]=fn;},
      setPointerCapture(){}};
  }
  const $=id=>{assert(nodes[id],id);return nodes[id];};
  const document={body:{classList:{contains:()=>false}}};
  const window={Orbic:{finishRecording:mode=>sent.push(mode)}};
  const current={running:true,saving:false,starting:false,supportsSwipeDiscard:true};
  const cap=(v,a,b)=>Math.min(b,Math.max(a,v));
  const Date={now:()=>time};
  const setText=()=>{};
  const src='let finishPending=false;let finishRequestedAt=0;\n'+handler+`\nreturn {closeSave,closeDiscard};`;
  new Function('$','document','window','current','cap','Date','setText',src)($,document,window,current,cap,Date,setText);
  const pointer=(kind,ev,x)=>{
    assert(events[kind+'Thumb:'+ev]);
    events[kind+'Thumb:'+ev]({pointerId:1,pointerType:'touch',clientX:x,button:0,preventDefault(){}});
  };
  const wait=n=>{time+=n;};
  return {nodes,sent,pointer,wait,current};
}
// Save: tapping, too-short, too-fast, canceling does not stop recording.
{
 const f=fixture();f.nodes.stop.onclick();assert.strictEqual(f.nodes.saveModal.hidden,false);
 f.pointer('save','pointerdown',25);f.wait(250);f.pointer('save','pointerup',25);
 assert.deepStrictEqual(f.sent,[]);
 f.pointer('save','pointerdown',25);f.pointer('save','pointermove',115);f.wait(250);f.pointer('save','pointerup',115);
 assert.deepStrictEqual(f.sent,[]);
 f.pointer('save','pointerdown',25);f.pointer('save','pointermove',264);f.wait(40);f.pointer('save','pointerup',264);
 assert.deepStrictEqual(f.sent,[]);
 f.pointer('save','pointerdown',25);f.pointer('save','pointermove',264);f.wait(200);f.pointer('save','pointercancel',264);
 assert.deepStrictEqual(f.sent,[]);
 // LEFTWARD movement must not complete the swipe.
 f.pointer('save','pointerdown',210);f.pointer('save','pointermove',30);f.wait(220);f.pointer('save','pointerup',30);
 assert.deepStrictEqual(f.sent,[]);
 f.pointer('save','pointerdown',25);f.pointer('save','pointermove',264);f.wait(200);f.pointer('save','pointerup',264);
 assert.deepStrictEqual(f.sent,['save']);
 assert.strictEqual(f.nodes.saveModal.hidden,true);
 f.nodes.stop.onclick();assert.strictEqual(f.nodes.saveModal.hidden,true); // latch: one finish
}
// Discard: also right swipe only. No chance to open both dialogs concurrently.
{
 const f=fixture();f.nodes.discard.onclick();assert.strictEqual(f.nodes.discardModal.hidden,false);
 f.nodes.stop.onclick();assert.strictEqual(f.nodes.saveModal.hidden,true);
 f.pointer('discard','pointerdown',25);f.pointer('discard','pointermove',264);f.wait(201);f.pointer('discard','pointerup',264);
 assert.deepStrictEqual(f.sent,['discard']);
 assert.strictEqual(f.nodes.discardModal.hidden,true);
}
console.log('PASS: deliberate right swipe required for both save and discard');
'''.replace('__HANDLER__',json.dumps(handler))
with tempfile.TemporaryDirectory() as tmp:
    f=Path(tmp)/'swipe.js';f.write_text(node)
    subprocess.run(['node',str(f)],check=True)
print('PASS: continuous native path, old actions refused, no notification STOP, OTA API guard')
