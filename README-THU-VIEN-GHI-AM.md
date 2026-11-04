# Tính năng mới: Thư viện nghe lại WAV

## Cách dùng

Tại dòng **🎙 GHI THEO TIẾNG ĐỘNG** (góc phải của cùng dòng):

- **📁**: mở danh sách file WAV đã lưu **mới nhất ở đầu**, có số thứ tự, tên, ngày giờ, loại (đã lọc / gốc), và dung lượng. Chạm **▶** của một dòng để nghe bản ghi đó.
- **▶**: tự tìm bản ghi WAV đã lưu mới nhất rồi phát ngay trong trình nghe lại.

Trong cửa sổ thư viện có **TRƯỚC / PAUSE – TIẾP / SAU / STOP**, thanh **tua thời gian**, **↻ làm mới** và **✕ ĐÓNG**. Chạm dấu đóng **không dừng** bản ghi đang phát. Nếu muốn tắt âm thanh, dùng **STOP**. Trình phát nhạc trên màn hình chính vẫn hiển thị trạng thái phát để thuận tiện theo dõi.

## File được tìm ở đâu?

1. Nếu trong **Cài đặt → Lưu trữ** đã chọn thư mục lưu bằng Android, thư viện sẽ đọc file `orbic_*.wav` tại thư mục đó qua quyền SAF đã cấp.
2. Ở Android 10 trở lên, thư viện cũng quét `Music/OrbicRecorder/DaLoc` và `Music/OrbicRecorder/Goc` qua MediaStore để không bỏ sót file lưu dự phòng.
3. Trên Android 9, thư viện đọc file đã lưu trong thư mục Music riêng của ứng dụng khi không có thư mục SAF.

File mới nhất được xác định theo ngày/giờ ghi có trong tên WAV; nếu tên không có dấu thời gian chuẩn thì dùng ngày sửa đổi của file. Với hai file WAV cùng thời điểm, **bản đã lọc** được xếp trước **bản gốc**. Để giữ giao diện chạy tốt, hiển thị tối đa **500 bản ghi mới nhất**. Không đưa file `.pending` hoặc file đang ở khu vực ghi tạm vào danh sách.

## Đảm bảo an toàn

- Nghe lại dùng cùng trình phát native như phần nhạc. Khi cần phát file trong lúc đang thu, ứng dụng yêu cầu tạm nhường micro theo cơ chế cũ; khi phát kết thúc, app trả quyền nghe tiếng động về cho phiên đang chạy (trừ PAUSE thủ công).
- Việc nghe lại **không sửa và không xoá WAV**. Nếu bản ghi không còn ở vị trí lưu hoặc thiết bị đã thu hồi quyền thư mục, danh sách có thể không hiển thị file đó.
- Không tự động phát kế tiếp khi kết thúc một file, tránh phát âm thanh bất ngờ.
- Để nghe WAV ngắn rõ ràng, hiệu ứng fade-in/fade-out của bài nhạc không áp dụng cho WAV nghe lại.

## Cài đặt bản cập nhật

Cần **build và cài APK mới** từ mã nguồn này, vì các hàm `recordings`, `playRecording` và `seekRecording` nằm trong Java native. Cập nhật OTA web *một mình* không kích hoạt tính năng trên APK cũ. Giao diện tích hợp đã được tăng lên **8.0.6** so với bản OTA 8.0.5 trước đó để APK mới chủ động dùng giao diện có hai nút này.

Mã nguồn đã được kiểm tra tự động với Node/Python, bao gồm một bài kiểm thử riêng về sắp xếp mới nhất và điều khiển nghe lại. Trong môi trường tạo gói này chưa có Android SDK/Gradle để biên dịch và thử trực tiếp trên điện thoại; sau khi build cần kiểm tra thực tế với Android 9 và các phiên bản mới hơn.
