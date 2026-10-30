package com.example.orbicrecorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * OTA updates signed HTML/CSS/JS only. It NEVER downloads or runs native Android code.
 * The installed APK pins the RSA public key. Private key is only in GitHub Actions secrets.
 */
public final class OtaManager {
    public static final int NATIVE_API = 1;
    public static final String BUILTIN_VERSION = "1.0.0";
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/gptlohoi-del/orbic-recorder/main/ota/latest.json";
    private static final String BUNDLE_PREFIX =
            "https://raw.githubusercontent.com/gptlohoi-del/orbic-recorder/main/ota/bundles/";
    private static final int MAX_MANIFEST = 12000;
    private static final int MAX_ARCHIVE = 2 * 1024 * 1024;
    private static final int MAX_UNPACKED = 3 * 1024 * 1024;
    private static final String[] FILES = {"index.html", "app.css", "app.js"};
    // This key is unique to this starter project. See README for provisioning instructions.
    private static final String PUBLIC_KEY_B64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA46aSFxUkhUsDyVshP4RP4YHt40ngruHLOqz2LTvQCYx6GpGTHIwEnuoV8Zh4IzkmKQqQElI7tujSW6SW/J0D2qRQoXY+jfUPHT5PosMBfH0W7XOAVSMr6rAUBIo68FIM+X8tl2/xN+Yc81/MuACOdssmFrRIs223b2vjD9PPF1HC5gIEWVBwqm7jV9CiDlP8jhREi06x+agOjPblgamk6MqjrxWVo2tkj0p1XlRLmOmQF6KsUdiTdbTPA0qzwFW/y0e6PfINca4E2EWS5sNIA6oFZKz+/Uddmb2FpPUbSDPwAgwWRRVEsZZUWRO8OlYDLKzi9Dfa/uqfXu1m01dr/cq9oRyxv2jUG3gJzaFgaZkfWFUQGw1pbVXVww/35Ii4HgFN84GVCa3945lSuIYuW3lg8QKfcHHVyCZWLnCAYrI2IBV5sx1Sfpl6YBjyXCDRp+4mc3NmXke17mTk7fbbaWSnanDNOt7c3PTEWZXsG0m/3t4maiTZNC0HShpsPtubAgMBAAE=";

    public interface ChangeListener { void changed(); }

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ChangeListener listener;
    private volatile String status = "idle";
    private volatile String message = "Chưa kiểm tra cập nhật";
    private volatile String offeredVersion = "";
    private volatile JSONObject offeredManifest;
    private volatile boolean busy = false;

    public OtaManager(Context context, ChangeListener listener) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("ota", Context.MODE_PRIVATE);
        this.listener = listener;
        validateActiveBundle();
    }

    private File otaRoot() { return new File(context.getFilesDir(), "ota"); }
    private File bundleDir(String version) { return new File(otaRoot(), "bundle-" + version); }
    public String getActiveVersion() { return preferences.getString("active", BUILTIN_VERSION); }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getOfferedVersion() { return offeredVersion; }
    public boolean isBusy() { return busy; }
    public boolean canRollback() { return preferences.contains("previous"); }

    private void report(String next, String description) {
        status = next;
        message = description;
        if (listener != null) listener.changed();
    }

    private static boolean versionValid(String version) {
        return version != null && version.matches("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}");
    }

    private static boolean newer(String remote, String installed) {
        if (!versionValid(remote) || !versionValid(installed)) return false;
        String[] a = remote.split("\\.");
        String[] b = installed.split("\\.");
        for (int i = 0; i < 3; i++) {
            int x = Integer.parseInt(a[i]);
            int y = Integer.parseInt(b[i]);
            if (x != y) return x > y;
        }
        return false;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int n;
        while ((n = input.read(block)) != -1) {
            if (output.size() + n > limit) throw new IOException("Gói cập nhật vượt giới hạn dung lượng");
            output.write(block, 0, n);
        }
        return output.toByteArray();
    }

    private static byte[] download(String url, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "OrbicRecorderOTA/1");
        try {
            if (connection.getResponseCode() != 200)
                throw new IOException("Máy chủ trả về HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input, limit);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(Locale.US, "%02x", b & 0xff));
        return result.toString();
    }

    private static String signedContent(String version, String sha, String url, int api) {
        return version + "\n" + sha + "\n" + url + "\n" + api + "\n";
    }

    private static boolean validSignature(JSONObject manifest) throws Exception {
        String version = manifest.getString("version");
        String sha = manifest.getString("sha256");
        String url = manifest.getString("url");
        int api = manifest.getInt("nativeApi");
        if (!versionValid(version) || !sha.matches("[a-f0-9]{64}") || api != NATIVE_API ||
                !url.equals(BUNDLE_PREFIX + version + ".zip")) return false;
        byte[] pub = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pub));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(signedContent(version, sha, url, api).getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.decode(manifest.getString("signature"), Base64.DEFAULT));
    }

    private static boolean allPresent(File directory) {
        for (String name : FILES) {
            File file = new File(directory, name);
            if (!file.isFile() || file.length() < 1) return false;
        }
        return true;
    }

    private void validateActiveBundle() {
        String current = getActiveVersion();
        if (!BUILTIN_VERSION.equals(current) &&
                (!versionValid(current) || !allPresent(bundleDir(current)))) {
            preferences.edit().putString("active", BUILTIN_VERSION).remove("previous").apply();
            report("fallback", "Gói cập nhật không đầy đủ, đã dùng giao diện gốc");
        }
    }

    /** Asset contents loaded through private, intercepted WebView URL, not public file URLs. */
    public InputStream openAsset(String name) throws IOException {
        boolean allowed = false;
        for (String file : FILES) if (file.equals(name)) allowed = true;
        if (!allowed) throw new IOException("Invalid local asset");
        String active = getActiveVersion();
        if (!BUILTIN_VERSION.equals(active)) {
            File chosen = new File(bundleDir(active), name);
            if (chosen.isFile()) return new FileInputStream(chosen);
        }
        return context.getAssets().open("ui/" + name);
    }

    public synchronized void check() {
        if (busy) return;
        busy = true;
        report("checking", "Đang kiểm tra OTA trên GitHub...");
        executor.execute(() -> {
            try {
                JSONObject manifest = new JSONObject(new String(download(MANIFEST_URL, MAX_MANIFEST), StandardCharsets.UTF_8));
                if (!validSignature(manifest)) throw new SecurityException("Chữ ký OTA không hợp lệ");
                String version = manifest.getString("version");
                if (newer(version, getActiveVersion())) {
                    offeredManifest = manifest;
                    offeredVersion = version;
                    report("available", "Có giao diện mới v" + version + ". Nhấn CẬP NHẬT OTA để cài.");
                } else {
                    offeredManifest = null;
                    offeredVersion = "";
                    report("latest", "Đang dùng giao diện mới nhất (v" + getActiveVersion() + ")");
                }
            } catch (Exception error) {
                report("error", "Không kiểm tra được OTA: " + error.getMessage());
            } finally {
                busy = false;
                if (listener != null) listener.changed();
            }
        });
    }

    public synchronized void install() {
        if (busy || offeredManifest == null) return;
        final JSONObject manifest = offeredManifest;
        busy = true;
        report("downloading", "Đang tải và xác minh gói giao diện...");
        executor.execute(() -> {
            File staging = null;
            try {
                if (!validSignature(manifest)) throw new SecurityException("Chữ ký OTA không hợp lệ");
                String version = manifest.getString("version");
                if (!newer(version, getActiveVersion())) throw new IOException("Phiên bản không mới hơn");
                byte[] archive = download(manifest.getString("url"), MAX_ARCHIVE);
                if (!hash(archive).equals(manifest.getString("sha256")))
                    throw new SecurityException("SHA-256 của gói OTA không khớp");
                if (!otaRoot().isDirectory() && !otaRoot().mkdirs()) throw new IOException("Không tạo được thư mục OTA");
                staging = new File(otaRoot(), "staging-" + version);
                deleteRecursive(staging);
                if (!staging.mkdirs()) throw new IOException("Không tạo được thư mục cập nhật");
                unpack(archive, staging);
                if (!allPresent(staging)) throw new IOException("Gói OTA thiếu file giao diện");
                File target = bundleDir(version);
                deleteRecursive(target);
                if (!staging.renameTo(target)) throw new IOException("Không kích hoạt được thư mục OTA");
                String previous = getActiveVersion();
                preferences.edit().putString("previous", previous).putString("active", version).commit();
                offeredVersion = "";
                offeredManifest = null;
                report("installed", "Đã cập nhật giao diện OTA v" + version);
            } catch (Exception error) {
                report("error", "Cập nhật OTA thất bại: " + error.getMessage());
            } finally {
                if (staging != null) deleteRecursive(staging);
                busy = false;
                if (listener != null) listener.changed();
            }
        });
    }

    public boolean rollback() {
        if (busy || !preferences.contains("previous")) return false;
        String old = preferences.getString("previous", BUILTIN_VERSION);
        if (!BUILTIN_VERSION.equals(old) && (!versionValid(old) || !allPresent(bundleDir(old)))) {
            report("error", "Không tìm thấy bản giao diện trước");
            return false;
        }
        preferences.edit().putString("active", old).remove("previous").apply();
        offeredManifest = null;
        offeredVersion = "";
        report("rolledback", "Đã quay về phiên bản giao diện " + old);
        return true;
    }

    private static void unpack(byte[] archive, File directory) throws IOException {
        int count = 0, total = 0;
        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                boolean allowed = false;
                for (String file : FILES) if (file.equals(name)) allowed = true;
                if (!allowed || entry.isDirectory()) throw new IOException("Tên file OTA không được cho phép: " + name);
                if (++count > FILES.length) throw new IOException("Quá nhiều file trong OTA");
                File output = new File(directory, name);
                try (FileOutputStream file = new FileOutputStream(output)) {
                    byte[] block = new byte[8192];
                    int n;
                    while ((n = input.read(block)) != -1) {
                        total += n;
                        if (total > MAX_UNPACKED) throw new IOException("Gói OTA giải nén quá lớn");
                        file.write(block, 0, n);
                    }
                }
                input.closeEntry();
            }
        }
        if (count != FILES.length) throw new IOException("Số file OTA không hợp lệ");
    }

    private static void deleteRecursive(File path) {
        if (path == null || !path.exists()) return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        if (!path.delete()) { /* best effort; validation will fail on a stale path */ }
    }
}
