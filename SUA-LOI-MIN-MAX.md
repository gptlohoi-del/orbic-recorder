# Sửa lỗi MIN/MAX trong Cài đặt nâng cao

## Hiện tượng và nguyên nhân

Trong giao diện cũ, mọi lần sửa MIN hoặc MAX đều kiểm tra cả DEFAULT và STEP
chưa đổi. Ví dụ: MIN=0, MAX=100, DEFAULT=80; sửa MAX=50 bị từ chối vì DEFAULT=80.
Lỗi khiến việc chỉnh sửa từng ô theo thứ tự thông thường rất khó hoặc không thể.
Ngoài ra, phần nhiều tham số có giới hạn cứng của Android nên không thể tăng
MAX hoặc giảm MIN vượt khoảng an toàn.

## Bản sửa

- MIN và MAX chỉnh độc lập; khi cần, tự điều chỉnh DEFAULT và STEP để hợp lệ.
- Nếu MIN vượt MAX cũ, MAX tự theo MIN; trường hợp ngược lại tương tự.
- Hiển thị khoảng cho phép ngay dưới tên thông số.
- Chặn số trống, không nguyên, nằm ngoài khoảng an toàn; không xóa toàn bộ ô vừa nhập.
- Gửi giới hạn và giá trị hiện tại đã chuẩn hóa trong cùng lần lưu.
- Nâng phiên bản giao diện tích hợp lên **8.0.20** (cao hơn OTA 8.0.19)
  để APK mới không nạp lại giao diện OTA đã lưu trước đó.

## Triển khai

Muốn đưa vào điện thoại, cần build và cài một APK được ký bằng cùng chứng chỉ
với bản đang cài, hoặc phát hành bản OTA mới **8.0.21 trở lên** đã ký bằng
khóa OTA của dự án. File ZIP mã nguồn tự nó không cập nhật app trên điện thoại.
Không chỉnh tay `ota/latest.json` hoặc các ZIP OTA đã được ký.

Giới hạn cứng ở `MainActivity.java` vẫn được giữ nguyên để tránh nhận giá trị
không hỗ trợ. Ví dụ, âm lượng nhạc không thể có MAX lớn hơn 100%.
