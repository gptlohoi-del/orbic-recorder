/* This file and index.html/app.css can be changed through signed OTA. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const count = 42;
  const levels = new Array(count).fill(0);
  const wave = $('waveform');
  for (let i = 0; i < count; i++) {
    const bar = document.createElement('div');
    bar.className = 'bar';
    wave.appendChild(bar);
  }
  const bars = Array.from(wave.children);
  let settingsLoaded = false;
  let lastState = null;
  let updating = false;
  const fmt = ms => {
    const n = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    return [Math.floor(n / 3600), Math.floor(n / 60) % 60, n % 60].map(x => String(x).padStart(2, '0')).join(':');
  };
  const sec = ms => (Number(ms || 0) / 1000).toFixed(1).replace('.', ',') + 's';
  const action = code => {
    try { window.Orbic.action(code); } catch (e) { console.error(e); }
  };
  const setText = (id, value) => { $(id).textContent = String(value == null ? '' : value); };
  function loadForm(d) {
    ['threshold','minSpeech','pre','tail','warnSeconds'].forEach(k => { $(k).value = d[k]; });
    $('theme').value = d.theme || 'system';
    $('backupRaw').checked = d.backupRaw !== false;
    $('splitMinutes').value = String(d.splitMinutes || 15);
    settingsLoaded = true;
  }
  function render(d) {
    lastState = d;
    const running = !!d.running, paused = !!d.paused, saving = !!d.saving;
    const status = $('status');
    status.className = 'pill' + (saving ? ' saving' : paused ? ' paused' : '');
    status.textContent = saving ? '⌛ ĐANG LƯU FILE' : running ? (paused ? 'Ⅱ ĐANG TẠM DỪNG' : '● ĐANG GHI') : '○ SẴN SÀNG / ĐÃ DỪNG';
    setText('capture', d.capture);
    setText('sourceShort', d.source && d.source !== 'Chưa xác định' ? 'Có nguồn thu' : 'MIC');
    setText('db', (d.db || '—') + ' dBFS');
    setText('elapsed', fmt(d.elapsedMs));
    setText('kept', fmt(d.keptMs));
    setText('segments', d.segments || 0);
    setText('part', d.part || 1);
    setText('savedParts', d.savedParts || 0);
    setText('rawStatus', 'Bản gốc: ' + (d.backupRaw ? 'BẬT' : 'TẮT') + ' • Chia file: ' + (d.splitMinutes || 15) + ' phút' + (d.lastRaw ? ' • ' + d.lastRaw : ''));
    setText('recoveryStatus', d.recovering ? 'Đang khôi phục các file tạm…' : (d.recoveryStatus || ''));
    setText('preLabel', sec(d.pre));
    setText('tailLabel', sec(d.tail));
    setText('folderLabel', d.folder || 'Music/OrbicRecorder');
    setText('settingsFolder', d.folder || 'Music/OrbicRecorder');
    setText('file', d.file || 'Chưa có bản ghi');
    setText('error', d.error || '');
    $('error').hidden = !d.error;
    $('start').disabled = running || saving || !!d.recovering || d.otaStatus === 'downloading';
    $('pause').disabled = !running || saving;
    $('stop').disabled = !running || saving;
    $('pause').textContent = paused ? '▶ TIẾP TỤC' : 'Ⅱ PAUSE';
    $('installOta').disabled = !d.otaAvailable || running || saving || d.otaStatus !== 'available';
    $('rollback').hidden = !d.canRollback;
    setText('otaVersion', 'Giao diện ' + (d.otaVersion || '1.0.0'));
    setText('otaMessage', d.otaMessage || 'Chưa kiểm tra');
    const v = d.theme || 'system';
    if (v === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', v);
    if (!settingsLoaded) loadForm(d);
    levels.shift(); levels.push(running && !paused ? Math.max(0, Math.min(100, d.level || 0)) : 0);
    bars.forEach((bar, i) => {
      const amp = levels[i];
      bar.style.height = Math.max(3, Math.round(amp * 0.74)) + 'px';
      bar.classList.toggle('hot', amp > 77);
    });
  }
  function poll() {
    try {
      const data = JSON.parse(window.Orbic.snapshot());
      render(data);
    } catch (e) {
      setText('capture', 'Không kết nối được bộ ghi âm Android');
    }
  }
  $('start').addEventListener('click', () => action('start'));
  $('pause').addEventListener('click', () => action(lastState && lastState.paused ? 'resume' : 'pause'));
  $('stop').addEventListener('click', () => action('stop'));
  $('play').addEventListener('click', () => action('playLast'));
  $('checkOta').addEventListener('click', () => action('checkOta'));
  $('installOta').addEventListener('click', () => action('installOta'));
  $('rollback').addEventListener('click', () => action('rollback'));
  $('settingsToggle').addEventListener('click', () => {
    if (lastState) loadForm(lastState);
    $('modal').hidden = false;
  });
  function close() { $('modal').hidden = true; }
  $('closeSettings').addEventListener('click', close);
  $('cancelSettings').addEventListener('click', close);
  $('modalBackdrop').addEventListener('click', close);
  $('chooseFolder').addEventListener('click', () => { close(); action('chooseFolder'); });
  $('resetFolder').addEventListener('click', () => action('resetFolder'));
  $('saveSettings').addEventListener('click', () => {
    if (updating) return;
    const data = {};
    for (const key of ['threshold','minSpeech','pre','tail','warnSeconds']) data[key] = Number($(key).value);
    data.theme = $('theme').value;
    data.backupRaw = $('backupRaw').checked;
    data.splitMinutes = Number($('splitMinutes').value);
    if (!Number.isInteger(data.threshold) || data.threshold < -70 || data.threshold > -10 ||
        !Number.isInteger(data.minSpeech) || data.minSpeech < 60 || data.minSpeech > 1000 ||
        !Number.isInteger(data.pre) || data.pre < 0 || data.pre > 5000 ||
        !Number.isInteger(data.tail) || data.tail < 100 || data.tail > 5000 ||
        !Number.isInteger(data.warnSeconds) || data.warnSeconds < 10 || data.warnSeconds > 120 ||
        ![1,5,10,15,30,60].includes(data.splitMinutes)) {
      alert('Vui lòng nhập các giá trị đúng khoảng cho phép.'); return;
    }
    try { window.Orbic.saveSettings(JSON.stringify(data)); close(); settingsLoaded = false; }
    catch (e) { alert('Không lưu được cấu hình'); }
  });
  poll();
  setInterval(poll, 180);
})();
