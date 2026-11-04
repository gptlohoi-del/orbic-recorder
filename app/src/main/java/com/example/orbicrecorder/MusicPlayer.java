package com.example.orbicrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

/**
 * One-shot playback. Both PAUSE and STOP fade out while playback continues.
 * The fade is capped at the actual remaining playback duration; a natural
 * onCompletion callback always wins when the song finishes first.
 * All methods run on Android's main Looper.
 */
final class MusicPlayer {
    interface Listener {
        void ended();
        void paused();
    }

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
    private int commandFadeStart, commandFadeDuration;
    private long resumeFadeStartMs = -1L;
    private int resumeFadeDurationMs = 2000;
    private float commandFadeGain = 1f;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player == null || !("playing".equals(status) || isFading())) return;
            try {
                positionMs = player.getCurrentPosition();
                durationMs = player.getDuration();
                int remaining = Math.max(0, durationMs - positionMs);
                float gainIn = fadeInMs <= 0 ? 1f : Math.min(1f, positionMs / (float) fadeInMs);
                float gainOut = fadeOutMs <= 0 ? 1f : Math.min(1f, remaining / (float) fadeOutMs);
                // TIẾP: fade-in mềm dựa trên đồng hồ đơn điệu, không dựa vào
                // vị trí phát nhạc (có thể cập nhật không đều sau PAUSE).
                float resumeGain = 1f;
                if (resumeFadeStartMs >= 0L) {
                    float progress = resumeFadeDurationMs <= 0 ? 1f :
                            Math.min(1f, Math.max(0f,
                                    (SystemClock.uptimeMillis() - resumeFadeStartMs)
                                            / (float) resumeFadeDurationMs));
                    // Smoothstep: tăng êm từ 0, không nhảy âm đầu đoạn.
                    resumeGain = progress * progress * (3f - 2f * progress);
                    if (progress >= 1f) resumeFadeStartMs = -1L;
                }
                float command = 1f;
                if (isFading()) {
                    float fraction = commandFadeDuration <= 0 ? 1f :
                            Math.min(1f, Math.max(0f,
                                    (positionMs - commandFadeStart) / (float) commandFadeDuration));
                    command = commandFadeGain * (1f - fraction);
                }
                float gain = Math.max(0f, Math.min(1f, Math.min(gainIn, gainOut)
                        * resumeGain * command * volume / 100f));
                player.setVolume(gain, gain);
                if (isFading() && (positionMs - commandFadeStart >= commandFadeDuration
                        || remaining == 0)) {
                    finishFade();
                    return;
                }
                handler.postDelayed(this, 40);
            } catch (IllegalStateException exception) {
                stop(true);
            }
        }
    };

    MusicPlayer(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    String getStatus() { return status; }
    boolean isActive() { return !"idle".equals(status); }
    boolean isBlockingMic() { return isActive() && !"paused".equals(status); }
    private boolean isFading() { return "fadingPause".equals(status) || "fadingStop".equals(status); }

    void writeSnapshot(JSONObject json) throws Exception {
        json.put("musicStatus", status);
        json.put("musicTitle", title);
        json.put("musicPosition", positionMs);
        json.put("musicDuration", durationMs);
    }

    void play(Uri uri, String label, int in, int out, int playbackVolume) {
        stop(false);
        fadeInMs = Math.max(0, in);
        fadeOutMs = Math.max(0, out);
        volume = Math.max(0, Math.min(100, playbackVolume));
        title = label;
        status = "loading";
        positionMs = 0;
        durationMs = 0;
        resumeFadeStartMs = -1L;
        try {
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
            focused = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focused) throw new IllegalStateException("Ứng dụng khác đang sử dụng âm thanh");
            player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setDataSource(context, uri);
            player.setOnPreparedListener(p -> {
                if (player != p || !"loading".equals(status)) return;
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

    /** Seek only within a prepared playback; never interfere with an active fade. */
    void seekTo(int requestedMs) {
        if (player == null || !("playing".equals(status) || "paused".equals(status))) return;
        try {
            int duration = player.getDuration();
            if (duration <= 0) return;
            int target = Math.max(0, Math.min(requestedMs, duration - 1));
            player.seekTo(target);
            positionMs = target;
        } catch (IllegalStateException ignored) { }
    }

    void requestPause(int requestedFadeMs) {
        if ("playing".equals(status)) beginFade(false, requestedFadeMs);
    }

    void requestStop(int requestedFadeMs) {
        if ("playing".equals(status) || "fadingPause".equals(status)) {
            beginFade(true, requestedFadeMs);
        } else if (!"fadingStop".equals(status)) {
            // Paused / loading playback is already silent: stop immediately.
            stop(true);
        }
    }

    private void beginFade(boolean stopAfterFade, int requestedFadeMs) {
        if (player == null) return;
        try {
            positionMs = player.getCurrentPosition();
            durationMs = player.getDuration();
            int remaining = Math.max(0, durationMs - positionMs);
            float previousGain = 1f;
            if (isFading()) {
                float progress = commandFadeDuration <= 0 ? 1f : Math.min(1f,
                        Math.max(0f, (positionMs - commandFadeStart) / (float) commandFadeDuration));
                previousGain = commandFadeGain * (1f - progress);
            }
            commandFadeStart = positionMs;
            commandFadeDuration = Math.min(Math.max(0, requestedFadeMs), remaining);
            commandFadeGain = previousGain;
            status = stopAfterFade ? "fadingStop" : "fadingPause";
            if (commandFadeDuration == 0 || remaining == 0) {
                finishFade();
            } else {
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            }
        } catch (IllegalStateException exception) { stop(true); }
    }

    private void finishFade() {
        if (player != null) {
            try { player.setVolume(0f, 0f); } catch (IllegalStateException ignored) { }
        }
        if ("fadingStop".equals(status)) {
            stop(true);
        } else if ("fadingPause".equals(status)) {
            try {
                if (player != null) player.pause();
                status = "paused";
                handler.removeCallbacks(ticker);
                listener.paused();  // Mic may resume only AFTER audio is silent.
            } catch (IllegalStateException exception) { stop(true); }
        }
    }

    void resume(int requestedFadeMs) {
        if (player != null && "paused".equals(status)) {
            try {
                resumeFadeDurationMs = Math.max(0, Math.min(15000, requestedFadeMs));
                // Chặn tiếng bật đột ngột trước khi bắt đầu phát lại.
                player.setVolume(0f, 0f);
                resumeFadeStartMs = SystemClock.uptimeMillis();
                player.start();
                status = "playing";
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            } catch (IllegalStateException exception) { stop(true); }
        }
    }

    void stop(boolean notify) {
        handler.removeCallbacks(ticker);
        boolean wasActive = isActive();
        status = "idle";
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (focused && focus != null) audioManager.abandonAudioFocusRequest(focus);
        focused = false;
        focus = null;
        positionMs = 0;
        durationMs = 0;
        resumeFadeStartMs = -1L;
        commandFadeGain = 1f;
        title = "";
        if (notify && wasActive) listener.ended();
    }
}
