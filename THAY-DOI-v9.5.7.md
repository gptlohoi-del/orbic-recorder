# Orbic Recorder v9.5.7 – PAUSE / CONTINUE trong AN TOÀN

- Tab AN TOÀN: START; PAUSE xác nhận bằng **vuốt trái → phải**; CONTINUE **chạm một lần**; SAVE xác nhận bằng vuốt trái → phải.
- Khi PAUSE, micro vẫn hoạt động để tránh lỗi khởi tạo lại nhưng các khung PCM không được ghi vào WAV; thời gian PAUSE không nằm trong file âm thanh.
- Vùng thông báo và thông báo Android đều hiển thị trạng thái PAUSE; vùng thông báo hiển thị thời gian tạm dừng đang trôi.
- STOP & SAVE dùng được cả lúc đang PAUSE; thời gian ghi WAV chỉ gồm các đoạn có ghi.
- Tab TIẾT KIỆM không có PAUSE; giữ nguyên tự kích hoạt và cắt khoảng lặng dài ≥ ngưỡng.
- Chuyển tab vẫn khóa khi đang ghi hoặc chờ tín hiệu; vẫn không có nút hủy bản ghi.
- Cập nhật nativeApi=13 và builtin UI=8.0.17; yêu cầu APK v9.5.7 mới, không thể chỉ OTA trên APK v9.5.6.
- Android 9 vẫn là minSdk 28. Cần thử nghiệm thực tế trên điện thoại để xác nhận micro, WAV và màn hình vuốt.
