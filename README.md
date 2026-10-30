# Orbic Recorder v4.0 — Bộ mã nguồn MỚI, độc lập 100%

Ứng dụng Android 12 (Orbic Q10), viết bằng Java thuần, không dùng thư viện ngoài.

## Các chức năng

- Thu âm WAV 16.000 Hz, mono, PCM 16-bit qua nguồn micro Android đang sử dụng (có thể là ngõ 3.5 mm nếu máy nhận dạng ngõ đó).
- Lọc các khoảng âm thanh dưới ngưỡng dBFS; ghép đoạn được giữ lại thành **một WAV**.
- Giữ **1.000 ms trước tiếng nói** và **1.000 ms sau tiếng nói** theo mặc định.
- Có cài đặt ngưỡng âm lượng, thời gian xác nhận có tiếng, đệm trước/sau, cảnh báo tín hiệu.
- Sóng âm đầu vào; Pause/Tiếp tục (không tạo file mới); Dừng/Lưu.
- Theme Sáng / Tối / Theo hệ thống.
- Hiển thị thời gian thu, thời lượng được giữ và số đoạn.
- Nghe bản ghi gần nhất; chọn thư mục Android SAF hoặc lưu mặc định `Music/OrbicRecorder`.
- Ghi âm nền qua Android Foreground Service, có nút điều khiển trên thông báo.
- Không có bất kỳ chức năng Google Dịch nào.

**Lưu ý:** bộ lọc dựa trên âm lượng, không phân biệt được tiếng nói với tiếng động lớn. WAV được lưu lúc dừng; máy bị tắt nguồn đột ngột có thể để lại file tạm trong dữ liệu ứng dụng. Bản này không tích hợp OTA hoặc cửa sổ nổi.

## A. XÓA HẾT file cũ rồi tải bản này lên GitHub

1. **Sao lưu** bản ghi cần thiết trên điện thoại. Việc xóa mã nguồn trên GitHub không xóa bản ghi trong điện thoại, nhưng việc gỡ ứng dụng khi đổi khóa ký có thể xóa dữ liệu riêng của ứng dụng.
2. Vào repository `orbic-recorder` trên GitHub. Xóa những file mã nguồn cũ mà bạn không cần (hoặc tốt hơn: tạo repository mới để có thể quay lại bản cũ nếu cần).
3. **Giải nén** `orbic-recorder-fresh-v4-source.zip` vào một thư mục trống trên máy tính. Bạn phải tải lên **các file/thư mục bên trong đó** (gồm `.github`, `app`, `build.gradle`...), KHÔNG tải ZIP như một file và KHÔNG đặt toàn bộ mã trong một thư mục con.
4. Đảm bảo có cả thư mục ẩn `.github/workflows` cùng file `build.yml`. Nếu giao diện web khó tải thư mục ẩn, dùng GitHub Desktop hoặc tạo file `.github/workflows/build.yml` thủ công.
5. Sau khi commit, mở GitHub → Actions → `Build Orbic Recorder v4.0 APK` → Run workflow (nhánh `main`).
6. Tải artifact `orbic-recorder-v4-apk`, giải nén để có file `app-debug.apk`.

### Cấu trúc đúng tại GỐC repository

```text
.github/
  workflows/
    build.yml
app/
  build.gradle
  src/
    main/
      AndroidManifest.xml
      java/
        com/
          example/
            orbicrecorder/
              MainActivity.java
              RecorderService.java
build.gradle
settings.gradle
gradle.properties
README.md
```

**KHÔNG** được xuất hiện `MainActivity.java`, `RecorderService.java`, `build.yml` ở thư mục gốc. **CHỈ** có duy nhất một `build.gradle` ở gốc và một `app/build.gradle` ở trong `app/`.

Workflow có bước kiểm tra hai file Java và phiên bản 4.0. Nếu sai cấu trúc, nó báo lỗi ngay thay vì xuất APK cũ.

## B. Về cài APK

- Đây là bản **debug** do GitHub Actions build, chưa thiết lập khóa ký cố định để cập nhật lâu dài.
- Mỗi lần build debug trên runner mới có thể dùng khóa ký khác, khiến Android báo không thể cài đè. **Đừng gỡ APK cũ nếu chưa sao lưu dữ liệu.**
- Muốn quản trị viên chỉ duyệt cài một lần và các bản về sau cài đè, cần thiết lập một **khóa ký cố định**, kiểm soát versionCode và quy trình phê duyệt/cài đặt phù hợp. Không thể cập nhật mã Java chỉ bằng OTA web.

## C. Cấu hình bản đầu

| Mục | Giá trị |
|---|---|
| Ngưỡng phát hiện tiếng | -38 dBFS |
| Xác nhận có tiếng | 180 ms |
| Giữ trước | 1.000 ms |
| Giữ sau | 1.000 ms |
| Cảnh báo không có tín hiệu | 20 giây |
| Theme | Theo hệ thống |
| Thư mục lưu | Music/OrbicRecorder |

Lưu ý: cảnh báo im lặng chỉ gợi ý kiểm tra, không khẳng định micro bị mất. Chọn thư mục tùy ý sử dụng trình chọn thư mục SAF của Android và phải là thư mục Android cho phép ghi.

## D. Kiểm tra sau cài đặt

1. Mở app, xác nhận thấy **ORBIC RECORDER v4.0**, cột sóng âm và nút PAUSE.
2. Phát lời nói từ ngõ 3.5 mm, nhấn BẮT ĐẦU, đọc 5 giây, nghỉ 5 giây, đọc 5 giây.
3. Nhấn PAUSE, phát lời nói (phần này bị bỏ), nhấn TIẾP TỤC, đọc thêm.
4. Nhấn DỪNG VÀ LƯU. Mở Music/OrbicRecorder hoặc thư mục đã chọn và nghe WAV.
5. Đổi theme, giữ trước/sau, và thử lại.

## E. Build local (nếu có Android SDK và Gradle)

- JDK 17, Gradle 8.10.2, Android SDK 35, Android Gradle Plugin 8.7.3.
- Chạy: `gradle --no-daemon :app:assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Bản source do ChatGPT tạo và kiểm tra cấu trúc; **chưa biên dịch APK hoặc chạy trên Orbic Q10 trong môi trường tạo gói**.
