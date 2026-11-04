"""Regression checks for the on-demand WAV archive / native bridge.

Node simulates the JS dialog; Android execution still requires an actual device.
"""
from pathlib import Path
import subprocess, json, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'app/src/main/assets/ui'
java = root / 'app/src/main/java/com/example/orbicrecorder'
html = (ui / 'index.html').read_text(encoding='utf-8')
js = (ui / 'app.js').read_text(encoding='utf-8')
lib = (java / 'RecordingLibrary.java').read_text(encoding='utf-8')
activity = (java / 'MainActivity.java').read_text(encoding='utf-8')
player = (java / 'MusicPlayer.java').read_text(encoding='utf-8')
for marker in ('id="openRecordings"', 'id="playNewest"', 'id="recordingsList"',
               'id="recordingSeek"', 'id="recordingPrevious"', 'id="recordingNext"',
               'id="recordingPause"', 'id="recordingStop"'):
    assert marker in html, marker
assert 'recording-library' not in html  # No unused imported JS package
assert 'supportsRecordingLibrary' in activity
assert '@JavascriptInterface public String recordings()' in activity
assert '@JavascriptInterface public void playRecording(String id)' in activity
assert 'RecordingLibrary.find(MainActivity.this, id)' in activity
assert 'Environment.DIRECTORY_MUSIC + "/OrbicRecorder/"' in lib
assert 'listSelectedFolder' in lib and 'listDefault' in lib
assert 'DocumentsContract.buildChildDocumentsUriUsingTree' in lib
assert 'MediaStore.Audio.Media.RELATIVE_PATH' in lib
assert 'new File(base, "OrbicRecorder/" + sub)' in lib
assert 'comparingLong((Item item) -> item.modified).reversed()' in lib
assert 'MAX_VISIBLE = 500' in lib
assert 'void seekTo(int requestedMs)' in player
assert 'voiceReplay ? 0 : c.getInt("fadeIn", 3000)' in activity
assert "replaceChildren" not in js and '?.' not in js and '??' not in js
start = js.index('  // Archive is read from native Android storage only on demand')
end = js.index('  function poll(){', start)
handler = js[start:end]
script = r'''
'use strict';
const assert = require('assert');
const handler = __HANDLER__;
class Element {
 constructor(tag='div'){
  this.tag=tag;this.children=[];this.attrs={};this.style={};this.hidden=true;this.disabled=false;
  this._text='';this.className='';this.classList={toggle(name,on){this[name]=on;}};
 }
 set textContent(v){this._text=String(v); this.children=[];}
 get textContent(){return this._text;}
 append(...children){this.children.push(...children);}
 setAttribute(name,val){this.attrs[name]=val;}
 addEventListener(name,cb){this['listener_'+name]=cb;}
 querySelector(){return this.children[0];}
}
const ids=['recordingsList','recordingsCount','recordingsFolder','recordingNow',
  'recordingTime','recordingPause','recordingStop','recordingPrevious',
  'recordingNext','recordingSeek','recordingsModal','openRecordings','playNewest',
  'refreshRecordings','closeRecordings','recordingsBackdrop'];
const nodes=Object.fromEntries(ids.map(id=>[id,new Element()]));
const $=id=>{assert(nodes[id],id);return nodes[id];};
const document={createElement:tag=>new Element(tag)};
const fixture=[
 {id:'file:old',name:'orbic_20261101_120000_000_p001_voice.wav',modified:1793520000000,size:2048,raw:false},
 {id:'file:new-raw',name:'orbic_20261105_120000_000_p001_raw.wav',modified:1793860000000,size:2048,raw:true},
 {id:'file:new-voice',name:'orbic_20261105_120000_000_p001_voice.wav',modified:1793860000000,size:1024,raw:false}];
const calls=[];
const window={Orbic:{recordings:()=>JSON.stringify(fixture),playRecording:id=>calls.push(['play',id]),
 seekRecording:ms=>calls.push(['seek',ms])}};
const current={activeRecordingId:'',musicStatus:'idle',folder:'Music/OrbicRecorder'};
const setText=(id,value)=>{$(id).textContent=value};
const send=id=>calls.push(['action',id]);
const shortTime=ms=>String(Math.floor((ms||0)/60000)).padStart(2,'0')+':'+
  String(Math.floor((ms||0)/1000)%60).padStart(2,'0');
const runner=new Function('$','document','window','current','setText','send','shortTime',
 handler+'\nreturn {getList:()=>recordings,updateRecordingPlayer};');
const library=runner($,document,window,current,setText,send,shortTime);
// Folder shortcut displays all files in newest-first order but does not auto-play.
nodes.openRecordings.onclick();
assert.strictEqual(calls.length,0);
assert.deepStrictEqual(library.getList().map(x=>x.id),['file:new-voice','file:new-raw','file:old']);
assert.strictEqual(nodes.recordingsList.children.length,3);
assert.strictEqual(nodes.recordingsList.children[0].children[1].children[0].textContent,
 '1. orbic_20261105_120000_000_p001_voice.wav');
// One tap on the latest shortcut opens the library AND plays the newest filtered WAV.
nodes.playNewest.onclick();
assert.deepStrictEqual(calls.pop(),['play','file:new-voice']);
// Preview playback has its own pause, seek, previous/next and stop buttons.
Object.assign(current,{activeRecordingId:'file:new-voice',musicStatus:'playing'});
library.updateRecordingPlayer({activeRecordingId:'file:new-voice',musicStatus:'playing',
 musicTitle:'2026 latest',musicDuration:4000,musicPosition:500,pendingMusic:false,
 pendingResume:false,saving:false});
assert.strictEqual(nodes.recordingNext.disabled,false);
assert.strictEqual(nodes.recordingPrevious.disabled,true);
nodes.recordingNext.onclick(); assert.deepStrictEqual(calls.pop(),['play','file:new-raw']);
nodes.recordingPause.onclick();assert.deepStrictEqual(calls.pop(),['action','musicPause']);
nodes.recordingSeek.value='2200';nodes.recordingSeek.listener_change();
assert.deepStrictEqual(calls.pop(),['seek',2200]);
nodes.recordingStop.onclick();assert.deepStrictEqual(calls.pop(),['action','musicStop']);
console.log('PASS: WAV library newest-first, filtered-before-raw, quick play, controls and seek');
'''.replace('__HANDLER__', json.dumps(handler))
with tempfile.TemporaryDirectory() as temp:
    filename = Path(temp) / 'recordings-ui.js'
    filename.write_text(script, encoding='utf-8')
    subprocess.run(['node', str(filename)], check=True)
print('PASS: archive bridge, Android 9/default/SAF scanning, native permission safety, no clip fades')
