# ORBIC RECORDER v9 – APK Release ký cố định (tùy chọn)

**Không bắt buộc.** Để tạo APK không có Release Key, dùng workflow **Tạo APK Orbic Recorder (không cần Release Key)** trong GitHub Actions và xem `README.md`.

APK Release ký bằng khóa cố định cho phép nâng cấp APK về sau mà không phải gỡ ứng dụng, miễn vẫn dùng **cùng applicationId, cùng chứng chỉ và versionCode cao hơn**. Khóa ký APK khác với `OTA_SIGNING_KEY_B64` (khóa ký giao diện OTA).

## Thiết lập một lần

Trên máy có JDK 17, chạy lệnh này (lưu file `.p12` ngoài repo):

```sh
keytool -genkeypair -v -keystore orbic-recorder-release.p12 -storetype PKCS12 -alias orbic-release -keyalg RSA -keysize 3072 -validity 10000
```

Giữ lại file keystore và mật khẩu tại nơi an toàn, không commit lên GitHub. Chuyển file sang Base64 để lưu vào GitHub Secret (ví dụ PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path './orbic-recorder-release.p12'))) | Set-Clipboard
```

Thiết lập 4 Secrets trong **Settings → Secrets and variables → Actions**:

- `ANDROID_RELEASE_KEYSTORE_B64`: chuỗi Base64 của `.p12`;
- `ANDROID_RELEASE_STORE_PASSWORD`: mật khẩu kho;
- `ANDROID_RELEASE_KEY_ALIAS`: `orbic-release`;
- `ANDROID_RELEASE_KEY_PASSWORD`: mật khẩu khóa.

Giữ riêng `OTA_SIGNING_KEY_B64` nếu đang sử dụng OTA.

## Tạo APK Release v9.5

Vào GitHub → **Actions → Optional signed Release APK (cần Release Key) → Run workflow**. Khi thành công, tải artifact tên `Recorder-9.5.0.<run_number>`, chứa APK `Recorder-9.5.0.<run_number>.apk`. Kiểm tra bước **Verify APK signature** trong log.

**Quan trọng:** APK Release **không cài đè** APK Debug có chữ ký khác. Sao lưu WAV và cài đặt trước khi gỡ bản debug. OTA chỉ cập nhật `index.html`, `app.js`, `app.css`, không cập nhật Java native. Duy trì khóa APK ổn định cho mọi bản native về sau.
