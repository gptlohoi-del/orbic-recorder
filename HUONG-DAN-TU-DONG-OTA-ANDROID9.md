# HƯỚNG DẪN – TỰ ĐỘNG CẬP NHẬT OTA TRÊN ANDROID 9

## 1. Hoạt động như thế nào?

Từ **APK native v9.5.1**, vào **⚙ CÀI ĐẶT → ↻ CẬP NHẬT OTA** có công tắc **TỰ ĐỘNG CẬP NHẬT OTA**. Mặc định công tắc **BẬT** nếu người dùng chưa chọn khác. Khi mở ứng dụng và có Internet, Android kiểm tra `ota/latest.json` trên GitHub. Bản mới chỉ được chấp nhận nếu `nativeApi` phù hợp (`8`), có chữ ký RSA tin cậy và SHA-256 của ZIP trùng manifest. Chỉ ba file `index.html`, `app.css`, `app.js` trong ZIP được áp dụng.

Nếu đang ghi âm, chờ ghi, lưu WAV, đang khôi phục hoặc phát nhạc thì **không bắt đầu áp dụng**. Khi ứng dụng còn mở ở phía trước, kiểm tra lại điều kiện cài đặt khoảng 3 giây một lần; lúc rảnh OTA tự cài và làm mới màn hình. Khi đóng màn hình app, không có tác vụ định kỳ chạy nền; lần mở sau sẽ kiểm tra lại. Nếu không có Internet hoặc OTA sai chữ ký, app tiếp tục chạy bản giao diện sẵn có, không xóa WAV.

**Quan trọng:** OTA này **không** tự cập nhật APK/Java Android. Cần build và cài **APK 9.5.1 mới một lần** để kích hoạt hệ thống tự động OTA. Thay đổi RecorderService, quyền hệ thống hoặc native bridge về sau vẫn cần APK mới.

## 2. Build APK một lần

1. Đẩy toàn bộ nội dung **bên trong** thư mục `orbic-recorder-main` lên repository `gptlohoi-del/orbic-recorder` (đúng repo được cố định trong OtaManager), không sửa các bundle OTA cũ trong `ota/bundles`.
2. GitHub → **Actions → Tạo APK Orbic Recorder (không cần Release Key) → Run workflow** để build debug. Debug APK chỉ phù hợp cài thử mới.
3. Để **cài đè APK cũ mà không mất dữ liệu**, dùng **Actions → Optional signed Release APK** và cung cấp **đúng cùng release keystore/certificate** của APK đang cài (`ANDROID_RELEASE_KEYSTORE_B64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`). Bản native 9.5.1 có versionCode nhóm 160000+, cao hơn nhóm 150000+ của 9.5.0 trong repo.
4. Sao lưu WAV trước khi thao tác. **Không gỡ app trước khi sao lưu**, đặc biệt trên Android 9 khi file có thể nằm trong bộ nhớ riêng ứng dụng và sẽ mất khi gỡ cài đặt. Nếu cần, chọn thư mục lưu ngoài qua SAF để dễ giữ bản ghi.

## 3. Phát hành OTA tiếp theo, không cài APK lại

1. Chỉ sửa `app/src/main/assets/ui/index.html`, `app.css`, `app.js`. Không dùng OTA để phát hành thay đổi Java.
2. Thiết lập GitHub Actions secret `OTA_SIGNING_KEY_B64` là khóa RSA **riêng** khớp public key đã ghim trong APK. Không đưa khóa riêng vào source/ZIP.
3. Chạy **Actions → Publish Orbic Recorder OTA → Run workflow**. Script tự đóng gói, ký manifest và tăng phiên bản; với giao diện nhúng 8.0.8, bản OTA mới đầu tiên sẽ là **8.0.9**. `ota/latest.json` trong gói repo này là bản lịch sử 8.0.5, chưa tự kích hoạt cập nhật mới.
4. Mở ORBIC RECORDER trên thiết bị Android 9 có Internet. Nếu không ghi/lưu/phát, app sẽ tự áp dụng OTA. Nếu đang bận thì chờ kết thúc rồi áp dụng khi app còn mở. Nếu tắt công tắc, dùng **KIỂM TRA → CẬP NHẬT**.
5. Nếu phiên bản OTA lỗi, dùng **QUAY LẠI**. App không tự cài lại phiên bản bị quay lại ở lần mở tiếp theo; bản OTA mới hơn vẫn được xét.

## 4. Các giới hạn và bảo mật

- Không chạy tiến trình kiểm tra OTA liên tục khi ứng dụng ở nền/đã đóng; chỉ kiểm tra từ lúc mở app và chờ áp dụng khi app foreground. Không có APK self-install, không yêu cầu quyền cài từ nguồn không xác định.
- Chỉ chấp nhận manifest `nativeApi=8` ký đúng RSA và zip checksum SHA-256 trùng, URL gói phiên bản cố định. Không tắt xác minh này.
- Khi nâng cấp APK cần **cùng package ID và cùng chứng chỉ APK** với bản đang cài. `versionCode` phải tăng so với bản đang cài thực tế; nếu trước đó bản cũ đã có `versionCode` cao hơn 160000+, cần tăng thêm một mức an toàn trước khi build.
- `minSdk=28` nghĩa là dự án hỗ trợ API Android 9 về mặt cấu hình. Các test tự động kiểm tra mã và giao diện; cần build APK bằng CI và thử trên điện thoại Android 9 thật để xác nhận hoạt động thiết bị/micro/WAV/OTA mạng.

## 5. Kiểm thử tại repo

```bash
node --check app/src/main/assets/ui/app.js
for t in tests/test_*.py; do python3 "$t"; done
# Trong môi trường có Android SDK + Gradle 8.10.2:
gradle --no-daemon :app:assembleDebug
```
