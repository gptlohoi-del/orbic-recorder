# Orbic Recorder v5.1.1 – Cài đặt APK release ký bằng khóa cố định

Đây là bản v5.1 giữ nguyên các chức năng thu âm, file gốc dự phòng, chia file, khôi phục WAV và hệ thống OTA. Chỉ thêm cấu hình release signing và workflow GitHub Actions. Không có private key trong ZIP. OTA RSA key **khác** với Android release signing key.

## 1. Tạo khóa trên Windows (chỉ một lần)

Cài JDK 17 (nếu chưa có). Mở PowerShell trong thư mục riêng trên máy tính, dùng:

```powershell
keytool -genkeypair -v -keystore orbic-recorder-release.p12 -storetype PKCS12 -alias orbic-release -keyalg RSA -keysize 3072 -validity 10000
```

Làm theo phần hỏi mật khẩu và thông tin chứng chỉ. Nhớ mật khẩu keystore (với PKCS12 dùng cùng mật khẩu khóa) và alias `orbic-release`. Không đưa `.p12` lên GitHub. Sao lưu ngoại tuyến bản keystore và mật khẩu tại nơi an toàn.

## 2. Chuyển .p12 sang Base64 (PowerShell)

Mở PowerShell ở thư mục chứa .p12:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path './orbic-recorder-release.p12'))) | Set-Clipboard
```

Dán nội dung clipboard vào secret `ANDROID_RELEASE_KEYSTORE_B64` trên GitHub. Sau đó làm trống clipboard. Base64 là mã hóa biểu diễn, **không bảo mật dữ liệu**.

## 3. Tạo bốn GitHub Actions secrets

Repo → Settings → Secrets and variables → Actions → New repository secret:

- `ANDROID_RELEASE_KEYSTORE_B64`: Chuỗi Base64 của file `.p12`
- `ANDROID_RELEASE_STORE_PASSWORD`: Mật khẩu keystore
- `ANDROID_RELEASE_KEY_ALIAS`: `orbic-release`
- `ANDROID_RELEASE_KEY_PASSWORD`: Mật khẩu khóa (với PKCS12 thường giống mật khẩu keystore)

Giữ nguyên `OTA_SIGNING_KEY_B64` hiện có. Không đưa mật khẩu vào Gradle, workflow hay các file nguồn.

## 4. Đưa mã nguồn lên GitHub

Tải đầy đủ nội dung ZIP vào thư mục gốc repo, ghi đè file trùng. **Đặc biệt giữ nguyên thư mục `ota/` đã có `latest.json` và bản OTA 1.0.1 trở lên**; không xóa các bản OTA đã phát hành. File workflow mới phải nằm ở `.github/workflows/build-release.yml`.

## 5. Build APK release

Vào GitHub → Actions → **Build Orbic Recorder Signed Release APK** → Run workflow. Tải artifact `orbic-recorder-v5.1.1-signed-release-apk`, giải nén lấy `app-release.apk`.

Xác nhận log bước **Verify APK signature** thành công. Lưu lại SHA-256 dấu vân tay chứng chỉ từ log để đối chiếu các bản sau. Mỗi lần cập nhật mã native: dùng **cùng file .p12, cùng alias/mật khẩu**, tăng `versionCode` trong `app/build.gradle`, rồi build release lại. Với điện thoại chạy Android 12, chỉ ký bằng cùng chứng chỉ mới có thể cập nhật đè mà giữ dữ liệu ứng dụng (nếu chính sách quản trị cho phép).

## 6. Cảnh báo chuyển từ debug sang release

**APK release dùng chứng chỉ mới KHÔNG cài đè lên APK debug đã cài** nếu chứng chỉ khác nhau. Đây là giới hạn bảo mật Android. Trước khi gỡ bản debug: sao lưu file WAV (Music/OrbicRecorder), cấu hình quan trọng; lưu lại lựa chọn thư mục SAF để chọn lại; xác nhận OTA đã phát hành còn trên GitHub. Sau khi sao lưu, gỡ debug rồi cài release **một lần, trước khi quản trị viên áp dụng hạn chế**. Nếu thiết bị đã quản lý, cần được quản trị viên cho phép. Việc gỡ có thể xóa cài đặt ứng dụng, bản OTA được lưu nội bộ và quyền truy cập thư mục; bản ghi trong các thư mục công cộng không nên coi là bản sao lưu duy nhất.

**APK được ký cùng khóa không có nghĩa là Android tự cài mà không cần xác nhận; ký đúng chỉ cho phép cập nhật đè.** Các thay đổi HTML/CSS/JS tiếp tục phát hành qua OTA như trước, không cần APK mới. Không dùng `Build ... Debug APK` cho bản chính thức.
