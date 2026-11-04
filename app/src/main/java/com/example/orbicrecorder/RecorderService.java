package com.example.orbicrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.os.IBinder;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native v5.1: one 16-kHz capture stream writes a full, unfiltered backup (optional)
 * and threshold-filtered (or continuous) audio at the same time. Both are split into durable 15-min
 * default segments. A disk executor publishes closed WAV files without blocking
 * AudioRecord. "Pause" suspends *both* outputs.
 */
public class RecorderService extends Service {
    private static final String CHANNEL = "orbic_recording_v4";
    private static final int NOTIFICATION_ID = 141;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SIZE = RecordingArchive.BYTES_PER_SEC * FRAME_MS / 1000;
    private static final AtomicBoolean PROCESS_CAPTURING = new AtomicBoolean(false);

    static boolean isProcessCapturing() { return PROCESS_CAPTURING.get(); }

    private volatile boolean recording;
    private volatile boolean paused;
    private volatile boolean stopping;
    private volatile boolean musicHold;
    private volatile boolean saveRequested;
    private volatile boolean discardRequested;
    private AudioRecord audio;
    private AutomaticGainControl hardwareAgc;
    private boolean hardwareAgcSetting;
    private String hardwareAgcStatus = "Chưa kiểm tra";
    private Thread worker;

    private SharedPreferences state() { return getSharedPreferences("state", MODE_PRIVATE); }
    private SharedPreferences config() { return getSharedPreferences("config", MODE_PRIVATE); }
    private void stateValue(String key, Object val) {
        SharedPreferences.Editor edit = state().edit();
        if (val instanceof Boolean) edit.putBoolean(key, (Boolean) val);
        else if (val instanceof String) edit.putString(key, (String) val);
        else if (val instanceof Long) edit.putLong(key, (Long) val);
        else if (val instanceof Integer) edit.putInt(key, (Integer) val);
        edit.apply();
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Orbic Recorder đang ghi",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification() {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RecorderService.class).setAction("STOP"), flags);
        PendingIntent toggle = PendingIntent.getService(this, 2,
                new Intent(this, RecorderService.class).setAction(paused ? "RESUME" : "PAUSE"), flags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Orbic Recorder v9.5 – Bản ghi an toàn")
                .setContentText(stopping ? "Đang hoàn thiện WAV" : paused ? "Đang tạm dừng" :
                        musicHold ? "Micro tắt – nhạc đang phát" :
                                config().getBoolean("thresholdEnabled", true) ?
                                        "Đang lọc khoảng lặng" : "Đang ghi liên tục")
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), flags))
                .setOngoing(true)
                // An OS notification provides rescue controls even if a WebView
                // OTA fails or the app is switched to the background.
                .addAction(android.R.drawable.ic_media_pause,
                        paused ? "▶ TIẾP GHI" : "Ⅱ PAUSE", toggle)
                .addAction(android.R.drawable.ic_menu_save, "■ DỪNG & LƯU", stop)
                .build();
    }

    private void notifyChanged() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        switch (action) {
            case "START":
            case "START_HELD":
                if (!recording && !stopping && !RecordingArchive.isRecovering()) {
                    startForeground(NOTIFICATION_ID, notification());
                    // START_HELD tạo phiên ghi hợp lệ nhưng TUYỆT ĐỐI không mở micro
                    // trong lúc nhạc đang phát. MUSIC_RESUME mới mở micro trở lại.
                    startCapture("START_HELD".equals(action));
                }
                break;
            case "STOP_DISCARD":
                if (recording && !stopping) {
                    String session = state().getString("activeSession", "");
                    try {
                        // Durable marker must be flushed BEFORE recording stops.
                        // Crash recovery will never re-publish abandoned audio.
                        RecordingArchive.markDiscard(this, session);
                        discardRequested = true;
                        stopping = true;
                        recording = false;
                        state().edit().putBoolean("saving", true)
                                .putString("capture", "ĐANG HỦY VÀ XÓA WAV PHIÊN NÀY").apply();
                        notifyChanged();
                    } catch (IOException error) {
                        stateValue("error", "Không thể đánh dấu hủy an toàn: " + error.getMessage()
                                + ". Vẫn đang ghi; hãy dùng DỪNG & LƯU.");
                    }
                }
                break;
            case "STOP":
                if (recording) {
                    stopping = true;
                    recording = false;
                    state().edit().putBoolean("saving", true)
                            .putString("capture", "ĐANG LƯU CÁC ĐOẠN").apply();
                    notifyChanged();
                } else if (!stopping) stopSelf();
                break;
            case "PAUSE":
                if (recording && !stopping) {
                    paused = true;
                    state().edit().putBoolean("paused", true)
                            .putString("capture", "TẠM DỪNG").apply();
                    notifyChanged();
                }
                break;
            case "MUSIC_PAUSE":
                if (recording && !stopping) {
                    boolean alreadyReleased = musicHold &&
                            state().getBoolean("musicSuspended", false);
                    musicHold = true;
                    // If mic was already released, do not erase the acknowledgement.
                    if (!alreadyReleased) {
                        state().edit().putBoolean("musicSuspended", false)
                                .putString("capture", "ĐANG CHUYỂN SANG PHÁT NHẠC").apply();
                    }
                }
                break;
            case "MUSIC_RESUME":
                musicHold = false;
                state().edit().putBoolean("musicSuspended", false).apply();
                break;
            case "SAVE":
                if (recording && !stopping && !musicHold) saveRequested = true;
                break;
            case "RESUME":
                if (recording && !stopping) {
                    paused = false;
                    state().edit().putBoolean("paused", false)
                            .putString("capture", musicHold ? "NHẠC ĐANG PHÁT – MICRO TẮT" :
                                    config().getBoolean("thresholdEnabled", true)
                                            ? "ĐANG CHỜ TIẾNG ĐỘNG" : "ĐANG GHI LIÊN TỤC").apply();
                    notifyChanged();
                }
                break;
            default: break;
        }
        return START_NOT_STICKY;
    }

    private void startCapture(boolean startHeld) {
        PROCESS_CAPTURING.set(true);
        try {
            SharedPreferences cfg = config();
            String session = RecordingArchive.makeSessionId();
            // Preserve previous recording metadata so discard cannot clear it.
            String priorUri = state().getString("uri", "");
            String priorFile = state().getString("file", "Chưa có bản ghi");
            String priorRaw = state().getString("lastRaw", "");
            RecordingArchive.saveSession(this, session, cfg.getString("treeUri", ""),
                    cfg.getString("folderLabel", "Music/OrbicRecorder"));
            // Không lấy quyền AudioRecord nếu phát nhạc đang diễn ra.
            if (!startHeld) {
                audio = openMicrophone();
                audio.startRecording();
            }
            recording = true;
            paused = false;
            stopping = false;
            musicHold = startHeld;
            saveRequested = false;
            discardRequested = false;
            AudioDeviceInfo device = audio == null ? null : audio.getRoutedDevice();
            state().edit().putBoolean("running", true).putBoolean("saving", false)
                    .putBoolean("paused", false).putBoolean("musicSuspended", startHeld)
                    .putBoolean("overload", false).putString("agcStatus", startHeld ? "Đợi nhạc dừng" : hardwareAgcStatus)
                    .putBoolean("starting", false).putString("error", "")
                    .putString("capture", startHeld ? "NHẠC ĐANG PHÁT – MICRO TẮT" :
                            config().getBoolean("thresholdEnabled", true)
                                    ? "ĐANG CHỜ TIẾNG ĐỘNG" : "ĐANG GHI LIÊN TỤC")
                    .putString("activeSession", session)
                    .putString("priorUri", priorUri).putString("priorFile", priorFile)
                    .putString("priorLastRaw", priorRaw)
                    .putString("file", "Đang ghi phiên " + session)
                    .putLong("elapsedMs", 0).putLong("keptMs", 0)
                    .putInt("segments", 0).putInt("savedParts", 0)
                    .putInt("level", 0).putInt("part", 1).putString("lastRaw", "")
                    .putString("db", "—")
                    .putString("source", device == null ? "Chưa xác định" :
                            String.valueOf(device.getProductName()) + " (type " + device.getType() + ")")
                    .apply();
            worker = new Thread(() -> captureLoop(session), "OrbicVoiceWriterV7");
            worker.start();
        } catch (Exception e) {
            PROCESS_CAPTURING.set(false);
            recording = false;
            paused = false;
            stopping = false;
            musicHold = false;
            saveRequested = false;
            state().edit().putBoolean("running", false).putBoolean("saving", false)
                    .putBoolean("starting", false).putString("error", "Không bắt đầu được: " + e.getMessage()).apply();
            if (audio != null) { try { audio.release(); } catch (Exception ignored) {} audio = null; }
            stopForeground(true);
            stopSelf();
        }
    }

    private AudioRecord openMicrophone() throws IOException {
        int min = AudioRecord.getMinBufferSize(RecordingArchive.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IOException("Thiết bị không hỗ trợ 16 kHz mono");
        AudioRecord result = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RecordingArchive.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(Math.max(min * 2, FRAME_SIZE * 4)).build();
        if (result.getState() != AudioRecord.STATE_INITIALIZED) {
            result.release();
            throw new IOException("Không khởi tạo được AudioRecord");
        }
        // AGC is an Android capture effect, not a software repair for clipping.
        // Some microphone paths don't expose AGC; keep recording with limiter.
        hardwareAgc = null;
        hardwareAgcStatus = "Thiết bị không hỗ trợ AGC";
        try {
            if (AutomaticGainControl.isAvailable()) {
                hardwareAgc = AutomaticGainControl.create(result.getAudioSessionId());
                if (hardwareAgc != null) hardwareAgcStatus = "AGC sẵn sàng";
            }
        } catch (RuntimeException ignored) {
            hardwareAgc = null;
            hardwareAgcStatus = "AGC không khả dụng";
        }
        hardwareAgcSetting = !config().getBoolean("agcEnabled", true);
        setHardwareAgc(config().getBoolean("agcEnabled", true));
        return result;
    }

    private void setHardwareAgc(boolean enabled) {
        if (hardwareAgc == null) {
            hardwareAgcStatus = enabled ? "Thiết bị không hỗ trợ AGC" : "AGC đã tắt";
            stateValue("agcStatus", hardwareAgcStatus);
            return;
        }
        if (enabled != hardwareAgcSetting) {
            try { hardwareAgc.setEnabled(enabled); }
            catch (RuntimeException ignored) { /* unsupported control path */ }
            hardwareAgcSetting = enabled;
        }
        try {
            hardwareAgcStatus = hardwareAgc.getEnabled() ? "AGC hệ thống: BẬT" :
                    enabled ? "AGC hệ thống: KHÔNG BẬT ĐƯỢC" : "AGC đã tắt";
        } catch (RuntimeException e) { hardwareAgcStatus = "AGC không khả dụng"; }
        stateValue("agcStatus", hardwareAgcStatus);
    }

    private void releaseMicrophone() {
        AutomaticGainControl oldAgc = hardwareAgc;
        hardwareAgc = null;
        if (oldAgc != null) try { oldAgc.release(); } catch (RuntimeException ignored) { }
        AudioRecord old = audio;
        audio = null;
        if (old != null) {
            try { old.stop(); } catch (Exception ignored) {}
            try { old.release(); } catch (Exception ignored) {}
        }
    }

    private static void trim(Deque<byte[]> deque, int maximum) {
        while (deque.size() > maximum) deque.removeFirst();
    }

    private static long flush(RecordingArchive.Segment segment, Deque<byte[]> queue)
            throws IOException {
        long count = 0;
        for (byte[] item : queue) { segment.voice.write(item); count += item.length; }
        segment.voiceBytes += count;
        queue.clear();
        return count;
    }

    private static double rms(byte[] bytes) {
        long power = 0;
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            int sample = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
            power += (long) sample * sample;
        }
        return Math.sqrt((double) power / (bytes.length / 2));
    }

    private void captureLoop(String session) {
        SharedPreferences cfg = config();
        int threshold = cfg.getInt("threshold", -38);
        boolean thresholdEnabled = cfg.getBoolean("thresholdEnabled", true);
        int confirm = Math.max(1, (cfg.getInt("minSpeech", 180) + FRAME_MS - 1) / FRAME_MS);
        int preFrames = Math.max(0, (cfg.getInt("pre", 1000) + FRAME_MS - 1) / FRAME_MS);
        int tailFrames = Math.max(1, (cfg.getInt("tail", 1000) + FRAME_MS - 1) / FRAME_MS);
        int warnFrames = Math.max(1, cfg.getInt("warnSeconds", 9999) * 1000 / FRAME_MS);
        int splitMinutes = cfg.getInt("splitMinutes", 15);
        if (splitMinutes < 1 || splitMinutes > 60) splitMinutes = 15;
        long splitFrames = (long) splitMinutes * 60 * 1000 / FRAME_MS;
        boolean saveRaw = cfg.getBoolean("backupRaw", true);
        AudioDynamics dynamics = new AudioDynamics();
        boolean limiter = cfg.getBoolean("limiterEnabled", true);
        boolean overloadWarn = cfg.getBoolean("overloadWarn", true);
        int overloadThreshold = cfg.getInt("overloadDb", -1);
        long warningUntilFrame = 0;
        dynamics.configure(limiter, cfg.getInt("limitDb", -6),
                cfg.getInt("attackMs", 10), cfg.getInt("releaseMs", 500));

        Deque<byte[]> pre = new ArrayDeque<>();
        Deque<byte[]> candidate = new ArrayDeque<>();
        Deque<byte[]> tail = new ArrayDeque<>();
        boolean active = false, wasPaused = false;
        long elapsed = 0, kept = 0, silence = 0, partFrames = 0;
        int segments = 0, part = 1;
        RecordingArchive.Segment current = null;
        byte[] frame = new byte[FRAME_SIZE];
        try {
            current = RecordingArchive.open(this, session, part, saveRaw);
            while (recording) {
                // IMPORTANT: release Android AudioRecord, not just mute its samples.
                // The audio focus and music player start only after this acknowledgement.
                if (musicHold) {
                    if (active && !tail.isEmpty()) kept += flush(current, tail);
                    tail.clear(); pre.clear(); candidate.clear();
                    active = false;
                    wasPaused = true;
                    RecordingArchive.sync(current);
                    releaseMicrophone();
                    state().edit().putBoolean("musicSuspended", true)
                            .putBoolean("overload", false).putInt("level", 0).putString("db", "—")
                            .putString("agcStatus", "Micro tắt khi phát nhạc")
                            .putString("capture", "ĐANG PHÁT NHẠC – MICRO ĐÃ TẮT").apply();
                    while (recording && musicHold) {
                        // A second PLAY may arrive before the mic reopens. Re-acknowledge
                        // the already released mic, instead of timing out on stale state.
                        if (!state().getBoolean("musicSuspended", false))
                            state().edit().putBoolean("musicSuspended", true).apply();
                        try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                    }
                    if (!recording) break;
                    audio = openMicrophone();
                    audio.startRecording();
                    state().edit().putBoolean("musicSuspended", false)
                            .putString("capture", paused ? "ĐANG TẠM DỪNG" :
                                    cfg.getBoolean("thresholdEnabled", true)
                                            ? "ĐANG CHỜ TIẾNG ĐỘNG" : "ĐANG GHI LIÊN TỤC").apply();
                    pre.clear(); candidate.clear(); tail.clear();
                    wasPaused = false;
                    silence = 0;
                    continue;
                }
                int filled = 0;
                while (recording && filled < FRAME_SIZE) {
                    int size = audio.read(frame, filled, FRAME_SIZE - filled);
                    if (size > 0) filled += size;
                    else if (size < 0) throw new IOException("AudioRecord.read: " + size);
                }
                if (filled != FRAME_SIZE) break;
                if (paused) {
                    if (!wasPaused) {
                        if (active && !tail.isEmpty()) kept += flush(current, tail);
                        tail.clear(); pre.clear(); candidate.clear();
                        active = false;
                        wasPaused = true;
                        RecordingArchive.sync(current);
                    }
                    state().edit().putInt("level", 0).putBoolean("overload", false).apply();
                    continue;
                }
                if (wasPaused) {
                    pre.clear(); candidate.clear(); tail.clear();
                    active = false; wasPaused = false; silence = 0;
                }

                if (saveRequested) {
                    saveRequested = false;
                    if (active && !tail.isEmpty()) kept += flush(current, tail);
                    RecordingArchive.close(current);
                    RecordingArchive.queueExport(this, current);
                    current = null;
                    part++;
                    current = RecordingArchive.open(this, session, part, saveRaw);
                    partFrames = 0;
                    active = false;
                    tail.clear(); pre.clear(); candidate.clear();
                    state().edit().putInt("part", part)
                            .putString("capture", thresholdEnabled
                                    ? "ĐÃ LƯU ĐOẠN – ĐANG LẮNG NGHE"
                                    : "ĐÃ LƯU ĐOẠN – GHI LIÊN TỤC").apply();
                }
                byte[] block = Arrays.copyOf(frame, FRAME_SIZE);
                elapsed++; partFrames++;
                // Backup the captured PCM BEFORE the software limiter. This
                // preserves original audio, including possible source clipping.
                if (current.raw != null) {
                    current.raw.write(block);
                    current.rawBytes += block.length;
                }
                // VAD is based on input signal, independent of limiter output.
                double inputPeakDb = AudioDynamics.peakDbFs(block);
                if (elapsed % 5 == 0) {
                    limiter = cfg.getBoolean("limiterEnabled", true);
                    overloadWarn = cfg.getBoolean("overloadWarn", true);
                    overloadThreshold = cfg.getInt("overloadDb", -1);
                    dynamics.configure(limiter, cfg.getInt("limitDb", -6),
                            cfg.getInt("attackMs", 10), cfg.getInt("releaseMs", 500));
                    boolean desiredAgc = cfg.getBoolean("agcEnabled", true);
                    if (desiredAgc != hardwareAgcSetting || hardwareAgc == null &&
                            !hardwareAgcStatus.equals(desiredAgc ? "Thiết bị không hỗ trợ AGC" : "AGC đã tắt")) {
                        setHardwareAgc(desiredAgc);
                    }
                }
                if (overloadWarn && inputPeakDb >= overloadThreshold) {
                    warningUntilFrame = elapsed + 75; // keep warning 1.5 seconds
                }
                dynamics.processInPlace(block);

                double amp = rms(frame);
                double db = 20 * Math.log10(Math.max(1.0e-9, amp / 32768.0));
                // Both the threshold and the continuous-recording switch are hot-applied
                // within five 20 ms frames without reopening the microphone.
                if (elapsed % 5 == 0) {
                    threshold = cfg.getInt("threshold", -38);
                    thresholdEnabled = cfg.getBoolean("thresholdEnabled", true);
                }
                boolean loud = !thresholdEnabled || db >= threshold;
                silence = loud ? 0 : silence + 1;

                // Disabling VAD must write EVERY frame, even digital silence and
                // captures shorter than minSpeech. Do not fake this with -80 dBFS:
                // true silence is floored to -180 dBFS in the RMS calculation.
                if (!thresholdEnabled) {
                    if (!active) {
                        active = true;
                        segments++;
                        kept += flush(current, pre);
                        kept += flush(current, candidate);
                    }
                    kept += flush(current, tail);
                    current.voice.write(block);
                    current.voiceBytes += block.length;
                    kept += block.length;
                } else if (active) {
                    if (loud) {
                        kept += flush(current, tail);
                        current.voice.write(block);
                        current.voiceBytes += block.length;
                        kept += block.length;
                    } else {
                        tail.addLast(block);
                        if (tail.size() > tailFrames) {
                            for (int i = 0; i < tailFrames; i++) {
                                byte[] item = tail.removeFirst();
                                current.voice.write(item);
                                current.voiceBytes += item.length;
                                kept += item.length;
                            }
                            pre.clear();
                            while (!tail.isEmpty()) pre.addLast(tail.removeFirst());
                            trim(pre, preFrames);
                            active = false;
                        }
                    }
                } else if (loud) {
                    candidate.addLast(block);
                    if (candidate.size() >= confirm) {
                        active = true;
                        segments++;
                        kept += flush(current, pre);
                        kept += flush(current, candidate);
                    }
                } else {
                    while (!candidate.isEmpty()) pre.addLast(candidate.removeFirst());
                    pre.addLast(block);
                    trim(pre, preFrames);
                }

                if (elapsed % 5 == 0) {
                    int level = (int) Math.max(0, Math.min(100, (db + 60.0) * 100.0 / 60.0));
                    boolean overload = overloadWarn && elapsed <= warningUntilFrame;
                    String capture = overload ? "⚠ TÍN HIỆU GẦN 0 dBFS – KIỂM TRA MICRO" :
                            !thresholdEnabled ? "● GHI LIÊN TỤC – ĐÃ TẮT NGƯỠNG" :
                            active ? "● ĐANG GIỮ ÂM THANH" : silence > warnFrames ?
                            "! KHÔNG CÓ TIẾNG NÓI – KIỂM TRA NGUỒN" : "ĐANG LỌC KHOẢNG LẶNG";
                    state().edit().putLong("elapsedMs", elapsed * FRAME_MS)
                            .putLong("keptMs", kept * 1000L / RecordingArchive.BYTES_PER_SEC)
                            .putInt("segments", segments).putInt("level", level)
                            .putBoolean("overload", overload)
                            .putString("peakDb", String.format(Locale.US, "%.1f", inputPeakDb))
                            .putString("db", String.format(Locale.US, "%.1f", Math.max(-120.0, db)))
                            .putString("capture", capture).putInt("part", part).apply();
                }

                // Refresh and fsync both WAV headers every five seconds.
                if (elapsed % 250 == 0) RecordingArchive.sync(current);

                if (partFrames >= splitFrames) {
                    if (active && !tail.isEmpty()) kept += flush(current, tail);
                    RecordingArchive.close(current);
                    RecordingArchive.queueExport(this, current);
                    current = null;
                    part++;
                    current = RecordingArchive.open(this, session, part, saveRaw);
                    partFrames = 0;
                    // Preserve active voiced recording across a segment boundary.
                    // If silence was pending, the previous chunk has the tail already.
                    stateValue("part", part);
                }
            }
            if (current != null && active && !tail.isEmpty()) kept += flush(current, tail);
        } catch (Exception failure) {
            stateValue("error", "Lỗi ghi âm: " + failure.getMessage()
                    + ". Dữ liệu WAV tạm sẽ được khôi phục khi mở ứng dụng lần sau.");
        } finally {
            recording = false;
            paused = false;
            state().edit().putBoolean("running", false).putBoolean("paused", false)
                    .putBoolean("saving", true).putBoolean("musicSuspended", false)
                    .putBoolean("overload", false).putInt("level", 0)
                    .putLong("elapsedMs", elapsed * FRAME_MS)
                    .putLong("keptMs", kept * 1000L / RecordingArchive.BYTES_PER_SEC)
                    .putInt("segments", segments).putString("capture",
                            discardRequested ? "ĐANG XÓA WAV PHIÊN NÀY" : "ĐANG LƯU FILE").apply();
            releaseMicrophone();
            if (current != null) {
                try {
                    RecordingArchive.close(current);
                    if (!discardRequested) RecordingArchive.queueExport(this, current);
                } catch (IOException failure) {
                    stateValue("error", "Không hoàn thiện WAV: " + failure.getMessage()
                            + ". Giữ lại file tạm để khôi phục.");
                }
            }
            try {
                Future<?> done = RecordingArchive.barrier();
                done.get();
            } catch (Exception failure) {
                stateValue("error", "Xuất WAV chưa hoàn tất: " + failure.getMessage());
            }
            boolean discarded = discardRequested;
            boolean deleteOk = true;
            if (discarded) {
                // All queued export jobs have finished: delete only THIS session.
                deleteOk = RecordingArchive.discardSession(this, session);
            }
            stopping = false;
            musicHold = false;
            discardRequested = false;
            SharedPreferences.Editor finished = state().edit()
                    .putBoolean("musicSuspended", false).putBoolean("saving", false)
                    .putString("activeSession", "")
                    .putString("capture", discarded
                            ? (deleteOk ? "ĐÃ DỪNG & KHÔNG LƯU" : "⚠ CÒN FILE CHƯA XÓA")
                            : "ĐÃ DỪNG");
            if (discarded) {
                finished.putString("uri", state().getString("priorUri", ""))
                        .putString("file", state().getString("priorFile", "Chưa có bản ghi"))
                        .putString("lastRaw", state().getString("priorLastRaw", ""))
                        .putInt("savedParts", 0);
                if (!deleteOk) finished.putString("error", "Một số file phiên vừa hủy chưa xóa được; "
                        + "ứng dụng sẽ thử xóa khi mở lại. Kiểm tra thư mục WAV.");
                else finished.putString("error", "");
            }
            finished.remove("priorUri").remove("priorFile").remove("priorLastRaw").apply();
            PROCESS_CAPTURING.set(false);
            stopForeground(true);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        recording = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
