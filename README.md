# Orbic Recorder Voice Filter v3.1 — Fixed source layout

Đã chuyển `MainActivity.java` và `RecorderService.java` bản v3.1 vào đúng `app/src/main/java/com/example/orbicrecorder/`.

Đã chuyển cấu hình build v3.1 vào `.github/workflows/build.yml`, xóa bản workflow trùng ở thư mục gốc và cập nhật `versionCode` lên 4, `versionName` thành `3.1-voice-filter`.

## Build
1. Sao lưu repository hoặc tạo branch trước khi cập nhật.
2. Upload nội dung thư mục này (không thêm một cấp thư mục bao ngoài) vào GitHub repository, ghi đè các file cũ.
3. Xác nhận `.github/workflows/build.yml` đã được cập nhật.
4. GitHub Actions -> Build Orbic Recorder v3.1 APK -> Run workflow, hoặc push sẽ tự chạy.
5. Tải artifact `orbic-recorder-v31-apk`.

Lưu ý: đây là APK debug, có thể không cài đè lên APK đã ký bằng khóa khác. Sao lưu file WAV trước khi gỡ ứng dụng. Chưa biên dịch/kiểm thử trong môi trường này.
