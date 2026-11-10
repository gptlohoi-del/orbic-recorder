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
import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 16-kHz PCM recorder with SAFE and ECONOMY capture policies.
 * Safe mode stores every frame. Economy mode keeps all shorter quiet spans and
 * compacts spans that exceed the configured duration. Both write one WAV only.
 * The one-shot input trigger runs before either mode starts its capture.
 */
public class RecorderService extends Service {
    private static final String CHANNEL = "orbic_recording_v4";
    private static final int NOTIFICATION_ID = 141;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SIZE = RecordingArchive.BYTES_PER_SEC * FRAME_MS / 1000;
    private static final AtomicBoolean PROCESS_CAPTURING = new AtomicBoolean(false);

    static boolean isProcessCapturing() { return PROCESS_CAPTURING.get(); }

    private volatile boolean recording;
    private volatile boolean armed;
    private volatile boolean stopping;
    // Recording stays active while paused; only SAFE mode can suspend WAV writes.
    private volatile boolean paused;
    private volatile boolean safeSession;
    private volatile long pauseStartedAt;
    private volatile boolean discardRequested;
    private AudioRecord audio;
    private int initialDeviceId = -1;
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
        String warning = state().getString("micWarning", "");
        if (warning.isEmpty()) warning = state().getString("storageWarning", "");
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Orbic Recorder – Ghi âm")
                .setContentText(stopping ? "Đang hoàn thiện WAV" : !warning.isEmpty() ? warning
                        : armed ? "Đang chờ tín hiệu từ micro – chạm ứng dụng để hủy chờ"
                        : paused ? "⏸ ĐANG PAUSE – chạm ứng dụng để CONTINUE hoặc SAVE"
                        : "Đang ghi – mở ứng dụng, vuốt sang phải để LƯU")
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), flags))
                .setOngoing(true).build();
    }

    private void notifyChanged() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        switch (action) {
            case "START":
            case "ARM":
                if (!recording && !armed && !stopping && !RecordingArchive.isRecovering()) {
                    startForeground(NOTIFICATION_ID, notification());
                    startCapture("ARM".equals(action));
                }
                break;
            case "DISARM":
                if (armed && !recording && !stopping) armed = false;
                break;
            // Only SAFE recording supports PAUSE; it keeps the mic open but writes no PCM.
            // The UI exposes PAUSE only after a completed left-to-right confirmation swipe.
            case "PAUSE":
                if (safeSession && recording && !armed && !stopping && !paused) {
                    paused = true;
                    pauseStartedAt = SystemClock.elapsedRealtime();
                    state().edit().putBoolean("paused", true).putLong("pausedMs", 0L)
                            .putString("capture", "⏸ AN TOÀN – ĐANG TẠM DỪNG GHI").apply();
                    notifyChanged();
                }
                break;
            case "CONTINUE":
                if (safeSession && recording && !armed && !stopping && paused) {
                    paused = false;
                    state().edit().putBoolean("paused", false).putLong("pausedMs", 0L)
                            .putString("capture", "● AN TOÀN – ĐÃ TIẾP TỤC GHI").apply();
                    notifyChanged();
                }
                break;
            // Destructive legacy command intentionally not supported by the new UI.
            case "STOP_DISCARD": break;
            case "STOP":
                if (recording) {
                    stopping = true;
                    recording = false;
                    paused = false;
                    stateValue("paused", false);
                    state().edit().putBoolean("saving", true)
                            .putString("capture", "ĐANG LƯU FILE WAV").apply();
                    notifyChanged();
                } else if (!stopping && !armed) stopSelf();
                break;
            // Ignore legacy notification and old OTA generic commands. Only the
            // deliberate swipe UI exposes PAUSE; STOP still requires SAVE swipe.
            default: break;
        }
        return START_NOT_STICKY;
    }

    private void startCapture(boolean automatic) {
        PROCESS_CAPTURING.set(true);
        try {
            SharedPreferences cfg = config();
            // No session or output file exists during waiting; canceled waiting is clean.
            String session = automatic ? null : RecordingArchive.makeSessionId();
            String priorUri = state().getString("uri", "");
            String priorFile = state().getString("file", "Chưa có bản ghi");
            String priorRaw = state().getString("lastRaw", "");
            if (!automatic) RecordingArchive.saveSession(this, session, cfg.getString("treeUri", ""),
                    cfg.getString("folderLabel", "Music/OrbicRecorder"));
            audio = openMicrophone();
            audio.startRecording();
            recording = !automatic;
            armed = automatic;
            safeSession = !automatic;
            paused = false;
            pauseStartedAt = 0L;
            stopping = false;
            discardRequested = false;
            AudioDeviceInfo device = audio == null ? null : audio.getRoutedDevice();
            initialDeviceId = device == null ? -1 : device.getId();
            state().edit().putBoolean("running", !automatic).putBoolean("armed", automatic)
                    .putBoolean("autoStarted", false).putBoolean("saving", false)
                    .putBoolean("paused", false).putLong("pausedMs", 0L)
                    .putBoolean("musicSuspended", false)
                    .putBoolean("overload", false).putString("agcStatus", hardwareAgcStatus)
                    .putString("micWarning", "").putString("storageWarning", "")
                    .putBoolean("starting", false).putString("error", "")
                    .putString("capture", automatic ? "◉ ĐANG CHỜ TÍN HIỆU ĐẦU VÀO" : "● ĐANG GHI LIÊN TỤC")
                    .putString("activeSession", automatic ? "" : session)
                    .putString("priorUri", priorUri).putString("priorFile", priorFile)
                    .putString("priorLastRaw", priorRaw)
                    .putString("file", automatic ? priorFile : "Đang ghi phiên " + session)
                    .putLong("elapsedMs", 0).putLong("keptMs", 0)
                    .putInt("segments", 0).putInt("savedParts", 0)
                    .putInt("level", 0).putInt("part", 1).putString("lastRaw", "")
                    .putString("db", "—")
                    .putString("source", device == null ? "Chưa xác định" :
                            String.valueOf(device.getProductName()) + " (type " + device.getType() + ")")
                    .apply();
            notifyChanged();
            worker = new Thread(() -> captureLoop(session, automatic, priorUri, priorFile, priorRaw),
                    "OrbicVoiceWriterV8");
            worker.start();
        } catch (Exception e) {
            PROCESS_CAPTURING.set(false);
            recording = false;
            armed = false;
            paused = false;
            safeSession = false;
            stopping = false;
            state().edit().putBoolean("running", false).putBoolean("armed", false)
                    .putBoolean("saving", false)
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

    private static double rms(byte[] bytes) {
        long power = 0;
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            int sample = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
            power += (long) sample * sample;
        }
        return Math.sqrt((double) power / (bytes.length / 2));
    }

    /** Each WAV write/rewind keeps the durable header accounting consistent. */
    private static SilenceCompactor.Sink wavSink(final RecordingArchive.Segment segment) {
        return new SilenceCompactor.Sink() {
            @Override public void append(byte[] pcm) throws IOException {
                segment.voice.write(pcm);
                segment.voiceBytes += pcm.length;
            }
            @Override public void truncateTail(long bytes) throws IOException {
                if (bytes < 0 || bytes > segment.voiceBytes)
                    throw new IOException("Invalid silence trim length");
                long remaining = segment.voiceBytes - bytes;
                segment.voice.setLength(44 + remaining);
                segment.voice.seek(44 + remaining);
                segment.voiceBytes = remaining;
            }
        };
    }

    private void checkCaptureWarnings(AudioRecord source, long elapsedFrames) {
        if (elapsedFrames % 250 != 0) return; // every five seconds
        try {
            AudioDeviceInfo routed = source.getRoutedDevice();
            if (initialDeviceId != -1 && (routed == null || routed.getId() != initialDeviceId)
                    && state().getString("micWarning", "").isEmpty()) {
                stateValue("micWarning", "⚠ Nguồn micro đã đổi hoặc ngắt kết nối! Kiểm tra dây PHONES.");
                notifyChanged();
            }
        } catch (RuntimeException ignored) { /* Device routing not available. */ }
        try {
            long free = RecordingArchive.folder(this).getUsableSpace();
            String warning = free < 128L * 1024 * 1024
                    ? "⚠ Dung lượng ghi còn " + (free / (1024 * 1024)) + " MB; nên LƯU sớm."
                    : "";
            String previous = state().getString("storageWarning", "");
            if (!warning.equals(previous)) {
                stateValue("storageWarning", warning);
                notifyChanged();
            }
        } catch (IOException error) {
            stateValue("storageWarning", "⚠ Không kiểm tra được dung lượng lưu trữ.");
        }
    }

    private void captureLoop(String session, boolean automatic,
                             String priorUri, String priorFile, String priorRaw) {
        SharedPreferences cfg = config();
        int splitMinutes = cfg.getInt("splitMinutes", 60);
        if (splitMinutes < 1 || splitMinutes > 60) splitMinutes = 60;
        long splitFrames = (long) splitMinutes * 60 * 1000 / FRAME_MS;
        // Both tabs deliberately create one WAV. Mode is snapshotted for the session.
        final boolean economy = automatic;
        final boolean saveRaw = false;
        int selectedSeconds = cfg.getInt("silenceSeconds", 60);
        final int silenceSeconds = selectedSeconds >= 20 && selectedSeconds <= 1000
                && (selectedSeconds - 20) % 10 == 0 ? selectedSeconds : 60;
        final int silenceDb = Math.max(-100, Math.min(-15, cfg.getInt("triggerDb", -40)));
        final SilenceCompactor compactor = economy
                ? new SilenceCompactor(silenceSeconds, FRAME_MS, 2) : null;
        AudioDynamics dynamics = new AudioDynamics();
        boolean limiter = cfg.getBoolean("limiterEnabled", true);
        boolean overloadWarn = cfg.getBoolean("overloadWarn", true);
        int overloadThreshold = cfg.getInt("overloadDb", -1);
        long warningUntilFrame = 0;
        dynamics.configure(limiter, cfg.getInt("limitDb", -6),
                cfg.getInt("attackMs", 10), cfg.getInt("releaseMs", 500));

        long elapsed = 0, keptBytes = 0, partFrames = 0;
        int part = 1;
        RecordingArchive.Segment current = null;
        byte[] frame = new byte[FRAME_SIZE];
        // Pre-roll and one-shot input qualification are independently unit-tested.
        final int triggerDb = Math.max(-100, Math.min(-15, cfg.getInt("triggerDb", -40)));
        InputSignalTrigger signal = new InputSignalTrigger(triggerDb, 50, 5);
        long waitingFrames = 0;
        long pausedFrames = 0;
        boolean pauseSynced = false;
        try {
            if (!automatic) current = RecordingArchive.open(this, session, part, saveRaw);
            while (recording || armed) {
                int filled = 0;
                while ((recording || armed) && filled < FRAME_SIZE) {
                    int size = audio.read(frame, filled, FRAME_SIZE - filled);
                    if (size > 0) filled += size;
                    else if (size < 0) throw new IOException("AudioRecord.read: " + size);
                }
                if (filled != FRAME_SIZE) break;
                byte[] block = Arrays.copyOf(frame, FRAME_SIZE);
                if (armed) {
                    boolean detected = signal.offer(block);
                    if (!detected) {
                        // Update the UI 5 times/second, not every 20ms PCM frame.
                        if (++waitingFrames % 10 == 0) {
                            checkCaptureWarnings(audio, waitingFrames);
                            double signalDb = signal.lastDb();
                            int meter = (int) Math.max(0, Math.min(100, (signalDb + 60) * 100 / 60));
                            state().edit().putInt("level", meter)
                                    .putString("db", String.format(Locale.US, "%.1f", Math.max(-120, signalDb)))
                                    .putString("capture", "◉ ĐANG CHỜ TÍN HIỆU (kích hoạt ≥ " + triggerDb + " dBFS)")
                                    .apply();
                        }
                        continue;
                    }
                    if (!armed) break; // User canceled while micro was being read.
                    session = RecordingArchive.makeSessionId();
                    RecordingArchive.saveSession(this, session,
                            cfg.getString("treeUri", ""),
                            cfg.getString("folderLabel", "Music/OrbicRecorder"));
                    current = RecordingArchive.open(this, session, part, saveRaw);
                    // A cancel pressed during storage setup must never silently become START.
                    if (!armed) {
                        try { RecordingArchive.close(current); }
                        finally {
                            current = null;
                            RecordingArchive.abortUnstartedSession(this, session);
                            session = null;
                        }
                        break;
                    }
                    // Set running before leaving waiting state, avoiding a capture gap.
                    recording = true;
                    armed = false;
                    state().edit().putBoolean("armed", false).putBoolean("running", true)
                            .putBoolean("autoStarted", true)
                            .putString("activeSession", session)
                            .putString("priorUri", priorUri).putString("priorFile", priorFile)
                            .putString("priorLastRaw", priorRaw)
                            .putString("file", "Đang ghi phiên " + session)
                            .putString("capture", "● ĐÃ KÍCH HOẠT – ĐANG GHI LIÊN TỤC")
                            .apply();
                    notifyChanged();
                    // Every pre-roll frame, including the triggering frame, is written ONCE.
                    for (byte[] buffered : signal.drainPreRoll()) {
                        byte[] processed = Arrays.copyOf(buffered, buffered.length);
                        dynamics.processInPlace(processed);
                        long before = current.voiceBytes;
                        compactor.accept(processed, 20 * Math.log10(Math.max(1.0e-9,
                                rms(buffered) / 32768.0)) >= silenceDb, wavSink(current));
                        keptBytes += current.voiceBytes - before;
                        elapsed++;
                        partFrames = current.voiceBytes / FRAME_SIZE;
                    }
                    continue;
                }
                if (!recording) break;
                if (paused) {
                    // Consume PCM while paused so the AudioRecord buffer cannot overflow.
                    // Do not write any samples: WAV duration excludes the paused interval.
                    if (!pauseSynced && current != null) {
                        RecordingArchive.sync(current);
                        pauseSynced = true;
                    }
                    pausedFrames++;
                    if (pausedFrames % 10 == 0) {
                        stateValue("pausedMs", Math.max(0L,
                                SystemClock.elapsedRealtime() - pauseStartedAt));
                    }
                    checkCaptureWarnings(audio, pausedFrames);
                    continue;
                }
                pauseSynced = false;
                elapsed++;
                partFrames++;
                double inputPeakDb = AudioDynamics.peakDbFs(block);
                if (elapsed % 5 == 0) {
                    limiter = cfg.getBoolean("limiterEnabled", true);
                    overloadWarn = cfg.getBoolean("overloadWarn", true);
                    overloadThreshold = cfg.getInt("overloadDb", -1);
                    dynamics.configure(limiter, cfg.getInt("limitDb", -6),
                            cfg.getInt("attackMs", 10), cfg.getInt("releaseMs", 500));
                    boolean desiredAgc = cfg.getBoolean("agcEnabled", true);
                    if (desiredAgc != hardwareAgcSetting || hardwareAgc == null &&
                            !hardwareAgcStatus.equals(desiredAgc ? "Thiết bị không hỗ trợ AGC" : "AGC đã tắt"))
                        setHardwareAgc(desiredAgc);
                }
                if (overloadWarn && inputPeakDb >= overloadThreshold)
                    warningUntilFrame = elapsed + 75;
                dynamics.processInPlace(block);
                // SAFE writes every captured frame except during a user-confirmed PAUSE.
                // ECONOMY never pauses; it only compacts qualifying quiet spans.
                long before = current.voiceBytes;
                if (economy) {
                    double dbRms = 20 * Math.log10(Math.max(1.0e-9, rms(frame) / 32768.0));
                    compactor.accept(block, dbRms >= silenceDb, wavSink(current));
                } else {
                    current.voice.write(block);
                    current.voiceBytes += block.length;
                }
                keptBytes += current.voiceBytes - before;
                partFrames = current.voiceBytes / FRAME_SIZE;
                checkCaptureWarnings(audio, elapsed);
                if (elapsed % 5 == 0) {
                    double amp = rms(frame);
                    double db = 20 * Math.log10(Math.max(1.0e-9, amp / 32768.0));
                    int level = (int) Math.max(0, Math.min(100, (db + 60.0) * 100.0 / 60.0));
                    boolean overload = overloadWarn && elapsed <= warningUntilFrame;
                    state().edit().putLong("elapsedMs", elapsed * FRAME_MS)
                            .putLong("keptMs", keptBytes * 1000L / RecordingArchive.BYTES_PER_SEC)
                            .putInt("segments", 1).putInt("level", level)
                            .putBoolean("overload", overload)
                            .putString("peakDb", String.format(Locale.US, "%.1f", inputPeakDb))
                            .putString("db", String.format(Locale.US, "%.1f", Math.max(-120.0, db)))
                            .putString("capture", overload
                                    ? "⚠ ĐẦU VÀO GẦN QUÁ TẢI – KIỂM TRA MICRO"
                                    : economy ? "● TIẾT KIỆM – ĐANG GHI (cắt lặng ≥ " + silenceSeconds + "s)"
                                    : "● AN TOÀN – ĐANG GHI LIÊN TỤC")
                            .putInt("part", part).apply();
                }
                if (elapsed % 250 == 0) RecordingArchive.sync(current);
                // Never split a segment while a quiet run may still be rewound.
                if (partFrames >= splitFrames && (!economy || !compactor.inQuietSpan())) {
                    RecordingArchive.close(current);
                    RecordingArchive.queueExport(this, current);
                    current = null;
                    part++;
                    current = RecordingArchive.open(this, session, part, saveRaw);
                    partFrames = 0;
                    stateValue("part", part);
                }
            }
        } catch (Exception failure) {
            stateValue("error", "Lỗi ghi âm: " + failure.getMessage()
                    + ". Dữ liệu WAV tạm sẽ được khôi phục khi mở ứng dụng lần sau.");
        } finally {
            boolean producedRecording = current != null || recording || stopping || elapsed > 0 || session != null;
            recording = false;
            armed = false;
            paused = false;
            safeSession = false;
            if (!producedRecording) {
                releaseMicrophone();
                state().edit().putBoolean("running", false).putBoolean("armed", false)
                        .putBoolean("starting", false).putBoolean("saving", false)
                        .putBoolean("paused", false).putLong("pausedMs", 0L)
                        .putBoolean("overload", false).putBoolean("autoStarted", false)
                    .putString("micWarning", "").putString("storageWarning", "")
                        .putInt("level", 0).putString("activeSession", "")
                        .putString("db", "—").putString("capture", "ĐÃ HỦY CHỜ – CHƯA GHI FILE")
                        .apply();
                PROCESS_CAPTURING.set(false);
                stopForeground(true);
                stopSelf();
                return;
            }
            if (current != null && compactor != null) {
                try {
                    long before = current.voiceBytes;
                    compactor.flushTail(wavSink(current));
                    keptBytes += current.voiceBytes - before;
                } catch (IOException failure) {
                    stateValue("error", "Không hoàn thiện đuôi khoảng lặng: " + failure.getMessage());
                }
            }
            state().edit().putBoolean("running", false).putBoolean("armed", false)
                    .putBoolean("paused", false).putLong("pausedMs", 0L)
                    .putBoolean("saving", true).putBoolean("musicSuspended", false)
                    .putBoolean("overload", false).putInt("level", 0)
                    .putLong("elapsedMs", elapsed * FRAME_MS)
                    .putLong("keptMs", keptBytes * 1000L / RecordingArchive.BYTES_PER_SEC)
                    .putInt("segments", elapsed > 0 ? 1 : 0).putString("capture",
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
            if (discarded) deleteOk = RecordingArchive.discardSession(this, session);
            stopping = false;
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
            finished.remove("priorUri").remove("priorFile").remove("priorLastRaw")
                    .putString("micWarning", "").putString("storageWarning", "").apply();
            PROCESS_CAPTURING.set(false);
            stopForeground(true);
            stopSelf();
        }
    }

    @Override public void onDestroy() {
        recording = false;
        armed = false;
        paused = false;
        safeSession = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
