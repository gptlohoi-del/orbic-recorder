## v9.5.1 – Tự động OTA an toàn (2026-11-05)

- Trong Settings → CẬP NHẬT OTA, thêm công tắc **TỰ ĐỘNG CẬP NHẬT OTA**, mặc định BẬT, tự lưu trong Android.
- Giữ kiểm tra OTA trên GitHub khi mở app; tự cài bản OTA mới hơn, có chữ ký và SHA256 đúng, nếu ứng dụng rảnh.
- Không cài khi đang ghi/khởi động ghi/lưu WAV/khôi phục hoặc phát nhạc; khi đang bận, thử cài lúc rảnh (kiểm tra nội bộ 3 giây/lần khi foreground).
- Chặn nút phát nhạc/WAV mở khi đang áp dụng OTA để tránh xung đột chuyển giao diện.
- Sau khi người dùng quay lại bản trước, không tự cài lại cùng một bản OTA đã quay lại; cập nhật thủ công hoặc phiên bản mới hơn vẫn có thể thực hiện.
- Phiên bản native 9.5.1, `versionCode=160000+GITHUB_RUN_NUMBER` để ưu tiên cài đè bản 9.5.0; giữ `minSdk=28`, `applicationId`, `nativeApi=8`, public key OTA, và các bundle đã phát hành. Tăng giao diện tích hợp thành 8.0.8; OTA phát hành mới bắt đầu 8.0.9.
- Không cung cấp APK ký sẵn; phải build và cài APK native mới một lần. Không có cơ chế cập nhật APK tự động qua OTA.

## Giao diện 8.0.7 – Tùy chỉnh theo yêu cầu (2026-11-05)

- Giữ lại tiêu đề Android `ORBIC RECORDER`, bỏ tiêu đề HTML trùng và đặt XOAY 180° cùng hàng PHÁT NHẠC.
- Thêm thanh chỉnh riêng kích thước chữ vùng THÔNG BÁO (75–200%), lưu bền trong cấu hình native.
- Mặc định mới: kiểu sóng `line` (Đường sóng mềm); ngưỡng phát hiện tắt; không giữ WAV gốc; chia WAV theo 60 phút.
- Không ghi đè cấu hình người dùng đã thay đổi trước khi cập nhật.
- Chạm trực tiếp tên hoặc thông tin bản ghi cũng phát/tạm dừng được như nút ▶.
- Giao diện nhúng lên 8.0.7 để không bị OTA 8.0.6 cũ thay thế. Không thay giao thức OTA nativeApi=8.
- Giữ minSdk 28 (Android 9) và cơ chế lưu trữ có thể chọn thư mục SAF.

## Giao diện 8.0.6 – Thư viện WAV trong ứng dụng

- Đặt 📁 và ▶ cạnh phải tiêu đề **GHI THEO TIẾNG ĐỘNG**; mở ngay danh sách và nghe bản ghi vừa lưu.
- Sắp file WAV mới nhất trước, hỗ trợ SAF / Music MediaStore / đường dẫn nội bộ Android 9, phân biệt lọc / gốc.
- Thêm trình nghe lại với phát, tạm dừng, tiếp, dừng, trước/sau và tua; không tự xóa hoặc can thiệp vào WAV.
- Bỏ fade nhạc khi nghe WAV ngắn để không mất đầu/cuối câu; tái sử dụng bảo vệ micro từ trình phát nhạc.
- Giao diện tích hợp tăng từ 8.0.3 lên 8.0.6, vượt OTA 8.0.5 cũ, tránh che khuất tính năng mới.
- Bắt buộc APK mới; OTA HTML/CSS/JS không tự bổ sung được cầu nối Android native.

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
