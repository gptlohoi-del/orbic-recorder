
package com.example.orbicrecorder;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecorderService extends Service {

    static final String CHANNEL = "recording";

    volatile boolean recording = false;

    AudioRecord audio;
    Thread worker;
    File file;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationChannel channel =
            new NotificationChannel(
                CHANNEL,
                "Ghi âm đang chạy",
                NotificationManager.IMPORTANCE_LOW
            );

        getSystemService(NotificationManager.class)
            .createNotificationChannel(channel);
    }

    Notification notification() {

        Intent stop = new Intent(
            this,
            RecorderService.class
        ).setAction("STOP");

        PendingIntent pi = PendingIntent.getService(
            this,
            1,
            stop,
            PendingIntent.FLAG_UPDATE_CURRENT |
            PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL)
            .setContentTitle("Orbic Recorder đang ghi")
            .setContentText("Nhấn DỪNG để lưu WAV")
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .addAction(
                new Notification.Action.Builder(
                    null,
                    "DỪNG GHI",
                    pi
                ).build()
            )
            .build();
    }

    void state(String key, Object value) {

        android.content.SharedPreferences.Editor e =
            getSharedPreferences(
                "state",
                MODE_PRIVATE
            ).edit();

        if (value instanceof Integer) {
            e.putInt(key, (Integer) value);
        } else if (value instanceof Boolean) {
            e.putBoolean(key, (Boolean) value);
        } else {
            e.putString(key, String.valueOf(value));
        }

        e.apply();
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int id
    ) {

        String action =
            intent == null ? "" : intent.getAction();

        if ("STOP".equals(action)) {
            stopRecording();
            return START_NOT_STICKY;
        }

        if ("START".equals(action) && !recording) {

            startForeground(
                101,
                notification()
            );

            startRecording();
        }

        return START_NOT_STICKY;
    }

    void startRecording() {

        try {

            int rate = 16000;

            int min = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );

            if (min <= 0) {
                throw new IOException(
                    "Unsupported sample rate"
                );
            }

            audio = new AudioRecord.Builder()
                .setAudioSource(
                    MediaRecorder.AudioSource.MIC
                )
                .setAudioFormat(
                    new AudioFormat.Builder()
                        .setEncoding(
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        .setSampleRate(rate)
                        .setChannelMask(
                            AudioFormat.CHANNEL_IN_MONO
                        )
                        .build()
                )
                .setBufferSizeInBytes(
                    Math.max(min * 2, 4096)
                )
                .build();

            if (
                audio.getState() !=
                AudioRecord.STATE_INITIALIZED
            ) {
                throw new IOException(
                    "AudioRecord not initialized"
                );
            }

            // Thu muc tam rieng cua ung dung
            File dir = new File(
                getExternalFilesDir(null),
                "Recordings"
            );

            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException(
                    "Cannot create directory"
                );
            }

            String filename =
                "orbic_" +
                new SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(new Date()) +
                ".wav";

            file = new File(dir, filename);

            state("file", file.getAbsolutePath());
            state("error", "Không");
            state("level", 0);

            audio.startRecording();

            recording = true;
            state("running", true);

            AudioDeviceInfo device =
                audio.getRoutedDevice();

            state(
                "source",
                device == null
                    ? "Chưa xác định"
                    : device.getProductName() +
                      " (type " +
                      device.getType() +
                      ")"
            );

            worker = new Thread(
                () -> writeAudio(rate),
                "WavWriter"
            );

            worker.start();

        } catch (Exception ex) {

            state("error", ex.toString());
            state("running", false);

            recording = false;

            if (audio != null) {
                audio.release();
                audio = null;
            }

            stopForeground(true);
            stopSelf();
        }
    }

    void writeAudio(int rate) {

        long bytes = 0;
        byte[] buf = new byte[4096];

        boolean saved = false;

        try (
            RandomAccessFile out =
                new RandomAccessFile(file, "rw")
        ) {

            out.setLength(0);

            // Du phong 44 byte WAV header
            out.write(new byte[44]);

            while (recording) {

                int n = audio.read(
                    buf,
                    0,
                    buf.length
                );

                if (n > 0) {

                    out.write(buf, 0, n);
                    bytes += n;

                    // Tinh muc tin hieu am thanh
                    long energy = 0;

                    for (
                        int i = 0;
                        i + 1 < n;
                        i += 2
                    ) {

                        int sample = (short)(
                            (buf[i] & 255) |
                            (buf[i + 1] << 8)
                        );

                        energy +=
                            (long) sample * sample;
                    }

                    int count = Math.max(
                        1,
                        n / 2
                    );

                    int level = (int) Math.min(
                        100,
                        Math.sqrt(
                            (double) energy / count
                        ) * 100 / 32768
                    );

                    state("level", level);

                } else if (n < 0) {

                    state(
                        "error",
                        "AudioRecord read error: " + n
                    );

                    break;
                }
            }

            // Hoan tat WAV header
            writeHeader(
                out,
                bytes,
                rate
            );

            saved = true;

        } catch (Exception ex) {

            state("error", ex.toString());

        } finally {

            recording = false;

            state("running", false);
            state("level", 0);

            if (audio != null) {

                try {
                    audio.stop();
                } catch (Exception ignored) {}

                audio.release();
                audio = null;
            }

            // Chi xuat khi WAV da hoan tat
            if (saved) {

                try {

                    exportToMusic(file);

                } catch (Exception ex) {

                    state(
                        "error",
                        "Export failed: " +
                        ex.toString()
                    );
                }
            }

            stopForeground(true);
            stopSelf();
        }
    }

    // Xuat WAV vao Music/OrbicRecorder
    void exportToMusic(File source)
        throws IOException {

        ContentValues values =
            new ContentValues();

        values.put(
            MediaStore.Audio.Media.DISPLAY_NAME,
            source.getName()
        );

        values.put(
            MediaStore.Audio.Media.MIME_TYPE,
            "audio/wav"
        );

        values.put(
            MediaStore.Audio.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MUSIC +
            "/OrbicRecorder"
        );

        values.put(
            MediaStore.Audio.Media.IS_PENDING,
            1
        );

        Uri uri = getContentResolver().insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            values
        );

        if (uri == null) {
            throw new IOException(
                "Cannot create media file"
            );
        }

        boolean success = false;

        try {

            try (
                InputStream in =
                    new FileInputStream(source);

                OutputStream out =
                    getContentResolver()
                        .openOutputStream(uri)
            ) {

                if (out == null) {
                    throw new IOException(
                        "Cannot open output"
                    );
                }

                byte[] buffer = new byte[8192];

                int n;

                while (
                    (n = in.read(buffer)) != -1
                ) {

                    out.write(
                        buffer,
                        0,
                        n
                    );
                }

                out.flush();
            }

            // Cong khai file sau khi ghi xong
            ContentValues completed =
                new ContentValues();

            completed.put(
                MediaStore.Audio.Media.IS_PENDING,
                0
            );

            int updated = getContentResolver().update(
                uri,
                completed,
                null,
                null
            );

            if (updated <= 0) {
                throw new IOException(
                    "Cannot publish WAV file"
                );
            }

            success = true;

            state(
                "file",
                "Music/OrbicRecorder/" +
                source.getName()
            );

            state("error", "Không");

            // Xoa file tam sau khi xuat thanh cong
            if (!source.delete()) {
                state(
                    "cleanup",
                    "Không xóa được file tạm"
                );
            }

        } finally {

            if (!success) {

                try {
                    getContentResolver().delete(
                        uri,
                        null,
                        null
                    );
                } catch (Exception ignored) {}
            }
        }
    }

    // Tao WAV header
    void writeHeader(
        RandomAccessFile out,
        long size,
        int rate
    ) throws IOException {

        out.seek(0);

        out.writeBytes("RIFF");
        le32(out, size + 36);

        out.writeBytes("WAVEfmt ");
        le32(out, 16);

        le16(out, 1);
        le16(out, 1);

        le32(out, rate);
        le32(out, rate * 2);

        le16(out, 2);
        le16(out, 16);

        out.writeBytes("data");
        le32(out, size);
    }

    void le16(
        RandomAccessFile out,
        long value
    ) throws IOException {

        out.write((int) value & 255);
        out.write((int)(value >> 8) & 255);
    }

    void le32(
        RandomAccessFile out,
        long value
    ) throws IOException {

        le16(out, value);
        le16(out, value >> 16);
    }

    void stopRecording() {

        recording = false;
        state("running", false);
    }

    @Override
    public void onDestroy() {

        stopRecording();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
