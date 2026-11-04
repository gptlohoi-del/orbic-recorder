# BÁO CÁO RÀ SOÁT ORBIC RECORDER

**Cơ sở:** `ORBIC-Recorder-v9.4.2-Tat-Nguong-OTA-Source.zip` và repo gốc kèm cuộc trò chuyện. Phiên bản xuất: **9.5.0**, ngày 04/11/2026.

## Những phần đã giữ nguyên vì đang hợp lý

- Ghi PCM/WAV song song với tùy chọn RAW, lọc theo ngưỡng hoặc ghi liên tục; sửa header WAV và `fsync` định kỳ.
- Ghi tách segment và xuất trên executor riêng, khôi phục WAV chưa xuất sau sự cố.
- Xác nhận `DỪNG & KHÔNG LƯU` bằng vuốt; dấu `.discard` và `.exports` để chỉ xóa file của phiên hiện tại, có thử lại sau crash.
- Phát nhạc luân phiên micro (yêu cầu release micro trước khi bật nhạc), fade khi pause/stop và 4 kiểu sóng âm.
- OTA giao diện có RSA/SHA-256, đường dẫn download giới hạn, không cho tải Java code.

## Những điểm đã sửa trong repo v9.5.0

1. **Xung đột OTA sau nâng cấp APK (quan trọng):** APK mới v9.4.2 nhúng UI 8.0.0 có thể vô tình dùng gói 8.0.2 cũ. Nâng UI nhúng lên 8.0.3, áp dụng quy tắc không dùng OTA thấp hơn giao diện trong APK và không rollback xuống bản cũ hơn.
2. **Capability negotiation:** nút Bài 3 và nút hủy bị khóa trên APK chưa có native engine tương ứng, thay vì bị hiện nhưng thao tác vô tác dụng.
3. **An toàn thao tác xóa:** vuốt phải đi đủ ~92%, kéo có chủ đích ít nhất 180 ms, chống gửi lặp cho tới khi state Android phản hồi hoặc báo lỗi.
4. **Quyền riêng tư/UX:** phát file nhạc không cần xin quyền micro; quyền thu chỉ hỏi ở START.
5. **Điều khiển khi app thu nhỏ:** notification đã có PendingIntent nhưng chưa gắn nút chức năng; đã thêm `PAUSE/TIẾP GHI` và `DỪNG & LƯU`.
6. **Dễ phát hành và kiểm thử:** viết lại hướng dẫn chuẩn v9.5.0, tăng versionCode để cài đè, thêm regression tests và chạy toàn bộ tests cả trước khi ký Release APK.

## Khuyến nghị cho đợt nâng cấp tiếp theo (chưa thay đổi vì cần thử trên thiết bị)

- **Tách lớp lớn:** `MainActivity` đang kiêm JS Bridge, Settings, bộ điều phối phát nhạc/thu và OTA; `RecorderService` kiêm service, capture, frame VAD và xuất WAV. Có thể tách `RecorderController`, `MusicCoordinator`, `SettingsRepository`, `WebBridge` để giảm nguy cơ lỗi liên quan và dễ viết test.
- **Giảm ghi SharedPreferences dày:** vòng ghi cập nhật trạng thái mỗi ~100 ms, trong khi UI poll mỗi 220 ms; cân nhắc giữ bộ đệm realtime riêng và chỉ persist thay đổi quan trọng để giảm I/O/battery.
- **Kiểm thử máy thật:** đặc biệt Android 9 SAF, MediaStore 10+, USB/Bluetooth mic, âm thanh rất nhỏ, xóa chunk đã xuất, app bị hệ thống kill, quay 180° khi đang vuốt và nhận cuộc gọi trong lúc ghi.
- **Sao lưu dữ liệu:** cho người dùng tùy chọn xuất file log lỗi và cảnh báo dung lượng đĩa trước khi thu lâu; mặc định WAV RAW + WAV chính có thể làm dung lượng tăng nhanh.
- **Build tái lập:** có thể bổ sung Gradle Wrapper đã kiểm tra checksum và bộ test instrumentation sau khi môi trường SDK/thiết bị sẵn sàng.

## Mức độ xác nhận

- Đã chạy toàn bộ `tests/test_*.py` và `node --check`; test Java AudioDynamics chạy thành công; 3 file OTA lịch sử không đổi và SHA-256 của bundle 8.0.2 khớp `latest.json`.
- **Chưa build được APK hoặc thử điện thoại thật trong môi trường hiện tại** (không có Android SDK/Gradle). Workflow GitHub Actions được cấu hình để build Debug và Release, kiểm tra `minSdk 28` và chữ ký. APK Release yêu cầu keystore cũ.
- Không phát hành gói OTA mới; SHA-256/Signature OTA chưa bị thay đổi. OTA tiếp theo khi có signing key là 8.0.4 hoặc cao hơn.
