package com.example.orbicrecorder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
    private static final String NATIVE_LABEL = "ORBIC RECORDER v5.0";
    private static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net";
    private static final int REQUEST_MIC = 101;
    private static final int REQUEST_FOLDER = 102;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private WebView web;
    private OtaManager ota;

    private SharedPreferences config() { return getSharedPreferences("config", MODE_PRIVATE); }
    private SharedPreferences recorder() { return getSharedPreferences("state", MODE_PRIVATE); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ota = new OtaManager(this, () -> mainThread.post(() -> {
            if (web != null && ("installed".equals(ota.getStatus()) || "rolledback".equals(ota.getStatus()))) {
                web.clearCache(true);
                web.loadUrl(LOCAL_ORIGIN + "/index.html");
            }
        }));
        buildWebView();
        // Check quietly on app opening; recording itself does not need Internet.
        ota.check();
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
        if (recorder().getBoolean("running", false) || recorder().getBoolean("saving", false)) return;
        try {
            startForegroundService(new Intent(this, RecorderService.class).setAction("START"));
        } catch (Exception e) {
            toast("Không thể bắt đầu ghi: " + e.getMessage());
        }
    }

    private void sendRecorder(String command) {
        startService(new Intent(this, RecorderService.class).setAction(command));
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
        String value = recorder().getString("uri", "");
        if (value.isEmpty()) { toast("Chưa có bản ghi"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(value), "audio/wav");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Nghe bản ghi"));
        } catch (Exception e) { toast("Không tìm thấy ứng dụng phát WAV"); }
    }

    private boolean captureActive() {
        return recorder().getBoolean("running", false) || recorder().getBoolean("saving", false);
    }

    private final class Bridge {
        @JavascriptInterface public String snapshot() {
            JSONObject data = new JSONObject();
            SharedPreferences p = recorder(), cfg = config();
            try {
                data.put("running", p.getBoolean("running", false));
                data.put("paused", p.getBoolean("paused", false));
                data.put("saving", p.getBoolean("saving", false));
                data.put("level", p.getInt("level", 0));
                data.put("db", p.getString("db", "—"));
                data.put("capture", p.getString("capture", "SẴN SÀNG"));
                data.put("elapsedMs", p.getLong("elapsedMs", 0L));
                data.put("keptMs", p.getLong("keptMs", 0L));
                data.put("segments", p.getInt("segments", 0));
                data.put("source", p.getString("source", "Chưa xác định"));
                data.put("file", p.getString("file", "Chưa có"));
                data.put("error", p.getString("error", ""));
                data.put("folder", cfg.getString("folderLabel", "Music/OrbicRecorder"));
                data.put("pre", cfg.getInt("pre", 1000));
                data.put("tail", cfg.getInt("tail", 1000));
                data.put("threshold", cfg.getInt("threshold", -38));
                data.put("minSpeech", cfg.getInt("minSpeech", 180));
                data.put("warnSeconds", cfg.getInt("warnSeconds", 20));
                data.put("theme", cfg.getString("theme", "system"));
                data.put("otaStatus", ota.getStatus());
                data.put("otaMessage", ota.getMessage());
                data.put("otaAvailable", ota.getOfferedVersion());
                data.put("otaVersion", ota.getActiveVersion());
                data.put("canRollback", ota.canRollback());
                data.put("nativeApi", OtaManager.NATIVE_API);
                data.put("nativeLabel", NATIVE_LABEL);
            } catch (Exception ignored) { }
            return data.toString();
        }

        @JavascriptInterface public void action(String command) {
            if (command == null || command.length() > 32) return;
            runOnUiThread(() -> {
                switch (command) {
                    case "start": startRecording(); break;
                    case "pause": if (recorder().getBoolean("running", false)) sendRecorder("PAUSE"); break;
                    case "resume": if (recorder().getBoolean("running", false)) sendRecorder("RESUME"); break;
                    case "stop": if (recorder().getBoolean("running", false)) sendRecorder("STOP"); break;
                    case "chooseFolder": chooseFolder(); break;
                    case "resetFolder":
                        if (captureActive()) { toast("Hãy dừng ghi trước khi đổi thư mục"); break; }
                        config().edit().remove("treeUri").remove("folderLabel").apply();
                        toast("Đã chọn thư mục Music/OrbicRecorder"); break;
                    case "playLast": playLast(); break;
                    case "checkOta": ota.check(); break;
                    case "installOta":
                        if (captureActive()) { toast("Dừng và lưu bản ghi trước khi cập nhật"); break; }
                        ota.install(); break;
                    case "rollback":
                        if (captureActive()) { toast("Dừng ghi trước khi quay lại bản cũ"); break; }
                        if (!ota.rollback()) toast("Không có phiên bản giao diện trước để khôi phục");
                        else toast("Không có phiên bản giao diện trước để khôi phục"); break;
                    default: break;
                }
            });
        }

        @JavascriptInterface public void saveSettings(String json) {
            if (json == null || json.length() > 2000) return;
            try {
                JSONObject v = new JSONObject(json);
                int threshold = v.getInt("threshold");
                int minSpeech = v.getInt("minSpeech");
                int pre = v.getInt("pre");
                int tail = v.getInt("tail");
                int warn = v.getInt("warnSeconds");
                String theme = v.getString("theme");
                if (threshold < -70 || threshold > -10 || minSpeech < 60 || minSpeech > 1000 ||
                        pre < 0 || pre > 5000 || tail < 100 || tail > 5000 ||
                        warn < 10 || warn > 120 ||
                        !(theme.equals("system") || theme.equals("light") || theme.equals("dark"))) {
                    throw new IllegalArgumentException("Giá trị ngoài giới hạn");
                }
                config().edit().putInt("threshold", threshold).putInt("minSpeech", minSpeech)
                        .putInt("pre", pre).putInt("tail", tail).putInt("warnSeconds", warn)
                        .putString("theme", theme).apply();
                runOnUiThread(() -> toast("Đã lưu cài đặt. Bộ lọc áp dụng ở phiên ghi tiếp theo."));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Không lưu được cài đặt: " + e.getMessage()));
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
        if (code != REQUEST_FOLDER || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
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
        if (web != null) {
            web.removeJavascriptInterface("Orbic");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
