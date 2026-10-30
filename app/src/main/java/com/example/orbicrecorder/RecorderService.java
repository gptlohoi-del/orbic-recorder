package com.example.orbicrecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/**
 * One microphone capture stream, simple amplitude-based voice activity detection.
 * Each accepted section is joined into one WAV file, with configurable pre- and post-roll.
 * This is NOT an AI speech recognizer: any sufficiently loud sound can be accepted.
 */
public class RecorderService extends Service {
    private static final String CHANNEL = "orbic_recording_v4";
    private static final int NOTIFICATION_ID = 141;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SIZE = SAMPLE_RATE * 2 * FRAME_MS / 1000; // 640 PCM bytes

    private volatile boolean recording = false;
    private volatile boolean paused = false;
    private volatile boolean stopping = false;
    private AudioRecord audio;
    private Thread writerThread;
    private File tempFile;
    private String destinationTree = "";

    private SharedPreferences state() { return getSharedPreferences("state", MODE_PRIVATE); }
    private SharedPreferences config() { return getSharedPreferences("config", MODE_PRIVATE); }

    private void setState(String key, Object value) {
        SharedPreferences.Editor editor = state().edit();
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        editor.apply();
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Orbic Recorder đang ghi",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent stop = new Intent(this, RecorderService.class).setAction("STOP");
        Intent toggle = new Intent(this, RecorderService.class).setAction(paused ? "RESUME" : "PAUSE");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stopIntent = PendingIntent.getService(this, 4, stop, flags);
        PendingIntent toggleIntent = PendingIntent.getService(this, 5, toggle, flags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Orbic Recorder v4.0")
                .setContentText(paused ? "Đang tạm dừng" : stopping ? "Đang lưu file" : "Đang lọc khoảng lặng")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, paused ? "TIẾP TỤC" : "PAUSE", toggleIntent).build())
                .addAction(new Notification.Action.Builder(null, "DỪNG VÀ LƯU", stopIntent).build())
                .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if ("START".equals(action)) {
            if (!recording && !stopping) {
                startForeground(NOTIFICATION_ID, notification());
                startCapture();
            }
        } else if ("STOP".equals(action)) {
            if (recording) {
                stopping = true;
                recording = false;
                setState("saving", true);
                setState("capture", "ĐANG LƯU FILE");
                updateNotification();
            } else if (!stopping) stopSelf();
        } else if ("PAUSE".equals(action)) {
            if (recording && !stopping) {
                paused = true;
                setState("paused", true);
                setState("capture", "TẠM DỪNG");
                updateNotification();
            }
        } else if ("RESUME".equals(action)) {
            if (recording && !stopping) {
                paused = false;
                setState("paused", false);
                setState("capture", "ĐANG CHỜ TIẾNG NÓI");
                updateNotification();
            }
        }
        return START_NOT_STICKY;
    }

    private void startCapture() {
        try {
            int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IOException("Thiết bị không hỗ trợ 16 kHz mono");

            audio = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(minimum * 2, FRAME_SIZE * 4))
                    .build();
            if (audio.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IOException("Không khởi tạo được micro");

            File base = getExternalFilesDir(null);
            if (base == null) base = getFilesDir();
            File directory = new File(base, "RecordingsTemp");
            if (!directory.exists() && !directory.mkdirs())
                throw new IOException("Không tạo được thư mục tạm");
            String name = "orbic_voice_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                    .format(new Date()) + ".wav";
            tempFile = new File(directory, name);
            destinationTree = config().getString("treeUri", "");

            audio.startRecording();
            recording = true;
            paused = false;
            stopping = false;
            SharedPreferences.Editor editor = state().edit();
            editor.putBoolean("running", true).putBoolean("saving", false).putBoolean("paused", false)
                    .putString("error", "").putString("capture", "ĐANG CHỜ TIẾNG NÓI")
                    .putString("file", "Đang ghi: " + name)
                    .putLong("elapsedMs", 0L).putLong("keptMs", 0L).putInt("segments", 0)
                    .putInt("level", 0).putString("db", "—");
            AudioDeviceInfo device = audio.getRoutedDevice();
            editor.putString("source", device == null ? "Chưa xác định" :
                    String.valueOf(device.getProductName()) + " (type " + device.getType() + ")");
            editor.apply();
            writerThread = new Thread(this::writeVoice, "OrbicVoiceWriter");
            writerThread.start();
        } catch (Exception error) {
            recording = false;
            paused = false;
            stopping = false;
            setState("running", false);
            setState("saving", false);
            setState("error", "Không bắt đầu ghi được: " + error.getMessage());
            if (audio != null) {
                try { audio.release(); } catch (Exception ignored) {}
                audio = null;
            }
            stopForeground(true);
            stopSelf();
        }
    }

    private static void trimDeque(Deque<byte[]> frames, int maximum) {
        while (frames.size() > maximum) frames.removeFirst();
    }

    private static long writeFrames(RandomAccessFile out, Deque<byte[]> frames) throws IOException {
        long size = 0;
        for (byte[] frame : frames) {
            out.write(frame);
            size += frame.length;
        }
        frames.clear();
        return size;
    }

    private static double rms(byte[] frame) {
        long power = 0;
        for (int i = 0; i + 1 < frame.length; i += 2) {
            int sample = (short) ((frame[i] & 255) | (frame[i + 1] << 8));
            power += (long) sample * sample;
        }
        return Math.sqrt((double) power / (frame.length / 2));
    }

    private void writeVoice() {
        SharedPreferences cfg = config();
        int threshold = cfg.getInt("threshold", -38);
        int confirmFrames = Math.max(1, (cfg.getInt("minSpeech", 180) + FRAME_MS - 1) / FRAME_MS);
        int preFrames = Math.max(0, (cfg.getInt("pre", 1000) + FRAME_MS - 1) / FRAME_MS);
        int tailFrames = Math.max(1, (cfg.getInt("tail", 1000) + FRAME_MS - 1) / FRAME_MS);
        int warnFrames = Math.max(1, cfg.getInt("warnSeconds", 20) * 1000 / FRAME_MS);

        Deque<byte[]> pre = new ArrayDeque<>();
        Deque<byte[]> candidate = new ArrayDeque<>();
        Deque<byte[]> pendingSilence = new ArrayDeque<>();
        boolean active = false, wasPaused = false, completed = false;
        long keptBytes = 0L, elapsedFrames = 0L, sinceSignal = 0L;
        int segments = 0;
        byte[] frame = new byte[FRAME_SIZE];

        try (RandomAccessFile out = new RandomAccessFile(tempFile, "rw")) {
            out.setLength(0);
            out.write(new byte[44]);
            while (recording) {
                int filled = 0;
                while (recording && filled < FRAME_SIZE) {
                    int n = audio.read(frame, filled, FRAME_SIZE - filled);
                    if (n > 0) filled += n;
                    else if (n < 0) throw new IOException("AudioRecord.read: " + n);
                }
                if (filled != FRAME_SIZE) break;

                if (paused) {
                    if (!wasPaused) {
                        if (active) keptBytes += writeFrames(out, pendingSilence);
                        pendingSilence.clear();
                        candidate.clear();
                        pre.clear();
                        active = false;
                        wasPaused = true;
                    }
                    setState("level", 0);
                    continue;
                }
                if (wasPaused) {
                    pre.clear();
                    candidate.clear();
                    pendingSilence.clear();
                    active = false;
                    wasPaused = false;
                    sinceSignal = 0;
                }

                elapsedFrames++;
                byte[] block = Arrays.copyOf(frame, FRAME_SIZE);
                double amplitude = rms(block);
                double db = 20 * Math.log10(Math.max(1.0e-9, amplitude / 32768.0));
                boolean loud = db >= threshold;
                sinceSignal = loud ? 0 : sinceSignal + 1;

                if (active) {
                    if (loud) {
                        keptBytes += writeFrames(out, pendingSilence);
                        out.write(block);
                        keptBytes += block.length;
                    } else {
                        pendingSilence.addLast(block);
                        if (pendingSilence.size() > tailFrames) {
                            // Preserve only the configured tail, then discard the rest of the silence.
                            for (int i = 0; i < tailFrames; i++) {
                                byte[] b = pendingSilence.removeFirst();
                                out.write(b);
                                keptBytes += b.length;
                            }
                            pre.clear();
                            while (!pendingSilence.isEmpty()) pre.addLast(pendingSilence.removeFirst());
                            trimDeque(pre, preFrames);
                            active = false;
                        }
                    }
                } else if (loud) {
                    candidate.addLast(block);
                    if (candidate.size() >= confirmFrames) {
                        active = true;
                        segments++;
                        keptBytes += writeFrames(out, pre);
                        keptBytes += writeFrames(out, candidate);
                    }
                } else {
                    while (!candidate.isEmpty()) pre.addLast(candidate.removeFirst());
                    pre.addLast(block);
                    trimDeque(pre, preFrames);
                }

                if (keptBytes >= 0xFFFFFF00L)
                    throw new IOException("WAV đạt giới hạn 4GB. Hãy ghi các phiên ngắn hơn.");
                if (elapsedFrames % 5 == 0) {
                    int displayLevel = (int) Math.max(0, Math.min(100, (db + 60.0) * 100.0 / 60.0));
                    String status = active ? "● ĐANG GIỮ ÂM THANH" : sinceSignal > warnFrames ?
                            "! KHÔNG CÓ TIẾNG NÓI – KIỂM TRA NGUỒN" : "ĐANG LỌC KHOẢNG LẶNG";
                    state().edit().putLong("elapsedMs", elapsedFrames * FRAME_MS)
                            .putLong("keptMs", keptBytes * 1000 / (SAMPLE_RATE * 2))
                            .putInt("segments", segments).putInt("level", displayLevel)
                            .putString("db", String.format(Locale.US, "%.1f", Math.max(-120.0, db)))
                            .putString("capture", status).apply();
                }
                // Periodically write a valid WAV header even before STOP, reducing crash losses.
                if (elapsedFrames % 250 == 0) {
                    long position = out.getFilePointer();
                    writeHeader(out, keptBytes, SAMPLE_RATE);
                    out.seek(position);
                }
            }
            if (active && !pendingSilence.isEmpty()) keptBytes += writeFrames(out, pendingSilence);
            writeHeader(out, keptBytes, SAMPLE_RATE);
            completed = true;
        } catch (Exception error) {
            setState("error", "Ghi âm: " + error.getMessage());
        } finally {
            recording = false;
            paused = false;
            setState("running", false);
            setState("paused", false);
            setState("level", 0);
            setState("capture", "ĐÃ DỪNG");
            state().edit().putLong("elapsedMs", elapsedFrames * FRAME_MS)
                    .putLong("keptMs", keptBytes * 1000 / (SAMPLE_RATE * 2))
                    .putInt("segments", segments).apply();
            if (audio != null) {
                try { audio.stop(); } catch (Exception ignored) {}
                try { audio.release(); } catch (Exception ignored) {}
                audio = null;
            }
            if (completed) {
                try {
                    if (keptBytes > 0) {
                        Uri saved;
                        String location;
                        try {
                            if (destinationTree.isEmpty()) throw new IllegalStateException("Dùng thư mục mặc định");
                            saved = exportToChosenFolder(tempFile, destinationTree);
                            location = config().getString("folderLabel", "Thư mục tùy chọn");
                        } catch (Exception customError) {
                            // Never silently drop a recording if the chosen folder is unavailable.
                            saved = exportToMusic(tempFile);
                            location = "Music/OrbicRecorder";
                            if (!destinationTree.isEmpty()) setState("error", "Không ghi được thư mục đã chọn; đã lưu dự phòng trong Music/OrbicRecorder");
                        }
                        state().edit().putString("uri", saved.toString())
                                .putString("file", location + "/" + tempFile.getName()).apply();
                        if (!tempFile.delete()) setState("error", "Đã lưu, nhưng không xóa được file tạm");
                    } else {
                        setState("file", "Không có đoạn vượt ngưỡng. Không tạo file WAV mới.");
                        if (!tempFile.delete()) setState("error", "Không xóa được file ghi tạm rỗng");
                    }
                } catch (Exception exportError) {
                    setState("error", "Không xuất được WAV: " + exportError.getMessage());
                    setState("file", "Bản WAV tạm được giữ: " + tempFile.getAbsolutePath());
                }
            } else {
                setState("file", "Ghi âm bị lỗi; file tạm được giữ nếu đã tạo");
            }
            stopping = false;
            setState("saving", false);
            stopForeground(true);
            stopSelf();
        }
    }

    private Uri exportToMusic(File source) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/OrbicRecorder");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("MediaStore không tạo được file đích");
        boolean complete = false;
        try {
            copyFile(source, uri);
            ContentValues published = new ContentValues();
            published.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (getContentResolver().update(uri, published, null, null) < 1)
                throw new IOException("Không công bố được file WAV trong MediaStore");
            complete = true;
            return uri;
        } finally {
            if (!complete) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
            }
        }
    }

    private Uri exportToChosenFolder(File source, String treeValue) throws Exception {
        Uri tree = Uri.parse(treeValue);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri file = DocumentsContract.createDocument(getContentResolver(), parent, "audio/wav", source.getName());
        if (file == null) throw new IOException("Không thể tạo file trong thư mục đã chọn");
        boolean complete = false;
        try {
            copyFile(source, file);
            complete = true;
            return file;
        } finally {
            if (!complete) {
                try { DocumentsContract.deleteDocument(getContentResolver(), file); } catch (Exception ignored) {}
            }
        }
    }

    private void copyFile(File source, Uri destination) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Không mở được file đích");
            byte[] bytes = new byte[16384];
            int count;
            while ((count = input.read(bytes)) >= 0) {
                if (count > 0) output.write(bytes, 0, count);
            }
            output.flush();
        }
    }

    private static void writeHeader(RandomAccessFile output, long dataSize, int rate) throws IOException {
        output.seek(0);
        output.writeBytes("RIFF");
        little32(output, dataSize + 36);
        output.writeBytes("WAVEfmt ");
        little32(output, 16);
        little16(output, 1);
        little16(output, 1);
        little32(output, rate);
        little32(output, rate * 2);
        little16(output, 2);
        little16(output, 16);
        output.writeBytes("data");
        little32(output, dataSize);
    }
    private static void little16(RandomAccessFile out, long value) throws IOException {
        out.write((int) value & 0xFF);
        out.write((int) (value >> 8) & 0xFF);
    }
    private static void little32(RandomAccessFile out, long value) throws IOException {
        little16(out, value);
        little16(out, value >> 16);
    }

    @Override public void onDestroy() {
        recording = false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
