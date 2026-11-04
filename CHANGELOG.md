# Changelog

## v9.5.0 — 2026-11-04

- Nâng phiên bản Android lên 9.5.0 (`versionCode` = 150000 + `GITHUB_RUN_NUMBER`, `minSdk` 28), **không đổi applicationId**.
- Bổ sung capability flags cho ba nút điều khiển mới (song 3 và hủy phiên), tránh UI OTA trên Android native cũ gọi API không tồn tại; giữ tương thích mã v9.4.2 trước đó.
- Sửa trường hợp cập nhật APK mới vẫn dùng OTA cũ: giao diện gốc tăng từ 8.0.0 lên 8.0.3; OTA cũ hơn bị bỏ chọn, nhưng gói OTA 8.0.1/8.0.2 và chữ ký vẫn nguyên vẹn.
- Siết thao tác vuốt hủy: yêu cầu đủ quãng đường và độ dài thời gian, chặn gửi lệnh lặp; cơ chế hủy file theo `session`, đánh dấu bền và recovery vẫn giữ.
- Cho phép phát nhạc khi chưa cấp RECORD_AUDIO. Chỉ yêu cầu quyền khi bắt đầu ghi âm.
- Kích hoạt các nút PAUSE/TIẾP GHI và DỪNG & LƯU trên thông báo foreground, không cho hủy bằng notification.
- Thêm bài kiểm thử Node cho thao tác vuốt (nhả sớm, vuốt thiếu, hủy chạm, lặp, xoay CSS 180°) và test hợp đồng APK/OTA.
- Viết lại README và hướng dẫn Build/OTA/Signing. Không công bố OTA mới hoặc sinh APK ký sẵn.

## v9.4.2 — source provided

- Bật/tắt threshold (VAD), bảo vệ khi OTA chỉ giao diện; 3 bài nhạc, xoay WebView và hủy phiên bằng swipe đã có trong base code.
