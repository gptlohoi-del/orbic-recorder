# Orbic Recorder Test – Android 12

Ứng dụng thử nghiệm ghi WAV bằng AudioRecord trong Foreground Service và mở Google Dịch.

## Tạo APK
1. Tạo repository GitHub mới, upload toàn bộ nội dung thư mục này (kể cả `.github/workflows/build-apk.yml`).
2. Vào **Actions** → **Build Orbic Recorder APK** → **Run workflow**.
3. Chờ build thành công → tải artifact `orbic-recorder-debug-apk` → giải nén → cài `app-debug.apk`.
4. Nếu GitHub yêu cầu bật Actions, vào Settings → Actions → General để cho phép workflows.

## Thử nghiệm
1. Cắm nguồn âm thanh 3.5 mm qua đầu vào hỗ trợ micro của điện thoại. Cổng tai nghe 3.5 mm không tự động có nghĩa là hỗ trợ line-in.
2. Mở Orbic Recorder, cấp quyền microphone, bấm BẮT ĐẦU GHI.
3. Quan sát **Nguồn** và **Mức tín hiệu**. Nếu 0 liên tục, kiểm tra dây TRRS/adapter và mức tín hiệu.
4. Bấm MỞ GOOGLE DỊCH, nhấn MICRO trong Google Dịch, thử nói/phát âm thanh khoảng 15 giây.
5. Kéo thông báo xuống, bấm DỪNG GHI. File WAV nằm tại Android/data/com.example.orbicrecorder/files/Recordings/ (trình quản lý file Android 12 có thể hạn chế truy cập thư mục này; có thể lấy bằng USB/ADB).

## Giới hạn
Android có thể cấp micro cho Google Dịch và làm im lặng luồng ghi nền. APK này không vượt qua giới hạn hệ điều hành. Ghi WAV 16kHz mono 16-bit. Không tự động tải file lên mạng.
