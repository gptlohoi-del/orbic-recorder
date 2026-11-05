(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const send = code => { try { window.Orbic.action(code); } catch (e) { console.error(e); } };
  const save = object => { try { window.Orbic.saveSettings(JSON.stringify(object)); } catch (e) { console.error(e); } };
  const coalesce = (a, b) => a == null ? b : a; // Older Android 9 WebView
  const setText = (id, value) => { $(id).textContent = String(coalesce(value, '')); };
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
    {key:'limitDb',label:'Ngưỡng Limiter (mức âm đầu ra)',unit:'dBFS',default:-6,min:-18,max:-1,step:1,hardMin:-30,hardMax:-1,group:'protect'},
    {key:'attackMs',label:'Thời gian giảm mức âm (Attack)',unit:'ms',default:10,min:1,max:100,step:1,hardMin:1,hardMax:250,group:'protect'},
    {key:'releaseMs',label:'Thời gian phục hồi mức âm (Release)',unit:'ms',default:500,min:50,max:2000,step:50,hardMin:20,hardMax:5000,group:'protect'},
    {key:'overloadDb',label:'Mức cảnh báo đầu vào gần quá tải',unit:'dBFS',default:-1,min:-12,max:-1,step:1,hardMin:-12,hardMax:-1,group:'protect'},
    {key:'uiFont',label:'Tỷ lệ kích thước chữ toàn giao diện',unit:'%',default:100,min:75,max:160,step:5,group:'view'},
    {key:'noticeFont',label:'Kích thước chữ vùng THÔNG BÁO',unit:'%',default:100,min:75,max:200,step:5,group:'notice'},
    {key:'uiOpacity',label:'Độ đậm nền các khung',unit:'%',default:100,min:30,max:100,step:5,group:'view'},
    {key:'waveHeight',label:'Độ cao vùng sóng âm',unit:'px',default:92,min:45,max:135,step:5,hardMin:30,hardMax:180,group:'view'}
  ];
  const data = {};
  let limits = {};
  let current = null;
  let settingsOpened = false;
  let pendingConfirm = null;
  let discardPending = false;
  let discardRequestedAt = 0;
  const bars = [];
  const levels = Array(30).fill(0);
  const waveStyles = [
    {key:'bars',name:'Cột đứng',description:'Đơn giản, rõ mức âm'},
    {key:'mirror',name:'Cột đối xứng',description:'Cân đối như máy ghi âm'},
    {key:'line',name:'Đường sóng mềm',description:'Nét liền, chuyển động mượt'},
    {key:'dots',name:'Chấm nhịp',description:'Hiện đại, gọn mắt'}
  ];
  let activeWaveStyle = 'line';
  for (let i=0;i<30;i++) { const el=document.createElement('i'); el.className='bar'; $('waveBars').append(el); bars.push(el); }
  function cap(x,lo,hi){return Math.max(lo,Math.min(hi,x));}
  const svgNS = 'http://www.w3.org/2000/svg';
  const previewLevels = [8,16,32,57,36,67,89,61,45,28,53,76,96,55,37,66,46,25,14,43,72,38,25,9];
  function wavePath(values, width, height){
    const step=width/(values.length-1);
    let line=`M0,${(height-values[0]*height/100).toFixed(2)}`;
    for(let i=0;i<values.length-1;i++){
      const x=(i*step).toFixed(2), y=(height-values[i]*height/100).toFixed(2);
      const mx=((i+.5)*step).toFixed(2);
      const my=(height-(values[i]+values[i+1])*height/200).toFixed(2);
      line+=` Q${x},${y} ${mx},${my}`;
    }
    line+=` L${width},${(height-values[values.length-1]*height/100).toFixed(2)}`;
    return {line,area:line+` L${width},${height} L0,${height} Z`};
  }
  function svgElement(type,attrs){
    const item=document.createElementNS(svgNS,type);
    for(const [k,v] of Object.entries(attrs))item.setAttribute(k,String(v));
    return item;
  }
  function renderStylePreviews(){
    const picker=$('waveStylePicker');
    for(const style of waveStyles){
      const label=document.createElement('label');label.className='wave-style-option';
      const top=document.createElement('span');top.className='wave-style-top';
      const title=document.createElement('span');title.textContent=style.name;
      const input=document.createElement('input');input.type='radio';input.name='waveStyle';input.value=style.key;
      top.append(title,input);
      const svg=svgElement('svg',{viewBox:'0 0 180 44',preserveAspectRatio:'none','aria-hidden':'true'});
      if(style.key==='line'){
        const path=wavePath(previewLevels,180,44);
        svg.append(svgElement('path',{d:path.area,fill:'#2bcb9a',opacity:'.22'}),
          svgElement('path',{d:path.line,stroke:'#3aebc1','stroke-width':2,fill:'none'}));
      }else{
        const column=180/previewLevels.length;
        previewLevels.forEach((value,i)=>{
          const h=Math.max(3,value*.4),x=i*column+column*.17;
          if(style.key==='dots'){
            for(let y=44-3;y>44-h;y-=6)
              svg.append(svgElement('circle',{cx:x+column*.33,cy:y,r:1.7,fill:'#3aebc1'}));
          }else svg.append(svgElement('rect',{x,y:style.key==='mirror'?22-h/2:44-h,width:column*.65,height:h,rx:1.5,fill:'#3aebc1'}));
        });
      }
      const description=document.createElement('span');description.className='wave-style-description';description.textContent=style.description;
      label.append(top,svg,description);picker.append(label);
      input.onchange=()=>{
        if(!input.checked)return;
        data.waveStyle=style.key;
        save({waveStyle:style.key});
        applyWaveStyle(style.key);
      };
    }
  }
  function applyWaveStyle(choice){
    activeWaveStyle=waveStyles.some(s=>s.key===choice)?choice:'line';
    $('waveform').dataset.style=activeWaveStyle;
    for(const input of document.querySelectorAll('input[name="waveStyle"]')){
      input.checked=input.value===activeWaveStyle;
      input.closest('.wave-style-option').classList.toggle('selected',input.checked);
    }
    renderWaveform();
    refreshThreshold(coalesce(current && current.threshold, coalesce(data.threshold, -38)));
  }
  function renderWaveform(){
    if(activeWaveStyle==='line'){
      const path=wavePath(levels.map(v=>cap(Number(v)||0,0,100)),300,100);
      $('waveLine').setAttribute('d',path.line);
      $('waveArea').setAttribute('d',path.area);
    }else{
      bars.forEach((bar,i)=>{
        const v=cap(Number(levels[i])||0,0,100);
        bar.style.height=Math.max(activeWaveStyle==='dots'?3:4,v)+'%';
        bar.classList.toggle('hot',v>75);
      });
    }
  }
  renderStylePreviews();
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
      const bound=rule(spec); let value=Number(coalesce(data[spec.key], bound.default));
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
    const wrap=$('advancedNumbers'); while(wrap.firstChild)wrap.removeChild(wrap.firstChild);
    for(const spec of specs){
      const r=rule(spec), item=document.createElement('div');item.className='advanced-item';
      const name=document.createElement('strong'); name.textContent=spec.label; item.append(name);
      const fields={};
      for(const field of ['min','max','step','default']){
        const label=document.createElement('label');label.textContent=field.toUpperCase();
        const input=document.createElement('input');input.type='number';input.step='1';input.value=r[field];
        const lo=coalesce(spec.hardMin,spec.min), hi=coalesce(spec.hardMax,spec.max);
        input.min=(field==='step'?1:lo);input.max=(field==='step'?hi-lo+1:hi);
        label.append(input);item.append(label);fields[field]=input;
      }
      for(const input of Object.values(fields))input.onchange=()=>{
        const proposed={}; for(const k of Object.keys(fields))proposed[k]=Number(fields[k].value);
        const lo=coalesce(spec.hardMin,spec.min),hi=coalesce(spec.hardMax,spec.max);
        if(Object.values(proposed).some(v=>!Number.isInteger(v)) || proposed.min<lo || proposed.max>hi || proposed.min>proposed.max || proposed.step<1 || proposed.step>proposed.max-proposed.min+1 || proposed.default<proposed.min || proposed.default>proposed.max){
          alert(`Giá trị không hợp lệ. Giới hạn an toàn: ${lo} đến ${hi}.`);
          renderAdvanced();return;
        }
        limits[spec.key]=proposed;
        data[spec.key]=cap(Number(coalesce(data[spec.key],proposed.default)),proposed.min,proposed.max);
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
    document.documentElement.style.setProperty('--font-scale',cap(Number(coalesce(data.uiFont,100)),75,160)/100);
    const noticeScale=cap(Number(coalesce(data.noticeFont,100)),75,200)/100;
    document.documentElement.style.setProperty('--notice-font-size',(10*noticeScale)+'px');
    document.documentElement.style.setProperty('--notice-small-font-size',(9*noticeScale)+'px');
    document.documentElement.style.setProperty('--panel-opacity',cap(Number(coalesce(data.uiOpacity,100)),30,100)/100);
    document.documentElement.style.setProperty('--wave-height',cap(Number(coalesce(data.waveHeight,92)),30,180)+'px');
  }
  // Old native APKs do not know thresholdEnabled; keep the UI visible but
  // disabled so an OTA-only install never promises continuous recording.
  function updateThresholdSupport(d){
    const supported=!!d && typeof d.thresholdEnabled==='boolean';
    const enabled=!supported || d.thresholdEnabled;
    $('thresholdEnabled').disabled=!supported;
    $('thresholdEnabled').checked=enabled;
    setText('thresholdSupport',supported
      ? 'Bỏ chọn để ghi liên tục, kể cả âm thanh rất nhỏ và khoảng lặng. Có hiệu lực trong khoảng 100 ms; PAUSE và tắt micro khi phát nhạc vẫn hoạt động.'
      : 'Cần cài APK v9.4.2 trở lên một lần để tắt ngưỡng thực sự. OTA chỉ cập nhật giao diện nên không thể thay thuật toán của APK cũ.');
    $('thresholdMinus').disabled=$('thresholdPlus').disabled=!enabled;
    $('waveform').classList.toggle('threshold-disabled',!enabled);
    const numeric=document.querySelector('[data-key="threshold"]');
    if(numeric){
      numeric.classList.toggle('threshold-disabled',!enabled);
      for(const control of numeric.querySelectorAll('input,button'))control.disabled=!enabled;
    }
    return enabled;
  }
  function openSettings() {
    if (!current)return;
    for(const spec of specs) data[spec.key]=coalesce(current[spec.key],spec.default);
    try { limits=JSON.parse(current.numericLimits||'{}')||{}; }catch(e){limits={};}
    data.theme=current.theme||'system';
    data.waveStyle=current.waveStyle||data.waveStyle||'line';
    applyWaveStyle(data.waveStyle);
    specs.forEach(s=>document.querySelector(`[data-key="${s.key}"]`).updateViews());
    $('theme').value=data.theme;
    const supportsAutoRotate=typeof current.autoRotateEnabled==='boolean';
    $('autoRotateEnabled').disabled=!supportsAutoRotate;
    $('autoRotateEnabled').checked=supportsAutoRotate?current.autoRotateEnabled:false;
    setText('autoRotateHelp',supportsAutoRotate
      ? 'Mặc định TẮT. Bật để xoay theo cảm biến điện thoại; khi tắt ứng dụng khóa màn hình theo chiều dọc. Hai nút XOAY 90° / 180° luôn hoạt động độc lập.'
      : 'Cần cài APK mới để thay đổi tự xoay theo cảm biến. Chỉ cập nhật OTA giao diện là chưa đủ.');
    $('backupRaw').checked=current.backupRaw===true;
    updateThresholdSupport(current);
    for(const key of ['agcEnabled','limiterEnabled','overloadWarn']) $(key).checked=current[key]!==false;
    setText('agcStatus',current.agcStatus||'Chưa khởi động micro');
    $('splitMinutes').value=String(coalesce(current.splitMinutes,60));
    const supportsAutoOta=typeof current.autoOtaEnabled==='boolean';
    $('autoOtaEnabled').disabled=!supportsAutoOta;
    $('autoOtaEnabled').checked=supportsAutoOta?current.autoOtaEnabled:false;
    setText('autoOtaHelp',supportsAutoOta
      ? 'Mặc định BẬT. Tự kiểm tra khi mở ứng dụng; chỉ cập nhật khi không ghi âm, lưu WAV hoặc phát nhạc. Nếu bận, OTA sẽ chờ đến lúc rảnh.'
      : 'Cần cài APK mới có hỗ trợ tự động OTA. Chỉ cập nhật giao diện không thể thêm chức năng Android này.');
    renderAdvanced();settingsOpened=true;$('modal').hidden=false;
  }
  function closeSettings(){settingsOpened=false;$('modal').hidden=true;}
  // Nút Cài đặt cố định trong Vùng Thông Báo; không kéo thả và không làm mờ.
  $('settingsToggle').onclick=openSettings;
  $('closeSettings').onclick=$('closeBottom').onclick=$('modalBackdrop').onclick=closeSettings;
  $('theme').onchange=()=>{data.theme=$('theme').value;save({theme:data.theme});applyTheme();};
  $('backupRaw').onchange=()=>save({backupRaw:$('backupRaw').checked});
  $('autoRotateEnabled').onchange=()=>{
    if(!current || typeof current.autoRotateEnabled!=='boolean')return;
    current.autoRotateEnabled=$('autoRotateEnabled').checked;
    save({autoRotateEnabled:current.autoRotateEnabled});
    setText('saveStatus',current.autoRotateEnabled?'Đã bật tự xoay theo điện thoại':'Đã khóa tự xoay (vẫn xoay thủ công được)');
  };
  $('autoOtaEnabled').onchange=()=>{
    if(!current || typeof current.autoOtaEnabled!=='boolean')return;
    current.autoOtaEnabled=$('autoOtaEnabled').checked;
    save({autoOtaEnabled:current.autoOtaEnabled});
    setText('saveStatus',current.autoOtaEnabled?'Đã bật tự động cập nhật OTA':'Đã tắt tự động cập nhật OTA');
  };
  $('thresholdEnabled').onchange=()=>{
    if(!current || typeof current.thresholdEnabled!=='boolean')return;
    const enabled=$('thresholdEnabled').checked;
    current.thresholdEnabled=enabled;
    save({thresholdEnabled:enabled});
    updateThresholdSupport(current);
    refreshThreshold(coalesce(current.threshold,-38));
    setText('saveStatus',enabled?'Đã bật lọc ngưỡng':'Đã tắt ngưỡng – ghi liên tục');
  };
  for (const key of ['agcEnabled','limiterEnabled','overloadWarn']) {
    $(key).onchange=()=>save({[key]:$(key).checked});
  }
  $('splitMinutes').onchange=()=>save({splitMinutes:Number($('splitMinutes').value)});
  $('resetLimits').onclick=()=>{
    limits={};save({numericLimits:{}});
    renderAdvanced();specs.forEach(spec=>document.querySelector(`[data-key="${spec.key}"]`).updateViews());
    setText('saveStatus','Đã khôi phục giới hạn');
  };
  for(const id of ['playLast','chooseFolder','resetFolder','checkOta','installOta','rollback'])
    $(id).onclick=()=>{if(id==='chooseFolder')closeSettings();send(id);};
  // v9.4.1+ native rotates the entire WebView with reliable touch mapping.
  // In original v9.4 the Android orientation request may be silently ignored.
  // OTA can patch that older APK with a CSS-only *app UI* fallback; NEVER call
  // its broken native orientation action at the same time (would double-flip).
  $('rotate90').onclick=()=>{
    // 90° depends on swapped native WebView dimensions; do not emulate it with
    // an ordinary CSS transform on old APKs (would crop/touch-misalign).
    if(current && current.supportsRotation90===true)send('rotate90');
  };
  $('rotate180').onclick=()=>{
    let hasFixedNative=false;
    try {hasFixedNative=!!window.Orbic &&
        typeof window.Orbic.supportsReliableRotation==='function' &&
        window.Orbic.supportsReliableRotation()===true;
    } catch(e) {hasFixedNative=false;}
    if(hasFixedNative){send('rotate180');return;}
    const turned=document.body.classList.toggle('software-rotated-180');
    $('rotate180').textContent=turned?'↶ TRỞ LẠI':'⤾ XOAY 180°';
    $('rotate180').setAttribute('aria-pressed',String(turned));
  };
  for(const id of ['music1','music2','music3','chooseSong1','chooseSong2','chooseSong3','chooseOther','musicPause','musicStop'])
    $(id).onclick=()=>{
      if(id==='musicPause') send(current&&current.musicStatus==='paused'?'musicResume':'musicPause');
      else if((id==='music1'||id==='music2'||id==='music3') && current &&
          current.musicStatus!=='idle') {
        const song=id==='music1'?'Bài 1':id==='music2'?'Bài 2':'Bài 3';
        confirmAction('Chuyển sang '+song+'?',
          'Bài đang phát sẽ giảm dần âm lượng rồi mới chuyển bài. Bản ghi vẫn được giữ an toàn.',id);
      } else send(id);
    };
  function changeThreshold(direction){
    if(!current || current.thresholdEnabled===false)return;
    const spec=specs.find(s=>s.key==='threshold'),r=rule(spec);
    const previous=Number(coalesce(current.threshold,coalesce(data.threshold,spec.default)));
    const next=cap(previous+direction*r.step,r.min,r.max);
    data.threshold=current.threshold=next;
    save({threshold:next});
    refreshThreshold(next);
  }
  $('thresholdMinus').onclick=()=>changeThreshold(-1);
  $('thresholdPlus').onclick=()=>changeThreshold(1);
  function refreshThreshold(threshold){
    const t=Number(threshold);
    setText('thresholdValue',current && current.thresholdEnabled===false?'ĐÃ TẮT':t+' dBFS');
    // Recorder uses -60..0 dBFS for its level scale; line and bars share it.
    const height=cap((t+60)/60*100,3,97);
    $('thresholdLine').style.top=(activeWaveStyle==='mirror'?(100-height)/2:100-height)+'%';
    $('thresholdMirrorLine').style.top=((100+height)/2)+'%';
  }
  $('start').onclick=()=>send('start');
  // $('pause').onclick=()=>{
  //   if(current&&current.paused) send('resume');
  //   else confirmAction('Tạm dừng ghi âm?','Ghi âm sẽ ngừng thủ công và chỉ tiếp tục khi bạn bấm TIẾP TỤC.','pause');
  // };

  $('pause').onclick=()=>{
    if(current && current.paused) {
      send('resume');
    } else {
      send('pause');
    }
  };
  //$('stop').onclick=()=>confirmAction('Dừng và lưu phiên ghi?','Toàn bộ âm thanh đã thu sẽ được hoàn tất và lưu WAV. Phiên lắng nghe sẽ dừng nhưng nhạc đang phát vẫn tiếp tục.','stop');
  $('stop').onclick=()=>send('stop');
  // Confirmation requires a real pointer drag across the track.
  // A tap or a partial swipe can NEVER call the native discard action.
  const swipeTrack=$('discardTrack'), swipeThumb=$('discardThumb'), swipeFill=$('swipeFill');
  let swipePointer=null, swipeStart=0, swipeDistance=0, swipeStartedAt=0;
  function resetSwipe(){
    swipePointer=null; swipeDistance=0;
    swipeThumb.style.transform='translateX(0px)';
    swipeFill.style.width='0px';
    swipeTrack.classList.remove('dragging');
  }
  function closeDiscard(){ $('discardModal').hidden=true;resetSwipe(); }
  $('discard').onclick=()=>{
    if(!current||!current.running||current.saving||current.starting||
        discardPending||!isDiscardSupported(current))return;
    resetSwipe();$('discardModal').hidden=false;
  };
  $('cancelDiscard').onclick=$('discardBackdrop').onclick=closeDiscard;
  function maxSwipe(){return Math.max(0,swipeTrack.clientWidth-swipeThumb.offsetWidth-8);}
  swipeThumb.addEventListener('pointerdown',e=>{
    if(swipePointer!==null||$('discardModal').hidden)return;
    if(e.pointerType==='mouse' && e.button!==0)return;
    swipePointer=e.pointerId;swipeStart=e.clientX;swipeDistance=0;
    swipeStartedAt=Date.now();
    swipeTrack.classList.add('dragging');
    swipeThumb.setPointerCapture(e.pointerId);
    e.preventDefault();
  });
  swipeThumb.addEventListener('pointermove',e=>{
    if(e.pointerId!==swipePointer)return;
    // A CSS 180-degree rotation on the original APK reverses the visual
    // horizontal direction but clientX still uses physical viewport axes.
    // In the newer Android APK the WebView handles touch coordinates itself.
    const direction=document.body.classList.contains('software-rotated-180')?-1:1;
    swipeDistance=cap((e.clientX-swipeStart)*direction,0,maxSwipe());
    swipeThumb.style.transform='translateX('+swipeDistance+'px)';
    swipeFill.style.width=(swipeDistance+swipeThumb.offsetWidth/2)+'px';
    e.preventDefault();
  });
  swipeThumb.addEventListener('pointerup',e=>{
    if(e.pointerId!==swipePointer)return;
    // Full purposeful swipe (not a tap or accidental rapid touch).
    const completed=maxSwipe()>0 && swipeDistance>=maxSwipe()*.92 &&
        Date.now()-swipeStartedAt>=180;
    resetSwipe();
    if(completed && current && current.running && !current.saving &&
        !current.starting && !discardPending && isDiscardSupported(current)){
      $('discardModal').hidden=true;
      discardPending=true;
      discardRequestedAt=Date.now();
      send('discard');
    }
  });
  swipeThumb.addEventListener('pointercancel',resetSwipe);
  function isThirdTrackSupported(d){
    // v9.4.2 in the user's earlier source already implemented song 3.
    return !!d && (d.supportsThirdTrack===true ||
        /^ORBIC RECORDER v9\.4\.2(?:$|\.)/.test(d.nativeLabel||''));
  }
  function isDiscardSupported(d){
    return !!d && (d.supportsSwipeDiscard===true ||
        /^ORBIC RECORDER v9\.4\.2(?:$|\.)/.test(d.nativeLabel||''));
  }
  function confirmAction(title,message,action){
    setText('confirmTitle',title);setText('confirmText',message);pendingConfirm=action;$('confirm').hidden=false;
  }
  $('confirmCancel').onclick=()=>{$('confirm').hidden=true;pendingConfirm=null;};
  $('confirmYes').onclick=()=>{const action=pendingConfirm;pendingConfirm=null;$('confirm').hidden=true;if(action)send(action);};

  // Archive is read from native Android storage only on demand, never on each
  // 220-ms waveform poll. Newest WAV is first, even for a custom SAF folder.
  let recordings=[];
  let recordingRows=[];
  let highlightedRecordingId=null;
  let lastArchivedFile='';
  let wantedRecordingId='';
  let movingRecordingSeek=false;
  function recordingLibrarySupported(){
    return typeof window.Orbic!=='undefined' &&
      typeof window.Orbic.recordings==='function' &&
      typeof window.Orbic.playRecording==='function';
  }
  function dateLabel(milliseconds){
    if(!(Number(milliseconds)>0))return 'Không rõ ngày lưu';
    try{return new Date(Number(milliseconds)).toLocaleString('vi-VN',{hour12:false});}
    catch(e){return 'Không rõ ngày lưu';}
  }
  function fileSizeLabel(bytes){
    const n=Number(bytes);
    if(!Number.isFinite(n)||n<0)return '';
    return n>=1048576?(n/1048576).toFixed(1)+' MB':Math.max(1,Math.ceil(n/1024))+' KB';
  }
  function playRecordingItem(item){
    if(!item||!recordingLibrarySupported())return;
    wantedRecordingId=item.id;
    try {window.Orbic.playRecording(item.id);} catch(e){
      setText('recordingNow','Không thể mở bản ghi: '+e.message);
    }
  }
  function markActiveRecording(id){
    if(id===highlightedRecordingId)return;
    highlightedRecordingId=id;
    recordingRows.forEach((row,index)=>{
      const playing=recordings[index].id===id;
      row.classList.toggle('playing-recording',playing);
      row.querySelector('button').textContent=playing?'♫':'▶';
    });
  }
  function loadRecordingList(playNewest){
    const list=$('recordingsList');
    list.textContent="";recordingRows=[];highlightedRecordingId=null;
    if(!recordingLibrarySupported()){
      recordings=[];
      setText('recordingsCount','Cần cài bản APK mới để xem bản ghi');
      const empty=document.createElement('p');empty.className='recordings-empty';
      empty.textContent='Phiên bản Android hiện tại chưa hỗ trợ duyệt WAV. Chỉ cập nhật OTA giao diện là chưa đủ.';
      list.append(empty);return;
    }
    try {
      const raw=JSON.parse(window.Orbic.recordings());
      recordings=Array.isArray(raw)?raw.filter(x=>x&&typeof x.id==='string'&&typeof x.name==='string'):[];
      // Sorting here is a second safeguard for native providers that ignore sort order.
      recordings.sort((a,b)=>(Number(b.modified)||0)-(Number(a.modified)||0) ||
          Number(!!a.raw)-Number(!!b.raw) || String(b.name).localeCompare(String(a.name)));
      setText('recordingsCount',recordings.length+' file WAV · Mới nhất ở đầu danh sách');
      if(!recordings.length){
        const empty=document.createElement('p');empty.className='recordings-empty';
        empty.textContent='Chưa có file WAV đã lưu. Sau khi bấm DỪNG & LƯU, hãy mở lại thư mục hoặc bấm ↻ để làm mới.';
        list.append(empty);
      }
      recordings.forEach((item,index)=>{
        const row=document.createElement('div');row.className='recording-item';
        const play=document.createElement('button');play.type='button';play.className='btn outline';
        play.textContent='▶';play.setAttribute('aria-label','Nghe '+item.name);
        play.onclick=()=>{
          if(current&&current.activeRecordingId===item.id&&current.musicStatus==='paused')send('musicResume');
          else if(current&&current.activeRecordingId===item.id&&current.musicStatus==='playing')send('musicPause');
          else playRecordingItem(item);
        };
        const details=document.createElement('button');details.type='button';details.className='recording-file-details';
        details.setAttribute('aria-label','Nghe bản ghi '+item.name);
        details.title='Chạm để nghe '+item.name;
        details.onclick=play.onclick;
        const name=document.createElement('strong');name.textContent=(index+1)+'. '+item.name;
        const meta=document.createElement('small');
        meta.textContent=dateLabel(item.modified)+' · '+(item.raw?'WAV gốc':'WAV đã lọc')+
          (fileSizeLabel(item.size)?' · '+fileSizeLabel(item.size):'');
        details.append(name,meta);row.append(play,details);
        list.append(row);recordingRows.push(row);
      });
      markActiveRecording(current?current.activeRecordingId:'');
      if(playNewest&&recordings.length)playRecordingItem(recordings[0]);
    }catch(e){
      recordings=[];
      setText('recordingsCount','Không đọc được thư mục bản ghi');
      const empty=document.createElement('p');empty.className='recordings-empty';
      empty.textContent='Không thể tải danh sách WAV. Kiểm tra quyền truy cập thư mục, sau đó thử bấm ↻.';
      list.append(empty);
    }
  }
  function showRecordings(playNewest){
    $('recordingsModal').hidden=false;
    setText('recordingsFolder',current&&current.folder||'Music/OrbicRecorder');
    loadRecordingList(playNewest);
  }
  function closeRecordings(){
    $('recordingsModal').hidden=true;
    // Closing the library must not interrupt a recording or audio playback.
  }
  $('openRecordings').onclick=()=>showRecordings(false);
  $('playNewest').onclick=()=>showRecordings(true);
  $('refreshRecordings').onclick=()=>loadRecordingList(false);
  $('closeRecordings').onclick=$('recordingsBackdrop').onclick=closeRecordings;
  function navigateRecording(delta){
    const id=(current&&current.activeRecordingId)||wantedRecordingId;
    const index=recordings.findIndex(x=>x.id===id);
    if(index<0)return;
    const next=recordings[index+delta];
    if(next)playRecordingItem(next);
  }
  $('recordingPrevious').onclick=()=>navigateRecording(-1);
  $('recordingNext').onclick=()=>navigateRecording(1);
  $('recordingPause').onclick=()=>{
    if(!current||!current.activeRecordingId)return;
    send(current.musicStatus==='paused'?'musicResume':'musicPause');
  };
  $('recordingStop').onclick=()=>send('musicStop');
  $('recordingSeek').addEventListener('input',()=>{movingRecordingSeek=true;});
  $('recordingSeek').addEventListener('change',()=>{
    if(current&&current.activeRecordingId&&typeof window.Orbic.seekRecording==='function')
      try{window.Orbic.seekRecording(Number($('recordingSeek').value));}catch(e){console.error(e);}
    movingRecordingSeek=false;
  });
  function updateRecordingPlayer(d){
    if($('recordingsModal').hidden)return;
    const id=d.activeRecordingId||'';
    const playing=!!id&&d.musicStatus!=='idle';
    const index=recordings.findIndex(x=>x.id===id);
    markActiveRecording(id);
    setText('recordingNow',playing?(d.musicTitle||'Đang mở bản ghi'):'Chạm vào tên file hoặc ▶ để nghe lại');
    setText('recordingTime',shortTime(playing?d.musicPosition:0)+' / '+shortTime(playing?d.musicDuration:0));
    const pause=$('recordingPause');
    pause.disabled=!playing||d.musicStatus==='loading'||d.musicStatus==='fadingPause'||
      d.musicStatus==='fadingStop'||d.pendingMusic||d.pendingResume;
    pause.textContent=d.musicStatus==='paused'?'▶ TIẾP':'Ⅱ PAUSE';
    $('recordingStop').disabled=!playing&&!d.pendingMusic;
    $('recordingPrevious').disabled=index<=0||d.pendingMusic||d.pendingResume||d.saving;
    $('recordingNext').disabled=index<0||index>=recordings.length-1||d.pendingMusic||d.pendingResume||d.saving;
    const seek=$('recordingSeek');
    seek.disabled=!playing||d.musicDuration<=0||d.musicStatus==='loading'||
      d.musicStatus==='fadingPause'||d.musicStatus==='fadingStop';
    seek.max=Math.max(1,Number(d.musicDuration)||1);
    if(!movingRecordingSeek)seek.value=Math.max(0,Number(d.musicPosition)||0);
  }

  function poll(){
    try {
      const d=JSON.parse(window.Orbic.snapshot());current=d;
      if(!settingsOpened && Object.keys(data).length===0){
        for(const spec of specs)data[spec.key]=coalesce(d[spec.key],spec.default);
        data.theme=d.theme||'system';data.waveStyle=d.waveStyle||'line';applyTheme();applyWaveStyle(data.waveStyle);
      }
      const playing=d.musicStatus!=='idle', changing=!!d.pendingMusic||!!d.pendingResume;
      const fading=d.musicStatus==='fadingPause'||d.musicStatus==='fadingStop';
      const stopped=!d.running;
      const hasSong3=isThirdTrackSupported(d),hasDiscard=isDiscardSupported(d);
      if(discardPending && (stopped || d.saving || d.error ||
          Date.now()-discardRequestedAt>8000)) discardPending=false;
      const hasArchive=d.supportsRecordingLibrary===true;
      setText('featureCompatibility',hasSong3&&hasDiscard&&hasArchive
        ? 'APK hỗ trợ phát nhạc, xoay thủ công, hủy phiên ghi và nghe lại WAV.'
        : 'Cần cài APK mới để dùng đầy đủ BÀI 3, HỦY và THƯ VIỆN GHI ÂM. Chỉ cập nhật giao diện OTA là chưa đủ.');
      $('openRecordings').disabled=$('playNewest').disabled=!hasArchive;
      $('music3').textContent=hasSong3?'▶ BÀI 3':'BÀI 3 · APK MỚI';
      $('discard').textContent=hasDiscard?'✕ DỪNG & KHÔNG LƯU':'✕ CẦN APK MỚI ĐỂ HỦY';
      // UI state is persisted by the native APK; older OTA-only APKs use the
      // button's own CSS fallback state and must not have it overwritten.
      const hasQuarterTurn=d.supportsRotation90===true && typeof d.uiRotationDegrees==='number';
      $('rotate90').disabled=!hasQuarterTurn;
      $('rotate90').title=hasQuarterTurn?'Xoay thêm 90° theo chiều kim đồng hồ':'Cần cài APK mới để xoay 90°';
      if(hasQuarterTurn){
        const degrees=((d.uiRotationDegrees%360)+360)%360;
        $('rotate90').setAttribute('aria-label','Xoay thêm 90 độ; hiện tại '+degrees+' độ');
        $('rotate180').textContent=degrees===180?'↶ TRỞ LẠI':'⤾ XOAY 180°';
        $('rotate180').setAttribute('aria-pressed',String(degrees===180));
      } else if(typeof d.uiRotated180==='boolean'){
        $('rotate180').textContent=d.uiRotated180?'↶ TRỞ LẠI':'⤾ XOAY 180°';
        $('rotate180').setAttribute('aria-pressed',String(d.uiRotated180));
      }
      setText('song1',d.song1Name||'Chưa chọn');setText('song2',d.song2Name||'Chưa chọn');setText('song3',d.song3Name||'Chưa chọn');
      setText('musicState',d.pendingResume?'Chờ tắt micro':d.pendingMusic?'Đang chuẩn bị':d.musicStatus==='fadingPause'?'Đang giảm âm để PAUSE':d.musicStatus==='fadingStop'?'Đang giảm âm để STOP':d.musicStatus==='playing'?'Đang phát':d.musicStatus==='paused'?'Tạm dừng':d.musicStatus==='loading'?'Đang mở bài':'Đã dừng');
      setText('nowPlaying',d.musicTitle||'Chưa phát bài nào');
      setText('musicTime',shortTime(d.musicPosition)+' / '+shortTime(d.musicDuration));
      $('progressFill').style.width=(d.musicDuration>0?cap(d.musicPosition/d.musicDuration*100,0,100):0)+'%';
      $('musicPause').disabled=!playing||d.musicStatus==='loading'||fading||changing;
      $('musicPause').textContent=d.musicStatus==='paused'?'▶ TIẾP':'Ⅱ PAUSE';
      $('musicStop').disabled=!playing&&!changing;
      $('music1').disabled=$('music2').disabled=changing||!!d.saving||!!d.starting;
      $('music3').disabled=!hasSong3||changing||!!d.saving||!!d.starting;
      $('chooseSong3').disabled=!hasSong3||changing||!!d.saving||!!d.starting;
      $('chooseOther').disabled=changing||!!d.saving||!!d.starting;
      $('music1').classList.toggle('active-song',playing && d.activeSlot===1);
      $('music2').classList.toggle('active-song',playing && d.activeSlot===2);
      $('music3').classList.toggle('active-song',playing && d.activeSlot===3);
      // Điều khiển ghi âm độc lập với điều khiển nhạc.
      // Android sẽ giữ micro tắt trong suốt thời gian nhạc phát.
      $('start').disabled=!!d.running||!!d.saving||!!d.recovering||!!d.starting;
      $('pause').disabled=stopped||!!d.saving||!!d.starting;
      $('pause').textContent=d.paused?'▶ TIẾP TỤC':'Ⅱ PAUSE';
      $('stop').disabled=stopped||!!d.saving||!!d.starting;
      $('discard').disabled=!hasDiscard||stopped||!!d.saving||!!d.starting||discardPending;
      if(!stopped && d.saving && !$('discardModal').hidden)closeDiscard();
      if(stopped && !$('discardModal').hidden)closeDiscard();
      // Native APK v9.1+ provides a device-level AGC availability flag even before START.
      setText('agcSupport',typeof d.agcSupported==='boolean'
        ? (d.agcSupported?'CÓ HỖ TRỢ (API Android)':'KHÔNG HỖ TRỢ (API Android)')
        : 'CẦN CẬP NHẬT APK ĐỂ KIỂM TRA');
      setText('agcStatus',d.agcStatus||'Chưa khởi động micro');
      setText('capture',d.capture||'Sẵn sàng');setText('recordState',d.paused?'PAUSE':d.running?'Đã START':'Sẵn sàng');
      setText('db',(d.db||'—')+' dBFS');
      setText('elapsed',fmt(d.elapsedMs));setText('kept',fmt(d.keptMs));setText('segments',d.segments||0);
      setText('noticeMetrics','Tổng '+fmt(d.elapsedMs)+' · Đã giữ '+fmt(d.keptMs)+' · '+(d.savedParts||0)+' file');
      setText('file',d.file||'Chưa có bản ghi');setText('savedParts',(d.savedParts||0)+' đoạn đã lưu');
      if(d.file && d.file!==lastArchivedFile){
        // A freshly exported WAV becomes available after the native file field changes.
        if(lastArchivedFile && !$('recordingsModal').hidden)loadRecordingList(false);
        lastArchivedFile=d.file;
      }
      setText('folderLabel',d.folder||'Music/OrbicRecorder');
      setText('settingsFolder',d.folder||'Music/OrbicRecorder');
      updateRecordingPlayer(d);
      setText('error',d.error||'');$('error').hidden=!d.error;
      if(d.error) setText('capture','⚠ '+d.error);
      setText('otaVersion','Giao diện '+(d.otaVersion||'8.0.9'));setText('otaMessage',d.otaMessage||'Chưa kiểm tra');
      if(!settingsOpened){
        const supportsAutoOta=typeof d.autoOtaEnabled==='boolean';
        $('autoOtaEnabled').disabled=!supportsAutoOta;
        $('autoOtaEnabled').checked=supportsAutoOta && d.autoOtaEnabled;
      }
      $('installOta').disabled=!d.otaAvailable||d.otaStatus!=='available'||d.running||d.saving||playing||changing;
      $('rollback').hidden=!d.canRollback;
      levels.shift();levels.push(d.running&&!d.paused&&!d.musicAudible&&!changing&&!d.musicSuspended?cap(Number(d.level||0),0,100):0);
      renderWaveform();
      if(!settingsOpened) data.threshold=coalesce(d.threshold,data.threshold);
      updateThresholdSupport(d);
      refreshThreshold(coalesce(d.threshold,-38));
      if(!settingsOpened){
        for(const key of ['uiFont','noticeFont','uiOpacity','waveHeight','theme']) data[key]=coalesce(d[key],data[key]);
        if((d.waveStyle||'line')!==activeWaveStyle)applyWaveStyle(d.waveStyle||'line');
        applyTheme();
      }
    }catch(e){setText('error','MẤT KẾT NỐI ANDROID');$('error').hidden=false;}
  }
  poll();setInterval(poll,220);
})();
