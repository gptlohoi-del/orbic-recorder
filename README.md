# Bổ sung quy trình APK release cố định

Xem [`RELEASE_SIGNING_GUIDE.md`](RELEASE_SIGNING_GUIDE.md). Giữ nguyên OTA và mã ghi âm.

# Orbic Recorder v5.1 – Safe Recording + Web OTA

Dựa trên Orbic Recorder **v5.0 Hybrid OTA** đã chạy thành công trên Orbic Q10 (Android 12). Giữ nguyên package `com.example.orbicrecorder`, cấu trúc OTA ký RSA, đường dẫn GitHub và API web v1; mở rộng **phần native** với ba cải tiến:

1. **WAV gốc dự phòng (mặc định BẬT)**: Thu âm gốc 16 kHz mono song song với WAV lọc khoảng lặng. Nếu bộ lọc bỏ sót lời nói nhỏ, file `_raw.wav` vẫn giữ tín hiệu micro đã nhận. Lưu ý: khi nhấn **PAUSE**, cả hai đều ngừng ghi. Nếu Android không cấp âm thanh cho app, bản gốc cũng không có tín hiệu đó.
2. **Tự chia file**: mặc định 15 phút âm thanh đang thu (không tính Pause). Settings cho 1, 5, 10, 15, 30 hoặc 60 phút. Mỗi phần tạo WAV `_voice.wav` và `_raw.wav` (nếu bật dự phòng). Các file được lưu theo từng phần, không ghép thành một file duy nhất trong phiên dài.
3. **Cứu WAV sau sự cố**: file WAV đang ghi nằm trong `RecordingsTemp`; header được đồng bộ ra bộ nhớ mỗi 5 giây. Sau khi app bị tắt đột ngột/mất pin, lần mở lại tiếp theo sẽ quét file tạm, sửa kích thước RIFF theo số byte thực có, xuất lại những file hợp lệ. **Không bảo đảm lấy lại byte âm thanh chưa được hệ điều hành ghi xuống bộ nhớ**; không cứu được file đã bị xóa hoặc thiết bị lưu trữ hỏng.

**Lưu ý:** Đây là phiên bản nguồn mới **chưa được biên dịch APK hoặc kiểm thử trên điện thoại thực tế trong môi trường tạo file này**. Hãy build bằng GitHub Actions và kiểm tra ngắn trước khi dùng cho bài giảng quan trọng.

## Thư mục lưu

Khi dùng vị trí mặc định, file nằm ở:

- `Music/OrbicRecorder/Goc/` – file `*_raw.wav` không lọc.
- `Music/OrbicRecorder/DaLoc/` – file `*_voice.wav` đã lọc.

Khi chọn thư mục bằng SAF, cả hai loại file được xuất vào thư mục đã chọn, phân biệt qua `_raw.wav` và `_voice.wav`. Nếu không thể ghi vào SAF, ứng dụng thử lưu dự phòng vào Music và hiển thị cảnh báo. File tạm **chỉ xóa sau khi xuất thành công**. File WAV lọc rỗng sẽ không được xuất, nhưng bản gốc vẫn được xuất nếu bật sao lưu.

## Từ v5.0 sang v5.1: cập nhật mã nguồn và APK đúng cách

1. **Giữ lại** thư mục `ota/` với `latest.json`, các ZIP OTA đã phát hành; **giữ nguyên GitHub Actions secret `OTA_SIGNING_KEY_B64`**. Không thay RSA public key trong `OtaManager.java`.
2. Tải toàn bộ **nội dung bên trong** ZIP mã nguồn v5.1 lên root repository `gptlohoi-del/orbic-recorder` đúng đường dẫn; **ghi đè** các file trùng. Đặc biệt phải có `app/src/main/java/com/example/orbicrecorder/RecordingArchive.java` và `RecorderService.java` ở đúng đường dẫn. Không đặt Java hoặc workflow `.yml` ở thư mục gốc.
3. Kiểm tra `app/build.gradle`: `versionCode 21`, `versionName '5.1-safe-recording-ota'`.
4. GitHub **Actions → Build Orbic Recorder v5.1 Safe Recording OTA APK → Run workflow** ở nhánh `main` → tải artifact `orbic-recorder-v5.1-safe-ota-apk`.
5. Vì sửa **native Java**, v5.1 **bắt buộc phải cài APK mới một lần**; OTA không thay được mã Java. Nếu APK mới bị Android báo khác chữ ký, KHÔNG vội gỡ bản cũ: sao lưu bản ghi, thảo luận với quản trị viên và dùng khóa ký APK cố định từ lần cài chính thức. Lưu ý các APK debug build ở runner GitHub khác nhau có thể không dùng cùng khóa ký.
6. Sau khi cài v5.1, mở app và thử ghi vài phút, bật/tắt bản gốc, chia file 1 phút để thử, Pause/Tiếp tục, ghi không có tiếng nói, rút nguồn và khởi động lại để thử khôi phục có kiểm soát. Không dùng bài giảng quan trọng cho thử nghiệm mất nguồn.

## Giao diện v5.1 và bản OTA 1.0.1 đã cài

**Điểm quan trọng:** Điện thoại của bạn hiện đang dùng OTA web **1.0.1**; sau khi cài APK native v5.1, hệ thống sẽ tiếp tục hiển thị **giao diện OTA 1.0.1**, không tự thay bằng giao diện nhúng mới. Đó là thiết kế OTA, không phải lỗi build.

Để thấy Settings mới (checkbox **Lưu WAV gốc**, **Chia file mỗi...**):

1. Commit ba file web mới `app/src/main/assets/ui/index.html`, `app.css`, `app.js` trong bộ nguồn này.
2. GitHub Actions → **Publish Orbic Recorder OTA** → nhập **1.0.2** (hoặc phiên bản lớn hơn `ota/latest.json` đang có).
3. Trong app, khi **không ghi âm**, nhấn KIỂM TRA → CẬP NHẬT OTA.
4. Sau khi OTA hiển thị Settings mới, native v5.1 hỗ trợ `backupRaw` (boolean) và `splitMinutes` (phút). Các phiên trước đó không có hai trường vẫn tương thích: mặc định sao lưu BẬT và chia mỗi 15 phút.

**Không thay `NATIVE_API=1`**, vì giao diện web mới vẫn tương thích bridge cũ trong việc ghi, Pause, Dừng. Native v5.0 không hỗ trợ hai lựa chọn mới; vì vậy chỉ triển khai OTA giao diện mới **sau khi điện thoại đã cài APK v5.1** nếu cần điều khiển hai lựa chọn này.

## Cơ chế phục hồi và giới hạn

- Quá trình ghi sử dụng `AudioRecord` duy nhất → WAV gốc và WAV đã lọc. Không chiếm micro lần thứ hai.
- Mỗi file tạm có 44 byte header WAV; cập nhật header + `FileDescriptor.sync()` sau mỗi 5 giây, hoàn thiện khi tách phần/dừng. Mỗi phần đã đóng được xuất trên hàng đợi I/O riêng, không chặn luồng thu âm.
- Khi mở app sau khi ghi bị dừng bất thường, trình khôi phục được chạy trước khi cho phép ghi tiếp. WAV tạm được sửa theo kích cỡ PCM thực và xuất; file tạm được giữ lại nếu xuất thất bại.
- Nếu thiết bị hết bộ nhớ trong lúc ghi, app vẫn có thể thất bại: cần kiểm tra dung lượng thường xuyên. Không có thuật toán khôi phục nào bảo đảm an toàn tuyệt đối khi hệ điều hành, phần cứng hoặc bộ nhớ hỏng.
- Nếu mất nguồn trong lúc xuất WAV sang thư mục SAF, có thể xuất hiện file dở dang hoặc bản trùng. Kiểm tra các file trước khi xóa.
- Nguồn 16 kHz mono PCM16 tạo khoảng **1,92 MB/phút** cho WAV gốc (khoảng **28,8 MB/15 phút**), chưa kể file lọc. Hãy dành dung lượng tương ứng.
- Không tự giữ âm thanh trong khoảng Pause. Mặc định pre/post-roll 1 giây vẫn hoạt động trên file đã lọc.

## Tệp chính

```
.github/workflows/build.yml
.github/workflows/publish-ota.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/orbicrecorder/MainActivity.java
app/src/main/java/com/example/orbicrecorder/RecorderService.java
app/src/main/java/com/example/orbicrecorder/RecordingArchive.java
app/src/main/java/com/example/orbicrecorder/OtaManager.java
app/src/main/assets/ui/index.html
app/src/main/assets/ui/app.css
app/src/main/assets/ui/app.js
scripts/publish_ota.py
build.gradle
settings.gradle
gradle.properties
```

Không đưa private key/keystore vào repository. APK native cần được quản trị viên chấp thuận, kể cả khi OTA web hoạt động bình thường.
