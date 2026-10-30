# ORBIC RECORDER v5.0 — Hybrid Web OTA (dựa trên bản v4.0 đã hoạt động)

**Thử nghiệm — chưa biên dịch hoặc chạy APK trên Orbic Q10.** Bộ ghi âm `RecorderService.java` được sao chép từ bản v4.0, chỉ thay tên thông báo v5.0. Mã giao diện được chuyển từ Java sang WebView, để cập nhật **HTML/CSS/JS** bằng OTA có chữ ký số. App chạy OFFLINE sau khi cài (mạng chỉ cần khi kiểm tra/tải OTA).

## 1. Giữ nguyên chức năng v4.0

- Ghi âm WAV mono 16 kHz, lấy nguồn audio Android đang định tuyến (ví dụ 3.5mm nếu thiết bị hỗ trợ).
- Lọc khoảng im lặng theo ngưỡng dBFS, ghép các đoạn được giữ thành một WAV.
- Pre-buffer (giữ trước) mặc định **1000 ms**, post-buffer (giữ sau) mặc định **1000 ms**.
- Nút **Bắt đầu**, **Pause/Tiếp tục**, **Dừng và lưu**.
- Sóng âm đơn giản, hai đồng hồ thời lượng, số đoạn, cảnh báo tín hiệu.
- Theme sáng/tối/theo hệ thống, lưu mặc định trong `Music/OrbicRecorder` hoặc chọn thư mục bằng Android SAF.
- Ghi âm nền qua Foreground Service, điều khiển từ thông báo, nghe file gần nhất.
- KHÔNG có chức năng Google Dịch hoặc PiP.

## 2. OTA làm được gì?

**Có thể OTA không cài APK:** thay đổi `index.html`, `app.css`, `app.js` — bố cục, màu, font, nội dung và hành vi giao diện; thêm các điều khiển web gọi những phương thức native **đã có sẵn**. Có thể thay đổi các *giá trị cấu hình* hiện có qua Settings.

**Không thể OTA:** thay đổi `RecorderService.java`, `MainActivity.java`, `OtaManager.java`, quyền Android, công nghệ thu âm, tần số/codec chưa được native hỗ trợ, background service, hay các plugin native. Chúng vẫn cần APK mới và quy trình cài đặt của Android. Không dùng OTA để vượt sự kiểm duyệt của quản trị viên.

**Bảo mật:** gói OTA chỉ gồm `index.html`, `app.css`, `app.js`. APK có RSA public key cố định; workflow ký phiên bản + URL + SHA-256 + nativeApi bằng RSA private key. App chỉ tải URL trên repo này qua HTTPS, kiểm tra chữ ký và hash; chặn zip traversal và external navigation trong WebView; có thể rollback bằng nút hoặc phím Back. Không nạp DEX/JAR/SO.

## 3. Cấu trúc upload lên GitHub (GIỮ ĐÚNG ĐƯỜNG DẪN)

```
.github/workflows/build.yml
.github/workflows/publish-ota.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/orbicrecorder/MainActivity.java
app/src/main/java/com/example/orbicrecorder/RecorderService.java
app/src/main/java/com/example/orbicrecorder/OtaManager.java
app/src/main/assets/ui/index.html
app/src/main/assets/ui/app.css
app/src/main/assets/ui/app.js
scripts/publish_ota.py
ota/README.txt
build.gradle
settings.gradle
gradle.properties
README.md
```

- **Không tải nguyên ZIP lên GitHub**. Giải nén, upload **nội dung bên trong** vào thư mục GỐC repository `https://github.com/gptlohoi-del/orbic-recorder`.
- Nếu chuyển sang repo khác, bắt buộc sửa `MANIFEST_URL`, `BUNDLE_PREFIX` trong `OtaManager.java` **và** `OWNER/REPO` trong `scripts/publish_ota.py` rồi build APK nền tảng mới; OTA phụ thuộc địa chỉ đã ghim.
- Không để `MainActivity.java`, `RecorderService.java`, `build.yml` rơi vào thư mục gốc!
- Xóa file cũ và file trùng trước khi upload; nhất là tránh build nhầm giao diện v4.0.

## 4. Lần đầu: build và cài APK nền tảng

1. Commit toàn bộ mã mới vào nhánh `main` trên GitHub.
2. **Actions** → **Build Orbic Recorder v5.0 Hybrid OTA APK** → **Run workflow**.
3. Tải artifact `orbic-recorder-v5-hybrid-ota-apk`, giải nén và cài `app-debug.apk`.
4. Mở app. Phải thấy `ORBIC RECORDER v5.0` và khu vực `CẬP NHẬT OTA`.
5. Test ghi WAV, Pause, giữ trước/sau 1 giây, folder, theme, offline trước khi đưa cho quản trị viên duyệt.

**Chữ ký APK:** bản GitHub Actions hiện tại tạo APK **debug**. Runner mới có thể tạo khóa debug khác: muốn cài đè APK native về sau phải dùng cùng khóa ký. Nên thiết lập **release signing cố định** trước khi quản trị viên duyệt (xem mục 8). Gỡ APK cũ có thể xóa cấu hình và file tạm; hãy sao lưu dữ liệu trước.

## 5. Thiết lập khóa OTA (CHỈ MỘT LẦN)

Bên cạnh file ZIP mã nguồn, ChatGPT cung cấp file `orbic-recorder-ota-signing-secret.txt` **riêng**. Đây là private key được biểu diễn bằng base64 (base64 không phải mã hóa bảo mật). **KHÔNG BAO GIỜ upload file này vào repository, kể cả repo private.** Public key tương ứng đã ghim trong `OtaManager.java` thuộc APK.

1. Mở file secret TXT riêng trên máy tính, copy toàn bộ chuỗi base64 (một dòng).
2. GitHub → repository `orbic-recorder` → **Settings → Secrets and variables → Actions → New repository secret**.
3. Name: `OTA_SIGNING_KEY_B64`.
4. Secret: dán toàn bộ nội dung file TXT, nhấn **Add secret**.
5. Giữ bản sao private key ở nơi an toàn. Mất private key = không thể phát hành OTA được APK này chấp nhận; sẽ phải cấp APK mới ghim khóa khác.
6. Hãy xin quản trị viên đồng ý cơ chế OTA và repo phân phối khi duyệt APK.

Đây là khóa **mẫu riêng cho dự án thử nghiệm** được tạo trong lần dựng mã nguồn. Với triển khai có chính sách bảo mật nghiêm ngặt, hãy tự tạo khóa trên máy tin cậy và thay public key trong APK **trước khi phê duyệt**.

## 6. Những lần sau: chỉ cập nhật OTA, không cài APK

Ví dụ muốn thay đổi màu/nút chữ:

1. Mở 3 file web ở `app/src/main/assets/ui/` trên GitHub và sửa HTML/CSS/JS, commit vào `main`.
2. Vào **Actions → Publish Orbic Recorder OTA → Run workflow**.
3. Nhập version `1.0.1` (lần sau `1.0.2`, rồi `1.0.3`...). **Mỗi lần bắt buộc tăng version**, không dùng lại.
4. Workflow tạo ZIP với ba file web, SHA-256 và chữ ký RSA, lưu vào `ota/bundles/1.0.1.zip`, cập nhật `ota/latest.json`, rồi commit lên nhánh main.
5. Mở Orbic Recorder trên điện thoại khi có Internet → **KIỂM TRA → CẬP NHẬT OTA**. App chỉ cho tải/kích hoạt khi đã dừng ghi. Không phải cài APK.
6. Nếu không thích giao diện mới, chọn **Quay lại giao diện trước**. Nếu UI lỗi không bấm được, nhấn phím **Back** của Android để mở hộp thoại rollback native.

Ứng dụng tự kiểm tra bản mới mỗi khi mở, nhưng chỉ tải và kích hoạt nếu bạn chọn. Lúc offline, sử dụng giao diện đã cài và ghi âm bình thường. Thời gian cập nhật URL qua `raw.githubusercontent.com` có thể bị cache ngắn; nếu mới phát hành và chưa thấy, thử kiểm tra lại.

**Nếu workflow phát hành báo `Permission denied` khi push:** kiểm tra GitHub repo → Settings → Actions → General → Workflow permissions và cho phép `Read and write permissions` nếu tổ chức cho phép. Không xóa secret OTA.

## 7. Tại sao không cho phép toàn bộ native OTA?

Cập nhật giao diện tải và chạy web JS là một cách triển khai khác với cài APK mới; nhưng không thay được mã Java hay quyền Android. Chính sách Android/Google Play và chính sách thiết bị quản trị phải được tôn trọng. Giao diện web sử dụng cầu nối native hạn chế lệnh (bắt đầu, dừng, pause, chọn thư mục, sửa các giá trị trong phạm vi cho phép). Không tải hoặc chạy bytecode native qua OTA.

## 8. Gợi ý cho trước lần quản trị viên phê duyệt

- **Ký APK release bằng khóa cố định** và lưu keystore cẩn thận; hiện workflow chỉ build debug để thử nhanh.
- Thử vài giờ ghi với nguồn 3.5 mm, gián đoạn Internet, màn hình tắt, mức pin yếu.
- Bổ sung tùy chọn ghi file gốc không lọc song song (tránh mất lời nói nhỏ) nếu cần; đây là thay đổi native nên nên cân nhắc trước lần duyệt chính thức.
- Ghi phiên dài trên 4 GB WAV cần cơ chế tự chia file ở native; bản v4 gốc báo lỗi khi tới giới hạn.
- Phục hồi WAV tạm sau mất nguồn/force-stop là một cải tiến native đáng cân nhắc trong bản cài đầu tiên.

## 9. Giới hạn kiểm thử

Gói này được kiểm tra cấu trúc, cú pháp JavaScript, script đóng gói, chữ ký RSA/SHA-256 và bố trí file. Không có Android SDK + Gradle mạng trong môi trường dựng ZIP, vì vậy **chưa biên dịch APK thực tế và chưa chạy thử trên Orbic Q10**. Nếu GitHub Actions báo lỗi, hãy gửi lỗi từ lần chạy workflow mới (không re-run commit cũ).
