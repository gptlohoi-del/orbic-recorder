"""v9.5.7: SAFE recording with confirmed PAUSE and SAVE both require right swipe."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
ui=root/'app/src/main/assets/ui'; native=root/'app/src/main/java/com/example/orbicrecorder'
html=(ui/'index.html').read_text();js=(ui/'app.js').read_text();css=(ui/'app.css').read_text()
activity=(native/'MainActivity.java').read_text();service=(native/'RecorderService.java').read_text()
assert 'current.voice.write(block);' in service
assert 'if (economy)' in service and 'compactor.accept(block,' in service
assert 'case "PAUSE":' in service and 'case "CONTINUE":' in service
assert 'if (safeSession && recording && !armed && !stopping && !paused)' in service
assert 'if (safeSession && recording && !armed && !stopping && paused)' in service
assert 'if (paused) {' in service and 'RecordingArchive.sync(current);' in service
assert 'case "RESUME":' not in service
assert 'case "STOP":' in service
assert 'case "STOP_DISCARD": break;' in service
assert 'builder.addAction(' not in service and '.addAction(' not in service
assert 'if (!"save".equals(choice)) return;' in activity
assert 'sendRecorder("STOP")' in activity
assert 'case "pause":' not in activity and 'case "resume":' not in activity
assert 'id="saveModal"' in html and 'id="discardModal"' not in html
assert 'VUỐT SANG PHẢI ĐỂ LƯU' in html
assert '.swipe-thumb{position:absolute;left:4px' in css
assert '.swipe-fill{position:absolute;left:0;' in css
assert "window.Orbic.finishRecording('save')" in js
assert 'send(\'stop\')' not in js and 'send(\'discard\')' not in js
handler=js[js.index('  function setupSaveSwipe(){'):js.index('  function isThirdTrackSupported')]
node=r'''
const assert=require('assert');
const handler=__HANDLER__;
let now=1000;const sent=[];const events={};const nodes={};
for(const id of ['saveModal','saveBackdrop','saveTrack','saveThumb','saveFill','cancelSave','stop']){
  nodes[id]={hidden:id==='saveModal',style:{},clientWidth:300,offsetWidth:50,disabled:false,
    classList:{add(){},remove(){}},addEventListener(n,fn){events[id+':'+n]=fn;},
    setPointerCapture(){}};
}
const $=id=>{assert(nodes[id],id);return nodes[id];};
const document={body:{classList:{contains:()=>false}}};
const window={Orbic:{finishRecording:mode=>sent.push(mode)}};
const current={running:true,saving:false,starting:false};
const cap=(x,lo,hi)=>Math.max(lo,Math.min(hi,x));
const Date={now:()=>now};const setText=()=>{};
new Function('$','document','window','current','cap','Date','setText',
  'let finishPending=false;let finishRequestedAt=0;\n'+handler)($,document,window,current,cap,Date,setText);
function move(event,x){events['saveThumb:'+event]({pointerId:1,pointerType:'touch',clientX:x,
  button:0,preventDefault(){}});}
nodes.stop.onclick();assert(!nodes.saveModal.hidden);
move('pointerdown',25);now+=230;move('pointerup',25);assert.deepStrictEqual(sent,[]);
move('pointerdown',25);move('pointermove',115);now+=230;move('pointerup',115);assert.deepStrictEqual(sent,[]);
move('pointerdown',25);move('pointermove',275);now+=40;move('pointerup',275);assert.deepStrictEqual(sent,[]);
move('pointerdown',275);move('pointermove',25);now+=250;move('pointerup',25);assert.deepStrictEqual(sent,[]);
move('pointerdown',25);move('pointermove',275);now+=230;move('pointerup',275);
assert.deepStrictEqual(sent,['save']);
console.log('PASS: only deliberate rightward SAVE swipe can finish');
'''
node=node.replace('__HANDLER__',repr(handler))
# Python repr of multiline text is compatible with a JS string literal here.
subprocess.run(['node','-e',node],check=True)
print('PASS: SAFE PAUSE guarded; SAVE swipe; discard rejected')
