package com.example.orbicrecorder;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable native WAV archive, kept in the APK (not in OTA web assets).
 * Files are written in an app-private staging directory, headers are periodically
 * repaired/synced, and closed segments are exported on a separate serial worker.
 * Any stage files left after a process crash are repaired at next launch.
 */
public final class RecordingArchive {
    public static final int SAMPLE_RATE = 16000;
    public static final int BYTES_PER_SEC = SAMPLE_RATE * 2;
    private static final String PREFIX = "orbic_";
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbicArchiveDisk");
        t.setDaemon(false);
        return t;
    });
    private static final AtomicBoolean RECOVERING = new AtomicBoolean(false);
    private RecordingArchive() {}

    private static SharedPreferences state(Context c) { return c.getSharedPreferences("state", Context.MODE_PRIVATE); }
    public static boolean isRecovering() { return RECOVERING.get(); }

    public static File folder(Context context) throws IOException {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "RecordingsTemp");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục ghi tạm");
        return dir;
    }

    public static String makeSessionId() {
        return new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new java.util.Date());
    }

    public static void saveSession(Context context, String session, String treeUri,
                                   String folderName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("treeUri", treeUri == null ? "" : treeUri);
        properties.setProperty("folderName", folderName == null ? "Music/OrbicRecorder" : folderName);
        File metadata = new File(folder(context), PREFIX + session + ".properties");
        try (FileOutputStream output = new FileOutputStream(metadata)) {
            properties.store(output, "Orbic Recorder output folder for crash recovery");
            output.getFD().sync();
        }
    }

    public static final class Segment {
        final String session;
        final int part;
        final File voiceFile;
        final File rawFile;
        final RandomAccessFile voice;
        final RandomAccessFile raw;
        long voiceBytes;
        long rawBytes;
        boolean closed;

        Segment(String session, int part, File voiceFile, File rawFile,
                RandomAccessFile voice, RandomAccessFile raw) {
            this.session = session;
            this.part = part;
            this.voiceFile = voiceFile;
            this.rawFile = rawFile;
            this.voice = voice;
            this.raw = raw;
        }
    }

    private static RandomAccessFile openWav(File file) throws IOException {
        RandomAccessFile handle = new RandomAccessFile(file, "rw");
        handle.setLength(0);
        handle.write(new byte[44]);
        return handle;
    }

    public static Segment open(Context context, String session, int part, boolean backup)
            throws IOException {
        String name = PREFIX + session + String.format(Locale.US, "_p%03d", part);
        File filtered = new File(folder(context), name + "_voice.wav");
        File original = backup ? new File(folder(context), name + "_raw.wav") : null;
        RandomAccessFile v = openWav(filtered);
        RandomAccessFile r = null;
        try {
            if (original != null) r = openWav(original);
            return new Segment(session, part, filtered, original, v, r);
        } catch (IOException error) {
            try { v.close(); } catch (IOException ignored) {}
            throw error;
        }
    }

    public static void header(RandomAccessFile out, long pcmBytes) throws IOException {
        long savedPosition = out.getFilePointer();
        out.seek(0);
        out.writeBytes("RIFF");
        write32(out, pcmBytes + 36);
        out.writeBytes("WAVEfmt ");
        write32(out, 16);
        write16(out, 1);
        write16(out, 1);
        write32(out, SAMPLE_RATE);
        write32(out, BYTES_PER_SEC);
        write16(out, 2);
        write16(out, 16);
        out.writeBytes("data");
        write32(out, pcmBytes);
        out.seek(savedPosition);
    }

    private static void write16(RandomAccessFile file, long n) throws IOException {
        file.write((int)n & 255);
        file.write((int)(n >> 8) & 255);
    }
    private static void write32(RandomAccessFile file, long n) throws IOException {
        write16(file, n);
        write16(file, n >> 16);
    }

    public static void sync(Segment segment) throws IOException {
        header(segment.voice, segment.voiceBytes);
        segment.voice.getFD().sync();
        if (segment.raw != null) {
            header(segment.raw, segment.rawBytes);
            segment.raw.getFD().sync();
        }
    }

    public static void close(Segment segment) throws IOException {
        if (segment == null || segment.closed) return;
        IOException failure = null;
        try { sync(segment); } catch (IOException e) { failure = e; }
        try { segment.voice.close(); } catch (IOException e) { if (failure == null) failure = e; }
        if (segment.raw != null) {
            try { segment.raw.close(); } catch (IOException e) { if (failure == null) failure = e; }
        }
        segment.closed = true;
        if (failure != null) throw failure;
    }

    public static Future<?> queueExport(Context context, Segment segment) {
        Context application = context.getApplicationContext();
        return DISK.submit(() -> exportSegment(application, segment));
    }

    public static Future<?> barrier() { return DISK.submit(() -> {}); }

    /** Run only while recorder is inactive. Never touch an actively written WAV. */
    public static void recoverOnLaunch(Context context) {
        if (RecorderService.isProcessCapturing() || !RECOVERING.compareAndSet(false, true)) return;
        Context application = context.getApplicationContext();
        state(application).edit().putBoolean("recovering", true)
                .putString("recoveryStatus", "Đang kiểm tra bản ghi sau sự cố...").apply();
        DISK.execute(() -> {
            int recovered = 0;
            int failed = 0;
            try {
                File[] leftovers = folder(application).listFiles((dir, name) ->
                        name.endsWith(".wav") && (name.startsWith(PREFIX) || name.startsWith("orbic_voice_")));
                if (leftovers != null) {
                    Arrays.sort(leftovers);
                    for (File file : leftovers) {
                        // Do not open any in-progress file if recording started meanwhile.
                        if (RecorderService.isProcessCapturing()) break;
                        try {
                            exportFile(application, file, inferSession(file.getName()));
                            recovered++;
                        } catch (Exception failure) {
                            failed++;
                            state(application).edit().putString("error",
                                    "Khôi phục chưa hoàn tất: " + file.getName() + ": " + failure.getMessage()).apply();
                        }
                    }
                }
            } catch (Exception error) {
                failed++;
                state(application).edit().putString("error", "Khôi phục: " + error.getMessage()).apply();
            } finally {
                state(application).edit().putBoolean("recovering", false)
                        .putString("recoveryStatus", "Đã khôi phục " + recovered + " file"
                                + (failed > 0 ? ", còn " + failed + " file cần xử lý" : ""))
                        .apply();
                RECOVERING.set(false);
            }
        });
    }

    private static String inferSession(String name) {
        if (!name.startsWith(PREFIX)) return "";
        int p = name.indexOf("_p", PREFIX.length());
        return p > 0 ? name.substring(PREFIX.length(), p) : "";
    }

    private static void exportSegment(Context context, Segment segment) {
        try {
            // Preserve original first: a filtered file can always be recreated from raw.
            if (segment.rawFile != null) exportFile(context, segment.rawFile, segment.session);
            exportFile(context, segment.voiceFile, segment.session);
            state(context).edit().putInt("savedParts", segment.part).apply();
        } catch (Exception e) {
            state(context).edit().putString("error", "Lưu đoạn " + segment.part
                    + " thất bại; file tạm được giữ để khôi phục: " + e.getMessage()).apply();
        }
    }

    private static Properties sessionSettings(Context context, String session) {
        Properties properties = new Properties();
        if (!session.isEmpty()) {
            File file = new FileUnchecked(context, PREFIX + session + ".properties").file;
            if (file != null && file.isFile()) {
                try (InputStream input = new FileInputStream(file)) { properties.load(input); }
                catch (Exception ignored) {}
            }
        }
        return properties;
    }

    // Helper avoids throwing during optional metadata lookup.
    private static final class FileUnchecked {
        final File file;
        FileUnchecked(Context c, String name) {
            File value;
            try { value = new File(folder(c), name); } catch (IOException e) { value = null; }
            file = value;
        }
    }

    private static void repairHeader(File file) throws IOException {
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            long valid = Math.max(0, out.length() - 44);
            valid -= valid % 2;
            if (valid > 0xFFFFFFFFL - 36)
                throw new IOException("WAV vượt giới hạn RIFF 4 GB");
            out.setLength(valid + 44);
            header(out, valid);
            out.getFD().sync();
        }
    }

    private static void exportFile(Context context, File source, String session) throws Exception {
        if (source == null || !source.exists()) return;
        repairHeader(source);
        // Empty voice files are expected for segments without speech.
        if (source.length() <= 44) {
            if (!source.delete()) throw new IOException("Không xóa được file WAV rỗng");
            return;
        }
        boolean raw = source.getName().endsWith("_raw.wav");
        Properties properties = sessionSettings(context, session);
        SharedPreferences config = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String tree = properties.getProperty("treeUri", config.getString("treeUri", ""));
        Uri destination = null;
        if (!tree.isEmpty()) {
            try { destination = exportToFolder(context, source, tree); }
            catch (Exception folderError) {
                state(context).edit().putString("error", "Không lưu được thư mục tùy chọn; chuyển sang Music/OrbicRecorder: "
                        + folderError.getMessage()).apply();
            }
        }
        if (destination == null) destination = exportToMusic(context, source, raw);
        SharedPreferences.Editor editor = state(context).edit();
        if (raw) editor.putString("lastRaw", source.getName());
        else editor.putString("uri", destination.toString()).putString("file", source.getName());
        editor.apply();
        if (!source.delete()) throw new IOException("Đã xuất WAV nhưng không xóa được file tạm: " + source.getName());
    }

    private static Uri exportToMusic(Context context, File source, boolean raw) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/OrbicRecorder/" + (raw ? "Goc" : "DaLoc"));
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri item = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IOException("Không tạo được file trong Music");
        boolean succeeded = false;
        try {
            copy(context, source, item);
            ContentValues visible = new ContentValues();
            visible.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (context.getContentResolver().update(item, visible, null, null) < 1)
                throw new IOException("Không công bố được WAV");
            succeeded = true;
            return item;
        } finally {
            if (!succeeded) try { context.getContentResolver().delete(item, null, null); }
            catch (Exception ignored) {}
        }
    }

    private static Uri exportToFolder(Context context, File source, String treeString) throws Exception {
        Uri tree = Uri.parse(treeString);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Uri dest = DocumentsContract.createDocument(context.getContentResolver(), parent,
                "audio/wav", source.getName());
        if (dest == null) throw new IOException("Không tạo được file trong thư mục đã chọn");
        boolean succeeded = false;
        try {
            copy(context, source, dest);
            succeeded = true;
            return dest;
        } finally {
            if (!succeeded) try { DocumentsContract.deleteDocument(context.getContentResolver(), dest); }
            catch (Exception ignored) {}
        }
    }

    private static void copy(Context context, File from, Uri to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = context.getContentResolver().openOutputStream(to, "w")) {
            if (out == null) throw new IOException("Không mở được file xuất");
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
        }
    }
}
