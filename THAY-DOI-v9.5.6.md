# Orbic Recorder v9.5.6

- Giao diện: hai tab AN TOÀN/TIẾT KIỆM. Tab AN TOÀN mặc định lần đầu, lần sau nhớ tab vừa chọn.
- Khóa tab khi chờ tín hiệu, ghi âm, lưu hoặc khôi phục.
- AN TOÀN: START ngay, ghi WAV liên tục; không PAUSE/Không lưu; SAVE vuốt trái → phải.
- TIẾT KIỆM: tự START khi có tín hiệu; khoảng im lặng dưới thời gian quy định giữ đủ;
  khoảng đạt ngưỡng rút gọn còn khoảng 2 giây đầu và 2 giây cuối.
- Thời gian cắt lặng: mặc định 60 giây, 20–1000 giây, bước 10 giây, tự lưu.
- Không tạo WAV RAW phụ, giữ các file RAW cũ trong thư viện.
- Cảnh báo nguồn đầu vào thay đổi và dung lượng staging dưới 128 MB.
- Xử lý native: SilenceCompactor hỗ trợ hoàn tác phần đuôi file WAV trên đĩa mà
  không giữ tối đa 1000 giây PCM trong RAM.
- OTA nativeApi=12; BUILTIN_VERSION=8.0.16; build APK v9.5.6, minSdk vẫn 28.
- Bài kiểm tra mới: mô phỏng ghi 19/20/59/60/90/999/1000 giây im lặng,
  và kiểm tra ngón tay chỉ SAVE khi vuốt đủ từ trái sang phải.

Lưu ý: TIẾT KIỆM không nhận diện tiếng người. Nói quá nhỏ dưới ngưỡng kéo dài
có thể bị coi là im lặng và bị cắt, không thể phục hồi vì không có RAW.
