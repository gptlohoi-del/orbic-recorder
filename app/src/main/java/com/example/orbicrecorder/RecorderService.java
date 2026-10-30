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
 * and VAD-filtered audio at the same time. Both are split into durable 15-min
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
    private AudioRecord audio;
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
                .setContentTitle("Orbic Recorder v5.1 – Bản ghi an toàn")
                .setContentText(stopping ? "Đang hoàn thiện và lưu WAV" : paused ? "Đang tạm dừng" : "Đang thu và lọc âm thanh")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, paused ? "TIẾP TỤC" : "PAUSE", toggle).build())
                .addAction(new Notification.Action.Builder(null, "DỪNG VÀ LƯU", stop).build())
                .build();
    }

    private void notifyChanged() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        switch (action) {
            case "START":
                if (!recording && !stopping && !RecordingArchive.isRecovering()) {
                    startForeground(NOTIFICATION_ID, notification());
                    startCapture();
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
            case "RESUME":
                if (recording && !stopping) {
                    paused = false;
                    state().edit().putBoolean("paused", false)
                            .putString("capture", "ĐANG CHỜ TIẾNG NÓI").apply();
                    notifyChanged();
                }
                break;
            default: break;
        }
        return START_NOT_STICKY;
    }

    private void startCapture() {
        PROCESS_CAPTURING.set(true);
        try {
            int min = AudioRecord.getMinBufferSize(RecordingArchive.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("Thiết bị không hỗ trợ 16 kHz mono");
            audio = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RecordingArchive.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(min * 2, FRAME_SIZE * 4)).build();
            if (audio.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IOException("Không khởi tạo được AudioRecord");

            SharedPreferences cfg = config();
            String session = RecordingArchive.makeSessionId();
            RecordingArchive.saveSession(this, session, cfg.getString("treeUri", ""),
                    cfg.getString("folderLabel", "Music/OrbicRecorder"));
            audio.startRecording();
            recording = true;
            paused = false;
            stopping = false;
            AudioDeviceInfo device = audio.getRoutedDevice();
            state().edit().putBoolean("running", true).putBoolean("saving", false)
                    .putBoolean("paused", false).putString("error", "")
                    .putString("capture", "ĐANG CHỜ TIẾNG NÓI")
                    .putString("file", "Đang ghi phiên " + session)
                    .putLong("elapsedMs", 0).putLong("keptMs", 0)
                    .putInt("segments", 0).putInt("savedParts", 0)
                    .putInt("level", 0).putInt("part", 1).putString("lastRaw", "")
                    .putString("db", "—")
                    .putString("source", device == null ? "Chưa xác định" :
                            String.valueOf(device.getProductName()) + " (type " + device.getType() + ")")
                    .apply();
            worker = new Thread(() -> captureLoop(session), "OrbicVoiceWriterV51");
            worker.start();
        } catch (Exception e) {
            PROCESS_CAPTURING.set(false);
            recording = false;
            paused = false;
            stopping = false;
            state().edit().putBoolean("running", false).putBoolean("saving", false)
                    .putString("error", "Không bắt đầu được: " + e.getMessage()).apply();
            if (audio != null) { try { audio.release(); } catch (Exception ignored) {} audio = null; }
            stopForeground(true);
            stopSelf();
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
        int confirm = Math.max(1, (cfg.getInt("minSpeech", 180) + FRAME_MS - 1) / FRAME_MS);
        int preFrames = Math.max(0, (cfg.getInt("pre", 1000) + FRAME_MS - 1) / FRAME_MS);
        int tailFrames = Math.max(1, (cfg.getInt("tail", 1000) + FRAME_MS - 1) / FRAME_MS);
        int warnFrames = Math.max(1, cfg.getInt("warnSeconds", 20) * 1000 / FRAME_MS);
        int splitMinutes = cfg.getInt("splitMinutes", 15);
        if (splitMinutes < 1 || splitMinutes > 60) splitMinutes = 15;
        long splitFrames = (long) splitMinutes * 60 * 1000 / FRAME_MS;
        boolean saveRaw = cfg.getBoolean("backupRaw", true);

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
                    stateValue("level", 0);
                    continue;
                }
                if (wasPaused) {
                    pre.clear(); candidate.clear(); tail.clear();
                    active = false; wasPaused = false; silence = 0;
                }

                byte[] block = Arrays.copyOf(frame, FRAME_SIZE);
                elapsed++; partFrames++;
                if (current.raw != null) {
                    current.raw.write(block);
                    current.rawBytes += block.length;
                }

                double amp = rms(block);
                double db = 20 * Math.log10(Math.max(1.0e-9, amp / 32768.0));
                boolean loud = db >= threshold;
                silence = loud ? 0 : silence + 1;

                if (active) {
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
                    String capture = active ? "● ĐANG GIỮ ÂM THANH" : silence > warnFrames ?
                            "! KHÔNG CÓ TIẾNG NÓI – KIỂM TRA NGUỒN" : "ĐANG LỌC KHOẢNG LẶNG";
                    state().edit().putLong("elapsedMs", elapsed * FRAME_MS)
                            .putLong("keptMs", kept * 1000L / RecordingArchive.BYTES_PER_SEC)
                            .putInt("segments", segments).putInt("level", level)
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
                    .putBoolean("saving", true).putInt("level", 0)
                    .putLong("elapsedMs", elapsed * FRAME_MS)
                    .putLong("keptMs", kept * 1000L / RecordingArchive.BYTES_PER_SEC)
                    .putInt("segments", segments).putString("capture", "ĐANG LƯU FILE").apply();
            if (audio != null) {
                try { audio.stop(); } catch (Exception ignored) {}
                try { audio.release(); } catch (Exception ignored) {}
                audio = null;
            }
            if (current != null) {
                try {
                    RecordingArchive.close(current);
                    RecordingArchive.queueExport(this, current);
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
            stopping = false;
            state().edit().putBoolean("saving", false).putString("capture", "ĐÃ DỪNG").apply();
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
