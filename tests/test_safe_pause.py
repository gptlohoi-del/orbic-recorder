"""v9.5.7 SAFE PAUSE gesture and native guard regression checks.
Source checks and Node gestures are not an Android device test.
"""
from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parents[1]
ui = root / 'app/src/main/assets/ui'
java = root / 'app/src/main/java/com/example/orbicrecorder'
html = (ui/'index.html').read_text()
js = (ui/'app.js').read_text()
activity = (java/'MainActivity.java').read_text()
service = (java/'RecorderService.java').read_text()

assert 'id="recordPause"' in html
assert 'id="pauseModal"' in html and 'id="pauseTrack"' in html
assert 'VUỐT SANG PHẢI ĐỂ PAUSE' in html
assert 'id="saveModal"' in html and 'VUỐT SANG PHẢI ĐỂ LƯU' in html
assert html.index('id="recordPause"') < html.index('id="economyActions"'), 'PAUSE only in SAFE tab'
assert 'data.put("supportsRecordingPause", true)' in activity
assert 'data.put("pausedMs", p.getLong("pausedMs", 0L))' in activity
assert '@JavascriptInterface public void pauseRecording()' in activity
assert '@JavascriptInterface public void continueRecording()' in activity
assert 'sendRecorder("PAUSE")' in activity and 'sendRecorder("CONTINUE")' in activity
assert 'case "pause":' not in activity and 'case "resume":' not in activity
assert 'case "PAUSE":' in service and 'case "CONTINUE":' in service
assert 'safeSession = !automatic;' in service
assert 'if (safeSession && recording && !armed && !stopping && !paused)' in service
assert 'if (safeSession && recording && !armed && !stopping && paused)' in service
assert 'if (paused) {' in service
assert 'RecordingArchive.sync(current);' in service
assert 'SystemClock.elapsedRealtime() - pauseStartedAt' in service
assert 'continue;\n                }\n                pauseSynced = false;\n                elapsed++;' in service
assert 'if (recording) {' in service and 'case "STOP":' in service
assert "$('recordPause').textContent=d.paused?'▶ CONTINUE':'Ⅱ PAUSE'" in js
assert 'pauseRequestPending' in js
assert 'window.Orbic.pauseRecording()' in js and 'window.Orbic.continueRecording()' in js
assert "setText('recordState',d.paused?'⏸ PAUSE '+fmt(d.pausedMs):" in js

start = js.index('  let pauseRequestPending=false;')
end = js.index('  // SAVE needs a deliberate right swipe;', start)
handler = js[start:end]
node = r'''
const assert=require('assert');
let now=1000;const calls=[];const events={};const nodes={};
for(const id of ['pauseModal','pauseBackdrop','pauseTrack','pauseThumb','pauseFill','cancelPause','recordPause','error']){
  nodes[id]={hidden:id==='pauseModal',style:{},clientWidth:300,offsetWidth:50,
    classList:{add(){},remove(){},contains(){return false}},
    addEventListener(n,fn){events[id+':'+n]=fn},setPointerCapture(){}};
}
const $=id=>{assert(nodes[id],id);return nodes[id]};
const document={body:{classList:{contains:()=>false}}};
const window={Orbic:{pauseRecording:()=>calls.push('PAUSE'),continueRecording:()=>calls.push('CONTINUE')}};
const current={recordMode:'safe',supportsRecordingPause:true,running:true,saving:false,starting:false,paused:false};
const cap=(v,min,max)=>Math.max(min,Math.min(max,v));
const Date={now:()=>now};const setText=()=>{};
const controls=new Function('$','document','window','current','cap','Date','setText',__HANDLER__ + '\nreturn {clearPending(){pauseRequestPending=false}};')($,document,window,current,cap,Date,setText);
function move(event,x){events['pauseThumb:'+event]({pointerId:1,pointerType:'touch',clientX:x,
  button:0,preventDefault(){}})}
function open(){nodes.recordPause.onclick();assert(!nodes.pauseModal.hidden)}
open();move('pointerdown',25);now+=230;move('pointerup',25);assert.deepStrictEqual(calls,[]);
open();move('pointerdown',25);move('pointermove',125);now+=230;move('pointerup',125);
assert.deepStrictEqual(calls,[]);
open();move('pointerdown',25);move('pointermove',290);now+=30;move('pointerup',290);
assert.deepStrictEqual(calls,[]);
open();move('pointerdown',275);move('pointermove',25);now+=250;move('pointerup',25);
assert.deepStrictEqual(calls,[]);
open();move('pointerdown',25);move('pointermove',290);now+=230;move('pointerup',290);
assert.deepStrictEqual(calls,['PAUSE']);assert(nodes.pauseModal.hidden);
// A successful PAUSE pending acknowledgement cannot be sent again.
nodes.recordPause.onclick();assert.deepStrictEqual(calls,['PAUSE']);
controls.clearPending();current.paused=true;
nodes.recordPause.onclick();assert.deepStrictEqual(calls,['PAUSE','CONTINUE']);
// ECONOMY and idle states are denied the pause UI.
current.paused=false;current.recordMode='economy';nodes.recordPause.onclick();
assert(nodes.pauseModal.hidden);assert.deepStrictEqual(calls,['PAUSE','CONTINUE']);
current.recordMode='safe';current.running=false;nodes.recordPause.onclick();
assert(nodes.pauseModal.hidden);assert.deepStrictEqual(calls,['PAUSE','CONTINUE']);
console.log('PASS: SAFE PAUSE requires slow right swipe; CONTINUE single tap; ECONOMY denied');
'''.replace('__HANDLER__',json.dumps(handler))
subprocess.run(['node','-e',node],check=True)
print('PASS: native SAFE-only pause, WAV omission while paused, clock, SAVE allowed')
