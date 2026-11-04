package com.example.orbicrecorder;

import android.Manifest;
import android.media.audiofx.AutomaticGainControl;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Native recorder from v4 + OTA-updateable local HTML/CSS/JS user interface. */
public class MainActivity extends Activity {
    private static final String NATIVE_LABEL = "ORBIC RECORDER v9.5.0 · THƯ VIỆN GHI ÂM";
    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";
    private static final int REQUEST_MIC = 101;
    private static final int REQUEST_FOLDER = 102;
    private static final int REQUEST_MUSIC_1 = 201;
    private static final int REQUEST_MUSIC_2 = 202;
    private static final int REQUEST_MUSIC_OTHER = 203;
    private static final int REQUEST_MUSIC_3 = 205;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private WebView web;
    // Native View rotation works even when the device refuses reverse portrait/landscape.
    private boolean webRotated180;
    private OtaManager ota;
    private MusicPlayer music;
    private Uri queuedMusic;
    private String queuedMusicName;
    private boolean musicShouldReturnListening;
    // A cancelled/older song must never restart listening after DỪNG & LƯU.
    private long listeningRestoreTicket;
    private boolean pendingMusic;
    private boolean agcSupported;
    private boolean pendingResume;
    private Uri switchMusicUri;
    private String switchMusicName;
    private int switchMusicSlot;
    private int activeSlot;
    private volatile String activeRecordingId = "";
    private String switchRecordingId = "";
    private int waitCycles;

    private SharedPreferences config() { return getSharedPreferences("config", MODE_PRIVATE); }
    private SharedPreferences recorder() { return getSharedPreferences("state", MODE_PRIVATE); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // API capability can be checked before opening the microphone or requesting permission.
        // True means the Android AGC effect is exposed, not that every input path can use it.
        agcSupported = detectAgcSupport();
        webRotated180 = config().getBoolean("uiRotated180", false);
        music = new MusicPlayer(this, new MusicPlayer.Listener() {
            @Override public void ended() { onMusicEnded(); }
            @Override public void paused() { onMusicPaused(); }
        });
        ota = new OtaManager(this, () -> mainThread.post(() -> {
            if (web != null && ("installed".equals(ota.getStatus()) || "rolledback".equals(ota.getStatus()))) {
                web.clearCache(true);
                web.loadUrl(LOCAL_ORIGIN + "/index.html");
            }
        }));
        if (!RecorderService.isProcessCapturing()) {
            // Stale SharedPreferences flags can survive an abrupt process kill.
            recorder().edit().putBoolean("running", false).putBoolean("paused", false)
                    .putBoolean("saving", false).putBoolean("starting", false).apply();
            RecordingArchive.recoverOnLaunch(this);
        }
        buildWebView();
        // Inform Android 9 users that an SAF-selected folder survives app removal.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                !config().getBoolean("android9StorageTipShown", false)) {
            config().edit().putBoolean("android9StorageTipShown", true).apply();
            mainThread.postDelayed(() -> toast(
                    "Android 9: Vào Cài đặt > Chọn thư mục lưu WAV để giữ bản ghi khi gỡ ứng dụng."), 1800);
        }
        // Check quietly on app opening; recording itself does not need Internet.
        ota.check();
    }

    private static boolean detectAgcSupport() {
        try {
            return AutomaticGainControl.isAvailable();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isLocal(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) &&
                "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost()) &&
                (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private void buildWebView() {
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "Orbic");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isLocal(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!isLocal(uri)) return empty();
                String path = uri.getPath();
                String name = path == null ? "" : path.replaceFirst("^/", "");
                String mime = "text/plain";
                if ("index.html".equals(name)) mime = "text/html";
                else if ("app.css".equals(name)) mime = "text/css";
                else if ("app.js".equals(name)) mime = "application/javascript";
                else return empty();
                try {
                    InputStream input = ota.openAsset(name);
                    WebResourceResponse response = new WebResourceResponse(mime, "UTF-8", input);
                    response.setResponseHeaders(Collections.singletonMap("Cache-Control", "no-store"));
                    return response;
                } catch (IOException e) {
                    return empty();
                }
            }
        });
        setContentView(web);
        // WebView is the complete app UI; rotate this native View, not individual HTML panels.
        // Android transforms touch coordinates together with the drawing.
        web.setRotation(webRotated180 ? 180f : 0f);
        web.loadUrl(LOCAL_ORIGIN + "/index.html");
    }

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream("Blocked".getBytes(StandardCharsets.UTF_8)));
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void startRecording() {
        if (ota.isBusy() && "downloading".equals(ota.getStatus())) {
            toast("Đang áp dụng cập nhật OTA; hãy thử lại sau");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        if (RecordingArchive.isRecovering()) {
            toast("Đang khôi phục bản ghi trước đó; vui lòng thử lại sau");
            return;
        }
        if (recorder().getBoolean("running", false) || recorder().getBoolean("saving", false)) return;
        try {
            recorder().edit().putBoolean("starting", true).apply();
            // Ghi âm được bật ngay cả khi đang phát nhạc, nhưng micro không được mở
            // cho đến lúc nhạc thực sự kết thúc hoặc dừng.
            boolean heldByMusic = music.isBlockingMic() || pendingMusic || pendingResume;
            if (heldByMusic) musicShouldReturnListening = true;
            startForegroundService(new Intent(this, RecorderService.class)
                    .setAction(heldByMusic ? "START_HELD" : "START"));
        } catch (Exception e) {
            recorder().edit().putBoolean("starting", false).apply();
            toast("Không thể bắt đầu ghi: " + e.getMessage());
        }
    }

    private void sendRecorder(String command) {
        startService(new Intent(this, RecorderService.class).setAction(command));
    }

    /**
     * Flip the entire app exactly 180 degrees, with native touch-coordinate handling.
     *
     * v9.4 only called setRequestedOrientation(REVERSE_...). Some Android 9 firmware,
     * kiosk modes and rotation policies ignore that request without reporting an error.
     * Instead rotate the full-screen WebView itself; this works with sensor rotation
     * enabled or disabled, both in portrait and landscape, and never turns 90 degrees.
     * It does NOT rotate Android's status/navigation bars or other applications.
     */
    private void rotate180() {
        webRotated180 = !webRotated180;
        if (web != null) web.setRotation(webRotated180 ? 180f : 0f);
        config().edit().putBoolean("uiRotated180", webRotated180).apply();
    }

    private void chooseFolder() {
        if (recorder().getBoolean("running", false) || recorder().getBoolean("saving", false)) {
            toast("Hãy dừng ghi trước khi đổi thư mục");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_FOLDER); }
        catch (Exception e) { toast("Không mở được trình chọn thư mục"); }
    }

    private void playLast() {
        RecordingLibrary.Item item = RecordingLibrary.newest(this);
        if (item == null) { toast("Chưa có bản ghi WAV đã lưu"); return; }
        playRecording(item);
    }

    private void playRecording(RecordingLibrary.Item item) {
        if (item == null) { toast("Không tìm thấy bản ghi trong thư mục lưu"); return; }
        // Reuse MusicPlayer's audio focus and mic suspension. Never play staging files.
        queueSwitchMusic(item.uri, item.name, 0, item.uri.toString());
    }

    private void chooseMusic(int slot) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, slot); }
        catch (Exception e) { toast("Không mở được trình chọn bài nhạc"); }
    }

    private String musicName(int slot) {
        return config().getString("song" + slot + "Name", "Chưa chọn bài " + slot);
    }

    private void playMusic(int slot) {
        if (pendingMusic || pendingResume || recorder().getBoolean("saving", false)
                || RecordingArchive.isRecovering()) {
            toast("Hãy chờ thao tác đang thực hiện hoàn tất"); return;
        }
        String saved = config().getString("song" + slot + "Uri", "");
        if (saved.isEmpty()) { chooseMusic(slot == 1 ? REQUEST_MUSIC_1 : slot == 2 ? REQUEST_MUSIC_2 : REQUEST_MUSIC_3); return; }
        queueSwitchMusic(Uri.parse(saved), musicName(slot), slot);
    }

    /** All changes of track are faded; mic remains held between consecutive songs. */
    private void queueSwitchMusic(Uri uri, String label, int slot) {
        queueSwitchMusic(uri, label, slot, "");
    }

    private void queueSwitchMusic(Uri uri, String label, int slot, String recordingId) {
        if (pendingMusic || pendingResume || recorder().getBoolean("saving", false)) {
            toast("Vui lòng đợi thao tác hiện tại"); return;
        }
        if (music.isActive()) {
            switchMusicUri = uri;
            switchMusicName = label;
            switchMusicSlot = slot;
            switchRecordingId = recordingId;
            music.requestStop(config().getInt("pauseStopFade", 2000));
        } else {
            activeSlot = slot;
            activeRecordingId = recordingId;
            startMusic(uri, label);
        }
    }

    private void startMusic(Uri uri, String label) {
        // Listening to a file does NOT need microphone permission. Only START
        // requests RECORD_AUDIO. This keeps music usable before permission grant.
        if (music.isActive() || pendingMusic || pendingResume || recorder().getBoolean("saving", false)
                || recorder().getBoolean("starting", false)) {
            toast("Không thể phát khi đang xử lý âm thanh"); return;
        }
        queuedMusic = uri;
        queuedMusicName = label;
        pendingMusic = true;
        listeningRestoreTicket++; // Hủy callback phục hồi còn chờ từ bài nhạc cũ.
        musicShouldReturnListening = true;
        if (recorder().getBoolean("running", false)) {
            sendRecorder("MUSIC_PAUSE");
            waitCycles = 0;
            mainThread.postDelayed(this::waitForMicRelease, 70);
        } else playQueuedMusic();
    }

    private void waitForMicRelease() {
        if (!pendingMusic) return;
        if (recorder().getBoolean("musicSuspended", false)) {
            playQueuedMusic();
        } else if (++waitCycles < 90 && recorder().getBoolean("running", false)) {
            mainThread.postDelayed(this::waitForMicRelease, 60);
        } else {
            pendingMusic = false;
            musicShouldReturnListening = false;
            sendRecorder("MUSIC_RESUME");
            toast("Micro chưa tạm ngưng an toàn. Không thể phát nhạc.");
        }
    }

    private void playQueuedMusic() {
        if (!pendingMusic) return;
        pendingMusic = false;
        try {
            SharedPreferences c = config();
            // Fades meant for songs would hide the beginning/end of short WAV clips.
            boolean voiceReplay = !activeRecordingId.isEmpty();
            music.play(queuedMusic, queuedMusicName,
                    voiceReplay ? 0 : c.getInt("fadeIn", 3000),
                    voiceReplay ? 0 : c.getInt("fadeOut", 5000),
                    c.getInt("musicVolume", 80));
        } catch (Exception e) { toast(e.getMessage()); }
    }

    /** Called only after the player has truly paused (volume 0, no audible music). */
    private void onMusicPaused() {
        scheduleListeningRestore(false);
    }

    private void onMusicEnded() {
        pendingResume = false;
        if (switchMusicUri != null) {
            Uri next = switchMusicUri;
            String name = switchMusicName;
            int slot = switchMusicSlot;
            switchMusicUri = null;
            switchMusicName = null;
            activeSlot = slot;
            activeRecordingId = switchRecordingId;
            switchRecordingId = "";
            // Do not release the mic hold between two consecutive tracks.
            startMusic(next, name);
            return;
        }
        activeSlot = 0;
        activeRecordingId = "";
        if (!musicShouldReturnListening) return;
        musicShouldReturnListening = false;
        scheduleListeningRestore(true);
    }

    private void scheduleListeningRestore(boolean completed) {
        long ticket = ++listeningRestoreTicket;
        int delay = config().getInt("listenDelay", 600);
        mainThread.postDelayed(() -> restoreListening(ticket, 40), delay);
    }

    private void restoreListening(long ticket, int retries) {
        if (ticket != listeningRestoreTicket || music.isBlockingMic() || pendingMusic || pendingResume)
            return;
        SharedPreferences p = recorder();
        if (p.getBoolean("running", false)) {
            // Resume mic; never override a manual PAUSE on the recording session.
            sendRecorder("MUSIC_RESUME");
            return;
        }
        // START_HELD may still be launching the service: wait for the state.
        if (p.getBoolean("starting", false) && retries > 0) {
            mainThread.postDelayed(() -> restoreListening(ticket, retries - 1), 100);
        }
        // If user has not pressed START, NEVER start recording automatically.
    }

    private void resumeMusicSafely() {
        if (!"paused".equals(music.getStatus()) || pendingResume) return;
        pendingResume = true;
        listeningRestoreTicket++; // Cancel delayed microphone restoration.
        waitCycles = 0;
        waitForResumeMicRelease(false);
    }

    private void waitForResumeMicRelease(boolean holdRequested) {
        if (!pendingResume) return;
        SharedPreferences p = recorder();
        if (p.getBoolean("starting", false)) {
            if (++waitCycles < 90)
                mainThread.postDelayed(() -> waitForResumeMicRelease(holdRequested), 60);
            else { pendingResume = false; toast("Chưa thể xác nhận trạng thái micro"); }
            return;
        }
        if (!p.getBoolean("running", false)) {
            pendingResume = false;
            music.resume(activeRecordingId.isEmpty() ? config().getInt("pauseStopFade", 2000) : 0);
            return;
        }
        if (!holdRequested) {
            sendRecorder("MUSIC_PAUSE");
            mainThread.postDelayed(() -> waitForResumeMicRelease(true), 70);
            return;
        }
        if (p.getBoolean("musicSuspended", false)) {
            pendingResume = false;
            music.resume(activeRecordingId.isEmpty() ? config().getInt("pauseStopFade", 2000) : 0);
        } else if (++waitCycles < 90) {
            mainThread.postDelayed(() -> waitForResumeMicRelease(true), 60);
        } else {
            pendingResume = false;
            sendRecorder("MUSIC_RESUME");
            toast("Micro chưa nhường quyền an toàn; nhạc vẫn tạm dừng");
        }
    }

    private boolean captureActive() {
        return recorder().getBoolean("running", false) || recorder().getBoolean("saving", false);
    }

    private final class Bridge {
        // UI can detect this new native implementation; older v9.4 APKs can still
        // receive an OTA-only CSS fallback without double-rotating a new APK.
        @JavascriptInterface public boolean supportsReliableRotation() { return true; }

        @JavascriptInterface public String snapshot() {
            JSONObject data = new JSONObject();
            SharedPreferences p = recorder(), cfg = config();
            try {
                data.put("running", p.getBoolean("running", false));
                data.put("musicSuspended", p.getBoolean("musicSuspended", false));
                data.put("pendingMusic", pendingMusic);
                data.put("pendingResume", pendingResume);
                data.put("musicAudible", music.isBlockingMic());
                data.put("activeSlot", activeSlot);
                music.writeSnapshot(data);
                data.put("song1Name", musicName(1));
                data.put("song2Name", musicName(2));
                data.put("song3Name", musicName(3));
                data.put("paused", p.getBoolean("paused", false));
                data.put("saving", p.getBoolean("saving", false));
                data.put("starting", p.getBoolean("starting", false));
                data.put("level", p.getInt("level", 0));
                data.put("db", p.getString("db", "—"));
                data.put("capture", p.getString("capture", "SẴN SÀNG"));
                data.put("elapsedMs", p.getLong("elapsedMs", 0L));
                data.put("keptMs", p.getLong("keptMs", 0L));
                data.put("segments", p.getInt("segments", 0));
                data.put("part", p.getInt("part", 1));
                data.put("savedParts", p.getInt("savedParts", 0));
                data.put("recovering", RecordingArchive.isRecovering());
                data.put("recoveryStatus", p.getString("recoveryStatus", ""));
                data.put("lastRaw", p.getString("lastRaw", ""));
                data.put("source", p.getString("source", "Chưa xác định"));
                data.put("file", p.getString("file", "Chưa có"));
                data.put("error", p.getString("error", ""));
                data.put("folder", cfg.getString("folderLabel",
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                                ? "Bộ nhớ ứng dụng/OrbicRecorder (Android 9)" : "Music/OrbicRecorder"));
                data.put("pre", cfg.getInt("pre", 1000));
                data.put("tail", cfg.getInt("tail", 1000));
                data.put("threshold", cfg.getInt("threshold", -38));
                data.put("thresholdEnabled", cfg.getBoolean("thresholdEnabled", true));
                data.put("agcEnabled", cfg.getBoolean("agcEnabled", true));
                data.put("agcSupported", agcSupported);
                data.put("limiterEnabled", cfg.getBoolean("limiterEnabled", true));
                data.put("overloadWarn", cfg.getBoolean("overloadWarn", true));
                data.put("limitDb", cfg.getInt("limitDb", -6));
                data.put("attackMs", cfg.getInt("attackMs", 10));
                data.put("releaseMs", cfg.getInt("releaseMs", 500));
                data.put("overloadDb", cfg.getInt("overloadDb", -1));
                // Persisted capture status may be stale after STOP; report only current session status.
                data.put("agcStatus", p.getBoolean("running", false) || p.getBoolean("starting", false)
                        ? p.getString("agcStatus", "Chưa khởi động micro")
                        : "Chưa khởi động micro");
                data.put("overload", p.getBoolean("overload", false));
                data.put("peakDb", p.getString("peakDb", "—"));
                data.put("fadeIn", cfg.getInt("fadeIn", 3000));
                data.put("fadeOut", cfg.getInt("fadeOut", 5000));
                data.put("pauseStopFade", cfg.getInt("pauseStopFade", 2000));
                data.put("musicVolume", cfg.getInt("musicVolume", 80));
                data.put("listenDelay", cfg.getInt("listenDelay", 600));
                data.put("uiFont", cfg.getInt("uiFont", 100));
                data.put("uiOpacity", cfg.getInt("uiOpacity", 100));
                data.put("waveHeight", cfg.getInt("waveHeight", 92));
                data.put("waveStyle", cfg.getString("waveStyle", "mirror"));
                data.put("gearX", cfg.getInt("gearX", 99));
                data.put("gearY", cfg.getInt("gearY", 0));
                data.put("numericLimits", cfg.getString("numericLimits", "{}"));
                data.put("minSpeech", cfg.getInt("minSpeech", 180));
                data.put("warnSeconds", cfg.getInt("warnSeconds", 9999));
                data.put("backupRaw", cfg.getBoolean("backupRaw", true));
                data.put("splitMinutes", cfg.getInt("splitMinutes", 15));
                data.put("theme", cfg.getString("theme", "system"));
                data.put("otaStatus", ota.getStatus());
                data.put("otaMessage", ota.getMessage());
                data.put("otaAvailable", ota.getOfferedVersion());
                data.put("otaVersion", ota.getActiveVersion());
                data.put("canRollback", ota.canRollback());
                data.put("nativeApi", OtaManager.NATIVE_API);
                data.put("nativeLabel", NATIVE_LABEL);
                data.put("uiRotated180", webRotated180);
                // Capability negotiation allows HTML OTA to avoid displaying
                // destructive/native-only buttons on unsupported old APKs.
                data.put("supportsThirdTrack", true);
                data.put("supportsSwipeDiscard", true);
                data.put("supportsRecordingLibrary", true);
                data.put("activeRecordingId", activeRecordingId);
            } catch (Exception ignored) { }
            return data.toString();
        }

        // This only enumerates completed files in the chosen folder and Orbic's default store.
        // WebView invokes JavascriptInterface methods off the UI thread.
        @JavascriptInterface public String recordings() {
            return RecordingLibrary.asJson(MainActivity.this);
        }

        @JavascriptInterface public void playRecording(String id) {
            if (id == null || id.length() > 2048) return;
            // Validate against our own archive before opening a URI provided by JavaScript.
            RecordingLibrary.Item item = RecordingLibrary.find(MainActivity.this, id);
            runOnUiThread(() -> MainActivity.this.playRecording(item));
        }

        @JavascriptInterface public void seekRecording(int positionMs) {
            runOnUiThread(() -> {
                if (!activeRecordingId.isEmpty()) music.seekTo(positionMs);
            });
        }

        @JavascriptInterface public void action(String command) {
            if (command == null || command.length() > 32) return;
            runOnUiThread(() -> {
                switch (command) {
                    case "chooseSong1": chooseMusic(REQUEST_MUSIC_1); break;
                    case "chooseSong2": chooseMusic(REQUEST_MUSIC_2); break;
                    case "chooseSong3": chooseMusic(REQUEST_MUSIC_3); break;
                    case "rotate180": rotate180(); break;
                    case "chooseOther": chooseMusic(REQUEST_MUSIC_OTHER); break;
                    case "music1": playMusic(1); break;
                    case "music2": playMusic(2); break;
                    case "music3": playMusic(3); break;
                    case "musicPause": music.requestPause(activeRecordingId.isEmpty()
                            ? config().getInt("pauseStopFade", 2000) : 0); break;
                    case "musicResume": resumeMusicSafely(); break;
                    case "musicStop":
                        switchMusicUri = null;
                        switchMusicName = null;
                        switchRecordingId = "";
                        if (pendingMusic) {
                            pendingMusic = false;
                            onMusicEnded();
                        } else if (pendingResume) {
                            pendingResume = false;
                            music.requestStop(activeRecordingId.isEmpty()
                                    ? config().getInt("pauseStopFade", 2000) : 0);
                        } else {
                            music.requestStop(activeRecordingId.isEmpty()
                                    ? config().getInt("pauseStopFade", 2000) : 0);
                        }
                        break;
                    case "start": startRecording(); break;
                    case "pause": if (recorder().getBoolean("running", false)) sendRecorder("PAUSE"); break;
                    case "resume": if (recorder().getBoolean("running", false)) sendRecorder("RESUME"); break;
                    case "stop":
                        // DỪNG & LƯU ngừng phiên ghi, không tác động tới nhạc.
                        // Không được tự khởi động lại ghi sau khi bài nhạc kết thúc.
                        musicShouldReturnListening = false;
                        listeningRestoreTicket++;
                        if (recorder().getBoolean("running", false)) sendRecorder("STOP");
                        break;
                    case "discard":
                        // Only an explicit swipe gesture in the bundled UI sends this command.
                        // Native service owns deletion; never delete the previous session.
                        musicShouldReturnListening = false;
                        listeningRestoreTicket++;
                        if (recorder().getBoolean("running", false) &&
                                !recorder().getBoolean("saving", false)) sendRecorder("STOP_DISCARD");
                        break;
                    case "chooseFolder": chooseFolder(); break;
                    case "resetFolder":
                        if (captureActive()) { toast("Hãy dừng ghi trước khi đổi thư mục"); break; }
                        config().edit().remove("treeUri").remove("folderLabel").apply();
                        toast(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                                ? "Android 9: WAV sẽ lưu trong bộ nhớ ứng dụng. Nên chọn thư mục riêng để giữ WAV khi gỡ app."
                                : "Đã chọn thư mục Music/OrbicRecorder"); break;
                    case "playLast": playLast(); break;
                    case "checkOta": ota.check(); break;
                    case "installOta":
                        if (captureActive() || music.isActive() || pendingMusic) { toast("Dừng ghi và phát nhạc trước khi cập nhật"); break; }
                        ota.install(); break;
                    case "rollback":
                        if (captureActive()) { toast("Dừng ghi trước khi quay lại bản cũ"); break; }
                        if (!ota.rollback()) toast("Không có phiên bản giao diện trước để khôi phục");
                        else toast("Đã quay lại giao diện trước"); break;
                    default: break;
                }
            });
        }

        @JavascriptInterface public void saveSettings(String json) {
            if (json == null || json.length() > 12000) return;
            try {
                JSONObject v = new JSONObject(json);
                String[] numeric = { "threshold", "minSpeech", "pre", "tail", "warnSeconds",
                        "fadeIn", "fadeOut", "pauseStopFade", "musicVolume", "listenDelay", "uiFont", "uiOpacity", "waveHeight",
                        "limitDb", "attackMs", "releaseMs", "overloadDb" };
                int[] low = { -80, 20, 0, 100, 5, 0, 0, 0, 0, 0, 75, 30, 30, -30, 1, 20, -12 };
                int[] high = { -5, 2000, 10000, 10000, 9999, 30000, 30000, 15000, 100, 5000, 160, 100, 180, -1, 250, 5000, -1 };
                SharedPreferences.Editor edit = config().edit();
                for (int i = 0; i < numeric.length; i++) {
                    if (v.has(numeric[i])) {
                        int number = v.getInt(numeric[i]);
                        if (number < low[i] || number > high[i])
                            throw new IllegalArgumentException("Vượt giới hạn an toàn: " + numeric[i]);
                        edit.putInt(numeric[i], number);
                    }
                }
                for (String pos : new String[]{"gearX", "gearY"}) {
                    if (v.has(pos)) {
                        int n = v.getInt(pos);
                        if (n < 0 || n > 100) throw new IllegalArgumentException("Vị trí nút ngoài màn hình");
                        edit.putInt(pos, n);
                    }
                }
                if (v.has("splitMinutes")) {
                    int split = v.getInt("splitMinutes");
                    if (split < 1 || split > 60) throw new IllegalArgumentException("Chia file không hợp lệ");
                    edit.putInt("splitMinutes", split);
                }
                if (v.has("theme")) {
                    String theme = v.getString("theme");
                    if (!(theme.equals("system") || theme.equals("light") || theme.equals("dark")))
                        throw new IllegalArgumentException("Giao diện không hợp lệ");
                    edit.putString("theme", theme);
                }
                if (v.has("waveStyle")) {
                    String waveStyle = v.getString("waveStyle");
                    if (!(waveStyle.equals("bars") || waveStyle.equals("mirror") ||
                          waveStyle.equals("line") || waveStyle.equals("dots")))
                        throw new IllegalArgumentException("Kiểu sóng âm không hợp lệ");
                    edit.putString("waveStyle", waveStyle);
                }
                if (v.has("backupRaw")) edit.putBoolean("backupRaw", v.getBoolean("backupRaw"));
                // True by default for users upgrading from older versions.
                if (v.has("thresholdEnabled")) edit.putBoolean("thresholdEnabled", v.getBoolean("thresholdEnabled"));
                for (String key : new String[]{"agcEnabled", "limiterEnabled", "overloadWarn"}) {
                    if (v.has(key)) edit.putBoolean(key, v.getBoolean(key));
                }
                if (v.has("numericLimits")) {
                    JSONObject limits = v.getJSONObject("numericLimits");
                    if (limits.length() > numeric.length) throw new IllegalArgumentException("Quá nhiều thông số");
                    for (int i = 0; i < numeric.length; i++) {
                        if (!limits.has(numeric[i])) continue;
                        JSONObject bound = limits.getJSONObject(numeric[i]);
                        int min = bound.getInt("min"), max = bound.getInt("max");
                        int step = bound.getInt("step"), def = bound.getInt("default");
                        if (min < low[i] || max > high[i] || min > max || step <= 0 ||
                            step > max - min + 1 || def < min || def > max)
                            throw new IllegalArgumentException("Giới hạn không hợp lệ: " + numeric[i]);
                    }
                    edit.putString("numericLimits", limits.toString());
                }
                edit.apply();
            } catch (Exception e) {
                runOnUiThread(() -> toast("Cài đặt không hợp lệ: " + e.getMessage()));
            }
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRecording();
            else toast("Cần cấp quyền micro để ghi âm");
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (code == REQUEST_MUSIC_1 || code == REQUEST_MUSIC_2 || code == REQUEST_MUSIC_3 || code == REQUEST_MUSIC_OTHER) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) { toast("Không lưu được quyền bài nhạc; hãy thử chọn lại"); return; }
            String label = "Bài nhạc đã chọn";
            try (android.database.Cursor cursor = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) label = cursor.getString(0);
            } catch (Exception ignored) { }
            if (label == null || label.isEmpty()) label = "Bài nhạc";
            if (code == REQUEST_MUSIC_OTHER) queueSwitchMusic(uri, label, 0);
            else {
                int slot = code == REQUEST_MUSIC_1 ? 1 : code == REQUEST_MUSIC_2 ? 2 : 3;
                config().edit().putString("song" + slot + "Uri", uri.toString())
                        .putString("song" + slot + "Name", label).apply();
                toast("Đã lưu bài mặc định " + slot + ": " + label);
            }
            return;
        }
        if (code != REQUEST_FOLDER) return;
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            config().edit().putString("treeUri", uri.toString())
                    .putString("folderLabel", DocumentsContract.getTreeDocumentId(uri)).apply();
            toast("Đã chọn thư mục lưu WAV");
        } catch (Exception e) { toast("Không thể lưu quyền thư mục: " + e.getMessage()); }
    }

    @Override public void onBackPressed() {
        // Native escape hatch: works even if a faulty OTA breaks the HTML/JavaScript UI.
        new AlertDialog.Builder(this)
                .setTitle("Orbic Recorder – Khôi phục")
                .setMessage("Bạn muốn quay lại giao diện trước hoặc đóng ứng dụng?")
                .setPositiveButton("Quay lại giao diện trước", (d, w) -> {
                    if (captureActive()) { toast("Hãy dừng ghi trước khi khôi phục"); return; }
                    if (!ota.rollback()) toast("Không có phiên bản trước");
                })
                .setNegativeButton("Đóng", (d, w) -> finish())
                .setNeutralButton("Hủy", (d, w) -> {})
                .show();
    }

    @Override protected void onDestroy() {
        pendingMusic = false;
        pendingResume = false;
        switchMusicUri = null;
        activeRecordingId = "";
        switchRecordingId = "";
        musicShouldReturnListening = false;
        listeningRestoreTicket++;
        if (music != null) {
            music.stop(false);
            if (recorder().getBoolean("musicSuspended", false)) sendRecorder("MUSIC_RESUME");
        }
        if (web != null) {
            web.removeJavascriptInterface("Orbic");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
