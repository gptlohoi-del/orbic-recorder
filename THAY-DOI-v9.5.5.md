# Orbic Recorder v9.5.5 – Tự kích hoạt một lần

## Thay đổi
- Nút trên màn hình chính **BẮT ĐẦU GHI KHI CÓ TÍN HIỆU**: xám mặc định; bấm để chờ thì xanh, bấm lại để hủy chờ. Nếu đã kích hoạt thì nút vẫn xanh nhưng không thể tắt phiên ghi bằng nút này.
- ARM mở micro nhưng chưa tạo phiên WAV. Ngưỡng kích hoạt theo RMS dBFS: mặc định -40 dBFS, đủ 5 khung liên tiếp (mỗi khung 20ms). Đệm 50 khung = 1 giây âm thanh trước lần kích hoạt để tránh mất đầu câu.
- Sau khi kích hoạt, luôn ghi PCM liên tục, không dùng ngưỡng để xóa khoảng im lặng. Nếu điều chỉnh PHONES về MIN sau khi ghi bắt đầu, WAV vẫn chạy đúng dòng thời gian.
- Bắt buộc bật WAV gốc cho chế độ tự kích hoạt; lựa chọn giữ bản WAV chính và bản RAW trước Limiter.
- Cả **DỪNG & LƯU** lẫn **DỪNG & KHÔNG LƯU** giữ thao tác xác nhận vuốt từ **trái sang phải**; không có PAUSE ghi âm.
- Cài đặt → VÙNG THÔNG BÁO: bảng màu, cỡ chữ 75–200%, độ đậm 400–900. Cài đặt → GHI ÂM: mức tín hiệu để tự bắt đầu (-60 đến -15 dBFS). Các tùy chỉnh tự lưu.

## Vận hành & rủi ro
1. Cắm mixer PHONES qua giao tiếp đúng chuẩn CTIA và bộ suy giảm mức âm phù hợp với đầu vào micro điện thoại. Không dùng dây TRS–TRRS chưa xác định đúng chân/mức điện áp.
2. PHONES tại MIN không bảo đảm micro đầu vào yên lặng hoàn toàn; nhiễu, AGC hoặc chuyển sang micro nội có thể kích hoạt nhầm. Chỉnh mức kích hoạt trước khi nhấn ARM; thử một phiên ngắn.
3. Trước khi sử dụng thật, xác nhận nguồn micro và tín hiệu dBFS trên máy; thử nói nhỏ rồi ngừng nhiều giây để bảo đảm WAV vẫn liên tục.
4. Chế độ tự động không thể bảo đảm nhận lời quá nhỏ chưa vượt ngưỡng. Chế độ START thủ công là lựa chọn đáng tin cậy nhất khi không chấp nhận bỏ lỡ bất kỳ đoạn nào.
5. Khi tự kích hoạt, hai file WAV dùng gấp đôi dung lượng so với một WAV. Kiểm tra bộ nhớ và đừng gỡ APK cũ trước khi sao lưu file.

## Kiểm thử & triển khai
- Chạy `python tests/test_*.py` từng file, kiểm tra cú pháp app.js bằng `node --check`.
- Bộ kiểm tra Java độc lập: `javac -d build/test app/src/main/java/com/example/orbicrecorder/InputSignalTrigger.java tests/InputSignalTriggerTest.java` rồi `java -cp build/test InputSignalTriggerTest`.
- Mã native đã đổi (`MainActivity.java`, `RecorderService.java`, `RecordingArchive.java` và lớp kích hoạt) nên phải biên dịch **APK mới** để dùng chế độ ARM. Không thể chỉ OTA HTML/CSS/JS lên APK v9.5.4.
- `nativeApi=11`, `builtInVersion=8.0.15`; OTA mới cần được phát hành theo cùng API kèm chữ ký hợp lệ.
- Bản này là **mã nguồn, chưa biên dịch APK và chưa thử trên điện thoại thực tế**.
