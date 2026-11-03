(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const send = code => { try { window.Orbic.action(code); } catch (e) { console.error(e); } };
  const save = object => { try { window.Orbic.saveSettings(JSON.stringify(object)); } catch (e) { console.error(e); } };
  const setText = (id, value) => { $(id).textContent = String(value ?? ''); };
  const fmt = ms => { const n = Math.floor(Math.max(0, Number(ms) || 0) / 1000); return [Math.floor(n / 3600), Math.floor(n / 60) % 60, n % 60].map(x => String(x).padStart(2, '0')).join(':'); };
  const shortTime = ms => { const n = Math.floor(Math.max(0, Number(ms) || 0) / 1000); return String(Math.floor(n / 60)).padStart(2,'0') + ':' + String(n % 60).padStart(2,'0'); };
  const specs = [
    {key:'fadeIn', label:'Tăng dần âm lượng đầu bài',unit:'ms',default:3000,min:0,max:30000,step:500,group:'music'},
    {key:'fadeOut',label:'Giảm dần âm lượng cuối bài',unit:'ms',default:5000,min:0,max:30000,step:500,group:'music'},
    {key:'pauseStopFade',label:'Giảm âm PAUSE/STOP và tăng âm TIẾP',unit:'ms',default:2000,min:0,max:10000,step:250,hardMin:0,hardMax:15000,group:'music'},
    {key:'musicVolume',label:'Âm lượng phát nhạc',unit:'%',default:80,min:0,max:100,step:5,group:'music'},
    {key:'listenDelay',label:'Trễ trước khi mở micro trở lại',unit:'ms',default:600,min:0,max:5000,step:100,group:'music'},
    {key:'threshold',label:'Ngưỡng phát hiện tiếng động',unit:'dBFS',default:-38,min:-70,max:-10,step:1,hardMin:-80,hardMax:-5,group:'rec'},
    {key:'minSpeech',label:'Thời gian xác nhận có tiếng',unit:'ms',default:180,min:60,max:1000,step:10,hardMin:20,hardMax:2000,group:'rec'},
    {key:'pre',label:'Giữ âm thanh trước tiếng động',unit:'ms',default:1000,min:0,max:5000,step:100,hardMin:0,hardMax:10000,group:'rec'},
    {key:'tail',label:'Giữ âm thanh sau tiếng động',unit:'ms',default:1000,min:100,max:5000,step:100,hardMin:100,hardMax:10000,group:'rec'},
    {key:'warnSeconds',label:'Cảnh báo khi không có tín hiệu',unit:'giây',default:9999,min:10,max:9999,step:1,hardMin:5,hardMax:9999,group:'rec'},
    {key:'uiFont',label:'Tỷ lệ kích thước chữ',unit:'%',default:100,min:75,max:160,step:5,group:'view'},
    {key:'uiOpacity',label:'Độ đậm nền các khung',unit:'%',default:100,min:30,max:100,step:5,group:'view'},
    {key:'waveHeight',label:'Độ cao vùng sóng âm',unit:'px',default:92,min:45,max:135,step:5,hardMin:30,hardMax:180,group:'view'}
  ];
  const data = {};
  let limits = {};
  let current = null;
  let settingsOpened = false;
  let pendingConfirm = null;
  const bars = [];
  const levels = Array(30).fill(0);
  for (let i=0;i<30;i++) { const el=document.createElement('i'); el.className='bar'; $('waveBars').append(el); bars.push(el); }
  function cap(x,lo,hi){return Math.max(lo,Math.min(hi,x));}
  const rule = spec => limits[spec.key] || {min:spec.min,max:spec.max,step:spec.step,default:spec.default};
  function presentNumber(spec) {
    const r=rule(spec);
    const container=document.createElement('div'); container.className='numeric-item'; container.dataset.key=spec.key;
    container.innerHTML=`<div class="numeric-top"><span></span><span class="numeric-value"></span></div>
      <div class="numeric-controls"><button type="button" data-op="minus" aria-label="Giảm">−</button>
      <input type="range" aria-label="Thanh trượt"><button type="button" data-op="plus" aria-label="Tăng">+</button>
      <input type="number" inputmode="decimal" aria-label="Nhập giá trị"></div>
      <div class="numeric-foot"><span></span><button type="button" data-op="reset">Mặc định</button></div>`;
    container.querySelector('.numeric-top>span').textContent=spec.label;
    const range=container.querySelector('input[type=range]');
    const number=container.querySelector('input[type=number]');
    const foot=container.querySelector('.numeric-foot span');
    const valueLabel=container.querySelector('.numeric-value');
    function updateViews(){
      const bound=rule(spec); let value=Number(data[spec.key] ?? bound.default);
      range.min=number.min=bound.min; range.max=number.max=bound.max;
      range.step=number.step=bound.step;
      range.value=number.value=value;
      valueLabel.textContent=value+' '+spec.unit;
      foot.textContent=`MIN ${bound.min} · MAX ${bound.max} · STEP ${bound.step}`;
    }
    function commit(value){
      const bound=rule(spec); if (!Number.isFinite(value)) return updateViews();
      value=cap(Math.round(value),bound.min,bound.max);
      // Native validates against hard safety bounds independently of UI.
      data[spec.key]=value; save({[spec.key]:value}); updateViews(); applyTheme();
    }
    container.querySelector('[data-op=minus]').onclick=()=>commit(Number(data[spec.key])-rule(spec).step);
    container.querySelector('[data-op=plus]').onclick=()=>commit(Number(data[spec.key])+rule(spec).step);
    container.querySelector('[data-op=reset]').onclick=()=>commit(rule(spec).default);
    range.oninput=()=>commit(Number(range.value));
    number.onchange=()=>commit(Number(number.value));
    container.updateViews=updateViews;
    $(''+spec.group+'Numbers').append(container);
  }
  specs.forEach(presentNumber);
  function renderAdvanced() {
    const wrap=$('advancedNumbers'); wrap.replaceChildren();
    for(const spec of specs){
      const r=rule(spec), item=document.createElement('div');item.className='advanced-item';
      const name=document.createElement('strong'); name.textContent=spec.label; item.append(name);
      const fields={};
      for(const field of ['min','max','step','default']){
        const label=document.createElement('label');label.textContent=field.toUpperCase();
        const input=document.createElement('input');input.type='number';input.step='1';input.value=r[field];
        const lo=spec.hardMin??spec.min, hi=spec.hardMax??spec.max;
        input.min=(field==='step'?1:lo);input.max=(field==='step'?hi-lo+1:hi);
        label.append(input);item.append(label);fields[field]=input;
      }
      for(const input of Object.values(fields))input.onchange=()=>{
        const proposed=Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,Number(v.value)]));
        const lo=spec.hardMin??spec.min,hi=spec.hardMax??spec.max;
        if(Object.values(proposed).some(v=>!Number.isInteger(v)) || proposed.min<lo || proposed.max>hi || proposed.min>proposed.max || proposed.step<1 || proposed.step>proposed.max-proposed.min+1 || proposed.default<proposed.min || proposed.default>proposed.max){
          alert(`Giá trị không hợp lệ. Giới hạn an toàn: ${lo} đến ${hi}.`);
          renderAdvanced();return;
        }
        limits[spec.key]=proposed;
        data[spec.key]=cap(Number(data[spec.key]??proposed.default),proposed.min,proposed.max);
        save({numericLimits:limits,[spec.key]:data[spec.key]});
        document.querySelector(`[data-key="${spec.key}"]`).updateViews();
        setText('saveStatus','Đã tự lưu giới hạn');
      };
      wrap.append(item);
    }
  }
  function applyTheme(){
    const v=data.theme||'system';
    if(v==='system')document.documentElement.removeAttribute('data-theme');
    else document.documentElement.dataset.theme=v;
    document.documentElement.style.setProperty('--font-scale',cap(Number(data.uiFont||100),75,160)/100);
    document.documentElement.style.setProperty('--panel-opacity',cap(Number(data.uiOpacity??100),30,100)/100);
    document.documentElement.style.setProperty('--wave-height',cap(Number(data.waveHeight??92),30,180)+'px');
  }
  function openSettings() {
    if (!current)return;
    for(const spec of specs) data[spec.key]=current[spec.key]??spec.default;
    try { limits=JSON.parse(current.numericLimits||'{}')||{}; }catch(e){limits={};}
    data.theme=current.theme||'system';
    specs.forEach(s=>document.querySelector(`[data-key="${s.key}"]`).updateViews());
    $('theme').value=data.theme;
    $('backupRaw').checked=current.backupRaw!==false;
    $('splitMinutes').value=String(current.splitMinutes||15);
    renderAdvanced();settingsOpened=true;$('modal').hidden=false;
  }
  function closeSettings(){settingsOpened=false;$('modal').hidden=true;}
  // Nút Cài đặt cố định trong Vùng Thông Báo; không kéo thả và không làm mờ.
  $('settingsToggle').onclick=openSettings;
  $('closeSettings').onclick=$('closeBottom').onclick=$('modalBackdrop').onclick=closeSettings;
  $('theme').onchange=()=>{data.theme=$('theme').value;save({theme:data.theme});applyTheme();};
  $('backupRaw').onchange=()=>save({backupRaw:$('backupRaw').checked});
  $('splitMinutes').onchange=()=>save({splitMinutes:Number($('splitMinutes').value)});
  $('resetLimits').onclick=()=>{
    limits={};save({numericLimits:{}});
    renderAdvanced();specs.forEach(spec=>document.querySelector(`[data-key="${spec.key}"]`).updateViews());
    setText('saveStatus','Đã khôi phục giới hạn');
  };
  for(const id of ['playLast','chooseFolder','resetFolder','checkOta','installOta','rollback'])
    $(id).onclick=()=>{if(id==='chooseFolder')closeSettings();send(id);};
  for(const id of ['music1','music2','chooseSong1','chooseSong2','chooseOther','musicPause','musicStop'])
    $(id).onclick=()=>{
      if(id==='musicPause') send(current&&current.musicStatus==='paused'?'musicResume':'musicPause');
      else if((id==='music1'||id==='music2') && current &&
          current.musicStatus!=='idle') {
        const song=id==='music1'?'Bài 1':'Bài 2';
        confirmAction('Chuyển sang '+song+'?',
          'Bài đang phát sẽ giảm dần âm lượng rồi mới chuyển bài. Bản ghi vẫn được giữ an toàn.',id);
      } else send(id);
    };
  function changeThreshold(direction){
    if(!current)return;
    const spec=specs.find(s=>s.key==='threshold'),r=rule(spec);
    const previous=Number(current.threshold??data.threshold??spec.default);
    const next=cap(previous+direction*r.step,r.min,r.max);
    data.threshold=current.threshold=next;
    save({threshold:next});
    refreshThreshold(next);
  }
  $('thresholdMinus').onclick=()=>changeThreshold(-1);
  $('thresholdPlus').onclick=()=>changeThreshold(1);
  function refreshThreshold(threshold){
    const t=Number(threshold);
    setText('thresholdValue',t+' dBFS');
    // Recorder uses -60..0 dBFS for its level scale; line and bars share it.
    const height=cap((t+60)/60*100,3,97);
    $('thresholdLine').style.top=(100-height)+'%';
  }
  $('start').onclick=()=>send('start');
  $('pause').onclick=()=>{
    if(current&&current.paused) send('resume');
    else confirmAction('Tạm dừng ghi âm?','Ghi âm sẽ ngừng thủ công và chỉ tiếp tục khi bạn bấm TIẾP TỤC.','pause');
  };
  $('stop').onclick=()=>confirmAction('Dừng và lưu phiên ghi?','Toàn bộ âm thanh đã thu sẽ được hoàn tất và lưu WAV. Phiên lắng nghe sẽ dừng nhưng nhạc đang phát vẫn tiếp tục.','stop');
  function confirmAction(title,message,action){
    setText('confirmTitle',title);setText('confirmText',message);pendingConfirm=action;$('confirm').hidden=false;
  }
  $('confirmCancel').onclick=()=>{$('confirm').hidden=true;pendingConfirm=null;};
  $('confirmYes').onclick=()=>{const action=pendingConfirm;pendingConfirm=null;$('confirm').hidden=true;if(action)send(action);};

  function poll(){
    try {
      const d=JSON.parse(window.Orbic.snapshot());current=d;
      if(!settingsOpened && Object.keys(data).length===0){
        for(const spec of specs)data[spec.key]=d[spec.key]??spec.default;
        data.theme=d.theme||'system';applyTheme();
      }
      const playing=d.musicStatus!=='idle', changing=!!d.pendingMusic||!!d.pendingResume;
      const fading=d.musicStatus==='fadingPause'||d.musicStatus==='fadingStop';
      const stopped=!d.running;
      setText('song1',d.song1Name||'Chưa chọn');setText('song2',d.song2Name||'Chưa chọn');
      setText('musicState',d.pendingResume?'Chờ tắt micro':d.pendingMusic?'Đang chuẩn bị':d.musicStatus==='fadingPause'?'Đang giảm âm để PAUSE':d.musicStatus==='fadingStop'?'Đang giảm âm để STOP':d.musicStatus==='playing'?'Đang phát':d.musicStatus==='paused'?'Tạm dừng':d.musicStatus==='loading'?'Đang mở bài':'Đã dừng');
      setText('nowPlaying',d.musicTitle||'Chưa phát bài nào');
      setText('musicTime',shortTime(d.musicPosition)+' / '+shortTime(d.musicDuration));
      $('progressFill').style.width=(d.musicDuration>0?cap(d.musicPosition/d.musicDuration*100,0,100):0)+'%';
      $('musicPause').disabled=!playing||d.musicStatus==='loading'||fading||changing;
      $('musicPause').textContent=d.musicStatus==='paused'?'▶ TIẾP':'Ⅱ PAUSE';
      $('musicStop').disabled=!playing&&!changing;
      $('music1').disabled=$('music2').disabled=changing||!!d.saving||!!d.starting;
      $('chooseOther').disabled=changing||!!d.saving||!!d.starting;
      $('music1').classList.toggle('active-song',playing && d.activeSlot===1);
      $('music2').classList.toggle('active-song',playing && d.activeSlot===2);
      // Điều khiển ghi âm độc lập với điều khiển nhạc.
      // Android sẽ giữ micro tắt trong suốt thời gian nhạc phát.
      $('start').disabled=!!d.running||!!d.saving||!!d.recovering||!!d.starting;
      $('pause').disabled=stopped||!!d.saving||!!d.starting;
      $('pause').textContent=d.paused?'▶ TIẾP TỤC':'Ⅱ PAUSE';
      $('stop').disabled=stopped||!!d.saving||!!d.starting;
      setText('capture',d.capture||'Sẵn sàng');setText('recordState',d.paused?'PAUSE':d.running?'Đã START':'Sẵn sàng');
      setText('db',(d.db||'—')+' dBFS');
      setText('elapsed',fmt(d.elapsedMs));setText('kept',fmt(d.keptMs));setText('segments',d.segments||0);
      setText('noticeMetrics','Tổng '+fmt(d.elapsedMs)+' · Đã giữ '+fmt(d.keptMs)+' · '+(d.savedParts||0)+' file');
      setText('file',d.file||'Chưa có bản ghi');setText('savedParts',(d.savedParts||0)+' đoạn đã lưu');
      setText('folderLabel',d.folder||'Music/OrbicRecorder');
      setText('settingsFolder',d.folder||'Music/OrbicRecorder');
      setText('error',d.error||'');$('error').hidden=!d.error;
      if(d.error) setText('capture','⚠ '+d.error);
      setText('otaVersion','Giao diện '+(d.otaVersion||'4.0.0'));setText('otaMessage',d.otaMessage||'Chưa kiểm tra');
      $('installOta').disabled=!d.otaAvailable||d.otaStatus!=='available'||d.running||d.saving||playing||changing;
      $('rollback').hidden=!d.canRollback;
      levels.shift();levels.push(d.running&&!d.paused&&!d.musicAudible&&!changing&&!d.musicSuspended?cap(Number(d.level||0),0,100):0);
      bars.forEach((bar,i)=>{let v=levels[i];bar.style.height=Math.max(3,v)+'%';bar.classList.toggle('hot',v>75);});
      if(!settingsOpened) data.threshold=d.threshold??data.threshold;
      refreshThreshold(d.threshold??-38);
      if(!settingsOpened){
        for(const key of ['uiFont','uiOpacity','waveHeight','theme']) data[key]=d[key]??data[key];
        applyTheme();
      }
    }catch(e){setText('error','MẤT KẾT NỐI ANDROID');$('error').hidden=false;}
  }
  poll();setInterval(poll,220);
})();
