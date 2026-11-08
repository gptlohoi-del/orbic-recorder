# ORBIC RECORDER v9.5.5 — Ghi âm liên tục, ưu tiên giữ WAV đầy đủ

Ứng dụng Android (Android 9 trở lên) ghi giọng nói thành WAV PCM 16 kHz, mono, 16-bit. Phiên ghi luôn lưu **mọi khung PCM thu được**, kể cả im lặng và nói nhỏ; không có lọc tiếng động, không có nút PAUSE ghi âm, không có chế độ VIDEO riêng.

## Giao diện

- **● START** bắt đầu thu thực sự; nếu nhạc vẫn đang phát thì từ chối khởi động để tránh tạo lỗ hổng thời gian.
- **■ DỪNG & LƯU** chỉ mở hộp xác nhận. Vuốt đầy đủ **từ trái sang phải** để lưu. Chạm ngắn hay vuốt chưa đủ không dừng ghi.
- **✕ DỪNG & KHÔNG LƯU** cũng yêu cầu vuốt đầy đủ **từ trái sang phải**. Hành động này xóa toàn bộ WAV của phiên hiện tại, kể cả các đoạn đã lưu theo thời gian; không thể hoàn tác. Các phiên trước không bị ảnh hưởng.
- Không có nút PAUSE cho ghi âm. Thông báo Android chỉ hiển thị trạng thái và mở ứng dụng; không có PAUSE hay STOP một chạm.
- Nút PAUSE của **trình phát nhạc và nghe lại file** là chức năng riêng, không phải PAUSE ghi âm. Không thể phát nhạc khi đang ghi; phải dừng nhạc hoàn toàn trước START.

## File WAV

- `_voice.wav`: ghi liên tục, qua Limiter nếu có bật. Đây là bản ghi chính.
- `_raw.wav`: ghi liên tục trước Limiter phần mềm. Mặc định bật khi cài APK v9.5.5 lần đầu / nâng cấp; có thể tắt khi dùng START thủ công. Chế độ tự kích hoạt luôn tự bật lại WAV gốc để an toàn, nên cần đủ dung lượng cho hai file.
- File được chia theo khoảng thời gian cấu hình (mặc định 60 phút) để giảm rủi ro mất toàn bộ dữ liệu khi lỗi. Tiến trình cập nhật header WAV và đồng bộ dữ liệu định kỳ 5 giây.
- Android 9 có thể dùng bộ nhớ ứng dụng; hãy **chọn thư mục SAF** để tránh mất file nếu gỡ cài đặt. Sao chép file quan trọng ra nơi khác.

## An toàn & lưu ý

- Vuốt bảo vệ khỏi thao tác chạm nhầm, **không** bảo vệ hoàn toàn trước mất điện, hỏng micro, hết bộ nhớ, ứng dụng bị hệ điều hành dừng, hoặc lỗi thiết bị.
- Nếu thu cùng video ở thiết bị khác, vẫn cần đồng bộ điểm đầu (ví dụ một tiếng vỗ tay), kiểm tra độ trôi đồng hồ và tránh đổi nguồn micro.
- Cân nhắc bật WAV gốc, bật cảnh báo đầu vào gần quá tải, thử thu trước sự kiện và kiểm tra dung lượng trống.
- Tùy chọn AGC và Limiter hoạt động độc lập. WAV gốc không tránh được méo tiếng xảy ra ngay tại đầu vào.

## Cập nhật

**Cần xây dựng và cài APK mới v9.5.5 một lần**, vì thuật toán ghi liên tục và việc chặn các lệnh cũ nằm trong Java Android. OTA chỉ thay đổi giao diện đã ký; dự án dùng native API `11` và giao diện tích hợp `8.0.15` để tránh áp dụng OTA cũ chứa các nút đã bị xóa. Để giữ dữ liệu, cài đè APK cùng application ID, cùng khóa ký và versionCode cao hơn, không gỡ APK cũ.

Dùng `.github/workflows/build.yml` để build APK debug, hoặc `.github/workflows/build-release.yml` với các biến khóa ký được mô tả trong `RELEASE_SIGNING_GUIDE.md` để build bản Release cài đè. Chưa có APK được biên dịch trong gói mã nguồn này; cần thử trên thiết bị thật.

Xem [HUONG-DAN-GHI-AM-AN-TOAN.md](HUONG-DAN-GHI-AM-AN-TOAN.md) để kiểm tra trước khi ghi sự kiện quan trọng.


## v9.5.5 – Tự bắt đầu khi có tín hiệu

- Nút **BẮT ĐẦU GHI KHI CÓ TÍN HIỆU** trên màn hình chính: xám khi tắt, xanh khi chờ; chạm lại để hủy chờ.
- Micro được mở trong trạng thái chờ, chỉ tạo phiên/file khi đầu vào đạt mức kích hoạt tối thiểu 100ms. Giữ đệm tiền ghi ~1 giây để tránh mất âm đầu.
- Sau khi kích hoạt, ghi liên tục từng khung PCM vào WAV, **không ngắt vì im lặng**, không PAUSE; Lưu/Không lưu đều xác nhận vuốt **trái sang phải**.
- Cài đặt → Ghi âm: chỉnh mức kích hoạt chỉ cho lúc chờ (mặc định -40 dBFS). Cài đặt → Vùng thông báo: chỉnh cỡ, màu và độ đậm chữ.
- Vì thay đổi ghi âm Java/native, cần build **APK v9.5.5**, không thể dùng OTA để bổ sung chức năng này lên APK cũ. `nativeApi=11` để tránh OTA tương thích sai.
- Chú ý mức PHONES=MIN không chắc chắn là số 0 tuyệt đối, AGC/tạp âm vẫn có thể kích hoạt nhầm; thử thực tế và sử dụng cáp suy giảm mức tín hiệu, CTIA/TRRS đúng chuẩn nếu cắm cổng headset điện thoại.
