# ORBIC RECORDER v9.5.2 — Khóa xoay cảm biến và xoay thủ công

## Cài đặt

Trong **⚙ CÀI ĐẶT → 🎨 GIAO DIỆN**, có công tắc **Tự động xoay màn hình theo điện thoại**.

- **Mặc định TẮT**, kể cả khi nâng cấp từ APK cũ chưa có công tắc này. Khi TẮT, Android giữ ứng dụng ở chiều dọc dù bạn xoay điện thoại.
- Khi **BẬT**, Android dùng chế độ cảm biến `FULL_SENSOR`: màn hình thay đổi theo hướng cầm điện thoại.
- Trạng thái công tắc được lưu vào `SharedPreferences(config)` và có hiệu lực ngay mà không khởi động lại ứng dụng.
- Công tắc này không tắt các nút xoay bằng tay.

## Các nút xoay trên màn hình chính

Hai nút nằm bên phải dòng **♫ PHÁT NHẠC**, theo thứ tự:

1. **↻ XOAY 90°** (màu cam): xoay thêm 90° theo chiều kim đồng hồ sau mỗi lần bấm: 0 → 90 → 180 → 270 → 0.
2. **⤾ XOAY 180°** (màu xanh): xoay thêm 180°. Từ 0° bấm lần thứ hai sẽ về 0°.

Góc cuối được nhớ khi bạn mở ứng dụng lần sau. Góc 180° từ bản cũ được tự động chuyển sang cài đặt mới. Giao diện **WebView**, hộp thoại và vùng chạm đều quay theo; thanh trạng thái/thanh điều hướng Android bên ngoài WebView không quay theo nút.

Ở 90° và 270°, native Android đổi chỗ chiều rộng và chiều cao WebView trước khi xoay, tránh cắt giao diện hay lệch thao tác chạm. Thiết kế này cũng hoạt động khi tắt cảm biến xoay.

## Cập nhật từ APK v9.5.1

**Phải build và cài APK mới 1 lần**, vì tính năng là `MainActivity.java` và `AndroidManifest.xml`, không thể đưa vào ứng dụng cũ chỉ bằng gói OTA HTML/JS.

- Android tối thiểu vẫn **Android 9 (API 28)**.
- `applicationId` không đổi: `com.example.orbicrecorder`.
- APK mới là **9.5.2**, `versionCode=170000+GITHUB_RUN_NUMBER`, cao hơn dải 160000+ từ v9.5.1.
- Để cài đè giữ dữ liệu, ký APK mới bằng **đúng khóa ký của APK cũ**. APK Debug và Release thường dùng khóa khác nhau, không cài đè lẫn nhau.
- **Sao lưu các bản ghi WAV trước khi nâng cấp, không gỡ ứng dụng cũ** nếu chưa sao lưu.

### Quy trình build

Đưa toàn bộ nội dung thư mục repo lên GitHub, vào **Actions → Tạo APK Orbic Recorder (không cần Release Key)** để dựng APK Debug; hoặc dùng workflow release kèm secrets khóa ký nếu muốn cập nhật đè bản Release đã cài.

Các bài kiểm thử trong thư mục `tests/` chạy ở CI; có bài `tests/test_screen_orientation.py`. CI kiểm tra `minSdk 28` của APK với công cụ Android. Các bài kiểm tra mã nguồn/giao diện không thay thế thử trên điện thoại Android 9 thật.

## Tự động OTA sau khi cập nhật

`TỰ ĐỘNG CẬP NHẬT OTA` vẫn **BẬT theo mặc định** và giữ các điều kiện chờ thiết bị rảnh để không gián đoạn ghi âm.

Để giữ giao diện mới, APK sử dụng **nativeApi=9**, UI nhúng **8.0.9**. Gói OTA tiếp theo phải là **8.0.10** hoặc mới hơn, được ký đúng khóa RSA và khai báo `nativeApi=9`. Trong repo các bundle `8.0.1` – `8.0.5` và `ota/latest.json` cũ được giữ nguyên làm lịch sử; chúng không thể cập nhật APK mới. Khi cần phát hành, chạy **Publish Orbic Recorder OTA** bằng secret `OTA_SIGNING_KEY_B64`; đừng sửa bản JSON đã ký bằng tay.
