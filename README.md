# Orbic Recorder – Voice Filter v3.0

Ghi WAV mono PCM 16-bit / 16 kHz; phát hiện đoạn có tín hiệu dựa trên RMS/dBFS, loại khoảng lặng dài và nối các đoạn được giữ thành một WAV trong `Music/OrbicRecorder`.

## Cài đặt
- Ngưỡng dBFS mặc định -38; tăng lên (ví dụ -30) để lọc tiếng nền mạnh hơn; giảm xuống (-45) để giữ tiếng nhỏ hơn.
- Thời gian xác nhận tiếng: 180 ms.
- Pre-roll: 300 ms.
- Giữ phần cuối: 500 ms.
- Các giá trị chỉ có hiệu lực từ lần ghi kế tiếp.

## Build APK
Upload nội dung thư mục này lên nhánh `main` của repo GitHub. Actions -> `Build Orbic Recorder Voice Filter APK` -> Run workflow. Tải artifact `orbic-recorder-voice-filter-apk`.

## Giới hạn
Đây là lọc khoảng lặng theo mức âm thanh (energy-based gate), không phải mô hình nhận diện tiếng nói (VAD AI). Tiếng ồn lớn có thể bị giữ, tiếng nói nhỏ có thể bị bỏ. Hãy thử với file ngắn trước. Không có chức năng Google Dịch, OTA, PiP hoặc chọn thư mục trong bản thử nghiệm này. Không tự động khôi phục WAV chưa hoàn tất khi ứng dụng bị đóng cưỡng bức.
