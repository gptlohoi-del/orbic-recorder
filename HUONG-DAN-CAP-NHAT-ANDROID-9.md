> **Lưu ý:** Tài liệu này mô tả bản 8.0.7 trước đó. Với chức năng tự động OTA mới, đọc [HƯỚNG DẪN TỰ ĐỘNG OTA / ANDROID 9](HUONG-DAN-TU-DONG-OTA-ANDROID9.md).

# ORBIC RECORDER — Hướng dẫn cập nhật mã nguồn Android 9

Gói này là **full repo Android Studio**, không phải APK cài đặt sẵn.

## Các thay đổi (giao diện nhúng 8.0.7)

1. Bỏ tiêu đề HTML `ORBIC RECORDER` bị lặp. Giữ thanh tiêu đề Android và chuyển nút XOAY 180° lên cùng hàng PHÁT NHẠC.
2. Cài đặt → 🔔 VÙNG THÔNG BÁO: kích thước chữ 75–200%, mặc định 100%, tự lưu.
3. Giao diện mặc định: Đường sóng mềm.
4. Ghi âm mặc định: ngưỡng nhận diện TẮT, không lưu WAV gốc, chia file 60 phút.
5. Trong danh sách bản ghi, chạm tên file/thông tin hoặc nút ▶ để phát/tạm dừng. Mới nhất luôn đầu danh sách.

Các mặc định trên chỉ được áp dụng **khi chưa có cài đặt đã lưu**. Nếu trước đó bạn đã tự chọn một giá trị khác, ứng dụng giữ giá trị cũ để không tự ý thay đổi thói quen ghi âm.

## Biên dịch

- Android Studio với JDK 17, Android SDK API 35 hoặc GitHub Actions trong `.github/workflows/`.
- `compileSdk=35`, `targetSdk=32`, `minSdk=28` (Android 9), `applicationId=com.example.orbicrecorder`.
- Có thể chạy `gradle --no-daemon :app:assembleDebug` nếu máy đã cài Gradle 8.10.2 và Android SDK.
- Repo có workflow **Tạo APK Orbic Recorder (không cần Release Key)** để build APK debug; workflow release cần keystore cũ.

## Cài đặt nâng cấp an toàn

- Nếu ứng dụng đang cài là APK release, chỉ APK **được ký bằng chính keystore cũ** mới có thể cài đè mà không mất dữ liệu.
- APK debug thường dùng chứng chỉ khác và **không thể** cài đè bản release.
- **Không gỡ bản đang sử dụng trước khi sao lưu bản ghi**, nhất là Android 9: bản ghi trong vùng lưu trữ riêng của app có thể mất khi gỡ cài đặt.
- Nên vào Cài đặt → Lưu trữ → Chọn thư mục WAV trên Android 9 để ghi ra thư mục người dùng quản lý qua SAF.
- Sau cài, xác nhận 5 lựa chọn mới trong phần Setting, ghi thử vài giây, nghe lại qua tên file, thử START/PAUSE/DỪNG và thử chia file dài trên thiết bị thật.

## Kiểm tra

Chạy `node --check app/src/main/assets/ui/app.js` và `for t in tests/test_*.py; do python3 "$t"; done`.

Các kiểm tra mã nguồn và thử giao diện bằng Chromium chỉ là **kiểm tra trước khi build**, không xác nhận chạy thật trên Android 9. Cần biên dịch APK và thử trên thiết bị Android 9 để kiểm chứng đầy đủ microphone, SAF, phát lại và ghi liên tục 60 phút.

## OTA

Giao diện tích hợp là **8.0.7**, giữ `nativeApi=8`. Giao diện OTA cũ <=8.0.6 không được phép thay thế UI mới khi APK này khởi động. Chỉ phát hành OTA 8.0.8 hoặc mới hơn qua quy trình ký RSA hiện có. Không thay gói OTA cũ đã ký trong thư mục `ota/bundles/`.
