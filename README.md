# Orbic Recorder v9.5.7 — AN TOÀN và TIẾT KIỆM

Android 9+ (minSdk 28), ứng dụng ghi WAV mono PCM 16 kHz / 16-bit và phát nhạc riêng.
Giao diện HTML/CSS/JS chạy trong WebView; ghi âm, quản lý file và phát hiện tín hiệu
được xử lý bằng Java Android. Không cần Internet để ghi âm.

## Hai tab ghi âm

**AN TOÀN** (mặc định lần đầu hoặc khi nâng cấp): chọn START để ghi tất cả các
khung âm thanh nhận được, kể cả khoảng lặng. Khi bấm PAUSE, phải
**vuốt từ trái qua phải** để xác nhận; trong thời gian PAUSE ứng dụng giữ micro
mở nhưng không thêm tín hiệu vào WAV. **CONTINUE chỉ cần chạm một lần**.
Vùng thông báo hiển thị thời gian đang PAUSE. Chọn SAVE, vuốt xác nhận
trái qua phải để dừng và lưu (kể cả khi đang tạm dừng). Không có Không lưu.

**TIẾT KIỆM**: chọn BẮT ĐẦU GHI KHI CÓ TÍN HIỆU. App mở micro và chờ mức RMS
vượt ngưỡng (mặc định -40 dBFS) liên tục khoảng 100 ms. App giữ tối đa
1 giây âm thanh đã nhận trước lúc kích hoạt. Khi đang ghi, app giữ nguyên
mọi khoảng lặng **ngắn hơn thời gian thiết lập**. Với một khoảng lặng đạt
ngưỡng thời gian (mặc định 60 giây), app rút gọn phần giữa, chỉ giữ lại
2 giây ở đầu và 2 giây cuối. Mọi tín hiệu được phân loại theo mức RMS, **không
phải bộ nhận dạng tiếng người**: giọng nói quá nhỏ kéo dài có thể bị cắt.

Có thể chỉnh thời gian lặng tại Cài đặt → GHI ÂM: 20–1000 giây, bước 10 giây.
Có thể chỉnh ngưỡng tín hiệu -60 đến -15 dBFS. Hai thông số được khóa trong
suốt thời gian đang chờ hoặc đang ghi. Tab đang dùng được lưu cho lần mở sau;
không đổi tab khi đang ghi/chờ/lưu. START thủ công chỉ có ở tab AN TOÀN.

**Mỗi phiên chỉ tạo một WAV chính**, không tự lưu thêm `_raw.wav`. Những file
`_raw.wav` của phiên bản cũ vẫn có thể xuất hiện trong thư viện; bản nâng cấp
không xóa bản ghi cũ. WAV có thể được chia theo cấu hình 1–60 phút (thời
điểm chia phần được hoãn nếu đang xét một khoảng lặng dài để tránh mất dữ liệu).

## Cảnh báo và an toàn

- Khi nguồn âm thanh đổi hoặc rút micro, ứng dụng hiển thị cảnh báo tại vùng
  thông báo và trong thông báo Android, nhưng không tự ngắt phiên ghi.
  Một số nguồn âm không cung cấp định danh thiết bị: cảnh báo không đảm bảo
  phát hiện mọi trường hợp dây PHONES bị tuột.
- Khi dung lượng khu vực ghi tạm còn dưới 128 MB, ứng dụng cảnh báo và khuyên
  SAVE. Dữ liệu không thể được bảo đảm nếu bộ nhớ đầy hoặc Android đóng tiến trình.
- Khi START/ARM, ứng dụng từ chối nếu trình phát nhạc đang hoạt động; khi đang
  ghi thì không được mở phát nhạc để tránh nhả micro.
- Trong TIẾT KIỆM, không có bản RAW để khôi phục phần bị cắt. Dùng AN TOÀN nếu
  một câu nói bị mất là không thể chấp nhận.
- Dùng cổng PHONES của mixer với bộ chuyển mức và chuyển chân phù hợp; mức
  PHONES MIN không đảm bảo đầu vào điện thoại hoàn toàn bằng 0. Bản ghi không
  thể khôi phục âm thanh khi mixer chưa đưa tín hiệu tới điện thoại.

## Build, kiểm thử và cập nhật

- `minSdk 28`, `targetSdk 32`, `compileSdk 35` (Android 9 vẫn có thể cài).
- `nativeApi=13`, giao diện tích hợp `8.0.20` để tránh OTA cũ đè giao diện
  của bản mới. **Cần build/cài APK mới**, OTA giao diện không thể bổ sung lệnh PAUSE ghi âm native.
- GitHub Actions: **Tạo APK Orbic Recorder**, hoặc bản Release có khóa ký.
  Cài đè bằng APK cùng applicationId, cùng khóa ký và versionCode cao hơn;
  không gỡ ứng dụng khi chưa sao lưu WAV.
- Test: `python3 tests/test_silence_compactor.py`, sau đó chạy `tests/test_*.py`
  hoặc để GitHub Actions chạy tự động. Các test mã nguồn không thay cho thử trên
  thiết bị Android 9 thật.

Xem thêm `THAY-DOI-v9.5.7.md` và `HUONG-DAN-GHI-AM-AN-TOAN.md`.
