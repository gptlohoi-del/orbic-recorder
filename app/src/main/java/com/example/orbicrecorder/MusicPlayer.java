package com.example.orbicrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/** Music player on Android's main thread, with one-shot playback and two end fades. */
final class MusicPlayer {
    interface Listener { void ended(); }
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private final Listener listener;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private boolean focused;
    private volatile String status = "idle";
    private volatile String title = "";
    private volatile int positionMs, durationMs;
    private int fadeInMs = 3000, fadeOutMs = 5000, volume = 80;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player == null || !"playing".equals(status)) return;
            try {
                positionMs = player.getCurrentPosition();
                durationMs = player.getDuration();
                float gainIn = fadeInMs == 0 ? 1f : Math.min(1f, positionMs / (float) fadeInMs);
                int remaining = Math.max(0, durationMs - positionMs);
                float gainOut = fadeOutMs == 0 ? 1f : Math.min(1f, remaining / (float) fadeOutMs);
                float gain = Math.max(0f, Math.min(1f, Math.min(gainIn, gainOut))) * volume / 100f;
                player.setVolume(gain, gain);
                handler.postDelayed(this, 100);
            } catch (IllegalStateException exception) { stop(true); }
        }
    };

    MusicPlayer(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    String getStatus() { return status; }
    boolean isActive() { return !"idle".equals(status); }
    void writeSnapshot(JSONObject json) throws Exception {
        json.put("musicStatus", status);
        json.put("musicTitle", title);
        json.put("musicPosition", positionMs);
        json.put("musicDuration", durationMs);
    }

    void play(Uri uri, String label, int in, int out, int playbackVolume) {
        stop(false);
        fadeInMs = in;
        fadeOutMs = out;
        volume = playbackVolume;
        title = label;
        status = "loading";
        positionMs = 0;
        durationMs = 0;
        try {
            int focusResult;
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            handler.post(() -> stop(true));
                        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            handler.post(() -> { if (player != null) player.setVolume(0.1f, 0.1f); });
                        }
                    }).build();
            focusResult = audioManager.requestAudioFocus(focus);
            focused = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focused) throw new IllegalStateException("Ứng dụng khác đang sử dụng âm thanh");
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setDataSource(context, uri);
            player.setOnPreparedListener(p -> {
                if (player != p) return;
                durationMs = p.getDuration();
                p.setVolume(0f, 0f);
                p.start();
                status = "playing";
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            });
            player.setOnCompletionListener(p -> stop(true));
            player.setOnErrorListener((p, what, extra) -> { stop(true); return true; });
            player.prepareAsync();
        } catch (Exception e) {
            stop(true);
            throw new IllegalStateException("Không phát được nhạc: " + e.getMessage(), e);
        }
    }

    void pause() {
        if (player != null && "playing".equals(status)) {
            player.pause();
            status = "paused";
            handler.removeCallbacks(ticker);
        }
    }
    void resume() {
        if (player != null && "paused".equals(status)) {
            player.start();
            status = "playing";
            handler.post(ticker);
        }
    }
    void stop(boolean notify) {
        handler.removeCallbacks(ticker);
        boolean wasActive = isActive();
        status = "idle";
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (focused && focus != null) audioManager.abandonAudioFocusRequest(focus);
        focused = false;
        focus = null;
        positionMs = 0;
        durationMs = 0;
        if (notify && wasActive) listener.ended();
    }
}
