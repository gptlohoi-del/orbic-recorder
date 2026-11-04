# ORBIC RECORDER v9.4 — MÃ NGUỒN APK MỚI

Đây là TOÀN BỘ dự án Android (không chỉ 3 file vá). Giữ `applicationId=com.example.orbicrecorder`, minSdk=28 (Android 9), các chức năng âm nhạc/ghi âm cũ, và cơ chế OTA có chữ ký.

## 3 bổ sung

1. Thanh tiêu đề **ORBIC RECORDER** có nút **⤾ XOAY 180°**: gọi `setRequestedOrientation()` từ Android, điều khiển màn hình xoay 180° thực sự ở cả dọc và ngang; bấm lần nữa đảo lại.
2. Trình phát thêm **▶ BÀI 3**, nút **📂** bên cạnh, lưu URI và tên qua Android SAF như BÀI 1/BÀI 2. Ba bài được nhớ lại sau khi khởi động ứng dụng.
3. Bên dưới START / PAUSE / DỪNG & LƯU có **✕ DỪNG & KHÔNG LƯU**. Chỉ sau khi kéo nút tròn đến >=92% hành trình mới gửi lệnh `STOP_DISCARD`. Dừng phiên thu, đóng file đang mở, chờ toàn bộ tác vụ xuất file, xóa WAV theo mã phiên, gồm cả các phần đã lưu vào MediaStore/SAF (nếu nhà cung cấp cho phép xóa). Không xóa phiên trước. Nếu không xóa hết, hiển thị cảnh báo và lưu trạng thái để thử lại khi khởi động.

## APK / Chữ ký / Nâng cấp

**Build APK debug:** đẩy nội dung repo lên GitHub, mở tab Actions, chọn workflow `Tạo APK Orbic Recorder (không cần Release Key)` > `Run workflow`. Tải artifact `ORBIC-RECORDER-v9.4-debug` chứa APK. Hoặc trong máy đã cài Android SDK 35 + Java 17 + Gradle 8.10.2:

```bash
gradle :app:assembleDebug
```

APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`. APK debug có chữ ký DEBUG, **không thể cài đè APK Release ký bằng chứng chỉ khác**. Hãy dùng workflow `build-release.yml` với **chính release keystore cũ** để build APK có thể cập nhật lên bản đã cài mà không mất dữ liệu.

Cần thiết lập các GitHub Secrets theo `RELEASE_SIGNING_GUIDE.md` (`ANDROID_RELEASE_KEYSTORE_B64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`). Sau khi push, Actions sẽ build và cho tải signed release artifact (tên `Recorder-9.4.<run>.apk`).

Không cần thay đổi package name. `versionCode` đã nâng từ `100000 + run` lên `110000 + run`.

## OTA

Native API tăng từ `7` lên `8`; giao diện nhúng tăng từ `7.0.0` lên `8.0.0`. Việc này cố ý vô hiệu hóa giao diện OTA cũ còn lưu trên thiết bị, để 3 nút không biến mất sau nâng cấp APK. Sau khi cài APK này, OTA chỉ phát hành HTML/CSS/JS có chữ ký với `nativeApi=8`, qua `scripts/publish_ota.py` và workflow `publish-ota.yml`. APK v9.3 (nativeApi=7) sẽ không nhận được OTA dành riêng cho v9.4.

## Kiểm tra

```bash
node --check app/src/main/assets/ui/app.js
python3 tests/test_project.py
python3 tests/test_v94_features.py
python3 tests/test_android9_compat.py
python3 tests/test_audio_dynamics.py
```

**Cần test thực tế trên điện thoại:** xoay ngang/dọc cả hai chiều, chọn phát BÀI 3, ghi trên 1 phút (đã xuất ít nhất một đoạn) rồi vuốt DỪNG & KHÔNG LƯU, kiểm tra thư mục WAV gốc và WAV đã lọc, kiểm tra trường hợp thư mục SAF từ ứng dụng quản lý file khác. Một số nhà cung cấp SAF có thể từ chối xóa; ứng dụng sẽ báo lỗi thay vì giả vờ đã xóa. Nút KHÔNG LƯU không thể thu hồi file đã được người dùng chép/đồng bộ sang nơi khác.
