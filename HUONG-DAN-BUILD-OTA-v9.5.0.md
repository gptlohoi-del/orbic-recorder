# HƯỚNG DẪN ORBIC RECORDER v9.5.0 — FULL SOURCE / APK / OTA

## 1. Bạn nhận được những gì?

ZIP này chứa **100% project Android và các workflow build**, chưa bao gồm APK đã ký. `applicationId` tiếp tục là `com.example.orbicrecorder` (không đổi tên gói), Java 17, Android tối thiểu API 28. `versionName`: `9.5.0` (trên GitHub có thêm số lần chạy); `versionCode`: `150000 + GITHUB_RUN_NUMBER` (build local 150000). Kiểm tra versionCode trên máy trước khi cài đè nếu bạn đã có bản build cao hơn bất thường.

## 2. Build và cài APK

**Cách A — Nâng cấp giữ dữ liệu (khuyến nghị nếu đã có bản cũ):**

1. **Sao lưu WAV quan trọng và file keystore ký APK đang dùng.** Không gỡ ứng dụng. Trên Android 9, nếu file nằm trong `Android/data/...` thì có thể mất khi uninstall; chuyển WAV ra thư mục độc lập bằng trình quản lý file hoặc chọn thư mục lưu (SAF).
2. Giải nén ZIP, chép **nội dung** `orbic-recorder-main/` lên nhánh `main` của `gptlohoi-del/orbic-recorder`. Giữ nguyên keystore ở **ngoài repo** và không thay `ota/latest.json` hay `ota/bundles/8.0.*.zip`.
3. Trong GitHub → **Settings → Secrets and variables → Actions** bảo đảm tồn tại `ANDROID_RELEASE_KEYSTORE_B64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD` **cho đúng keystore cũ** (xem `RELEASE_SIGNING_GUIDE.md`).
4. GitHub → **Actions → Optional signed Release APK (cần Release Key) → Run workflow**. Khi hoàn thành, tải artifact `Recorder-9.5.0.<run_number>`, giải nén và cài APK `Recorder-9.5.0.<run_number>.apk` lên máy.
5. Bản Release phải có **cùng chứng chỉ và package name** như bản đang cài, và versionCode phải lớn hơn. Nếu Android báo khác chữ ký, **không gỡ app ngay**: kiểm tra lại keystore / sao lưu đầy đủ trước khi quyết định cài mới.

**Cách B — Cài thử trên thiết bị chưa có app hoặc đang dùng cùng debug key:**

1. GitHub → Actions → **Tạo APK Orbic Recorder (không cần Release Key)** → Run workflow.
2. Tải artifact `ORBIC-RECORDER-v9.5.0-debug`, giải nén `ORBIC-RECORDER-v9.5.0-debug.apk`, cài lên Android 9 trở lên.
3. Mỗi runner debug có thể dùng một debug certificate khác: **không giả định** APK Debug từ GitHub có thể cài đè bản Release/Debug đã cài trước đó.

**Build tại máy:** yêu cầu JDK 17, Android SDK 35 + build-tools 35.0.0, Gradle 8.10.2, Internet để tải dependencies. Lệnh `gradle --no-daemon :app:assembleDebug`. Project **không kèm Gradle wrapper**, workflow CI cài Gradle độc lập, tránh wrapper thiếu hoặc bị thay.

## 3. Kiểm tra tính năng sau cài đặt

- Xoay điện thoại dọc và ngang; chạm `⤾ XOAY 180°` → toàn bộ **giao diện app** (kể cả nút bấm, hộp thoại) phải quay ngược đúng 180°, không phải 90°; chạm tiếp để trả về. Đóng/mở lại để kiểm tra ghi nhớ.
- Chọn 📂 cho **BÀI 1/2/3** và phát từng bài; khi đổi bài đang phát có hỏi xác nhận và giảm âm mượt. Đóng/mở lại kiểm tra ba file đã chọn vẫn được lưu.
- START ghi âm, thử **tắt ngưỡng** trong Settings → GHI ÂM, nói nhỏ và im lặng; file WAV chính vẫn có PCM khoảng lặng, ghi đủ thời lượng (trừ khi nhạc đang phát/PAUSE).
- START, đợi **ít nhất một đoạn WAV đã xuất**, sau đó **DỪNG & KHÔNG LƯU**: chỉ kéo từ trái qua phải đến hết thanh mới hủy. Chạm/huỷ/nhả giữa chừng KHÔNG xóa. Kiểm tra file thuộc phiên vừa hủy biến mất; file của phiên trước vẫn còn. Kiểm thử cả khi đã PAUSE và khi đang phát nhạc (mic được giữ tắt).
- START → `DỪNG & LƯU`, kiểm tra WAV hợp lệ và xem có được xuất đúng thư mục SAF/MediaStore không. Thử các nút trên notification của Android khi màn hình app không mở.
- Trên Android 9, chọn thư mục WAV bên ngoài ứng dụng trong Settings nếu cần giữ dữ liệu qua việc gỡ/cài lại app.
- Thử tắt nhạc rồi dùng mic, giữ PAUSE ghi âm thủ công, thử file nhạc bị xóa/không còn quyền, và thử thiếu dung lượng lưu trữ.

**Không có thiết bị thử nghiệm trong gói này:** không thể đảm bảo thiết bị cụ thể cho phép xóa file qua mọi nhà cung cấp SAF/MediaStore. Nếu nhà cung cấp từ chối, app ghi lỗi và giữ dấu `.discard` để thử dọn khi khởi động sau. Xác nhận file thực sự biến mất trước khi coi việc hủy hoàn tất.

## 4. OTA — phát hành giao diện v8.0.4 hoặc mới hơn

- **KHÔNG cần OTA để thấy giao diện v9.5.0 lần đầu**: APK mới chứa giao diện gốc `8.0.3`.
- Gói OTA chỉ gồm `index.html`, `app.css`, `app.js` từ `app/src/main/assets/ui/`. Muốn chỉnh màu/chữ/vị trí nút/hiệu ứng vuốt, sửa 3 file và có thể OTA lên APK đã hỗ trợ các lệnh Android tương ứng.
- Vào repo GitHub → `Settings → Secrets and variables → Actions`: phải có `OTA_SIGNING_KEY_B64`, khóa RSA **trùng public key đóng trong APK**. Khóa này **khác với keystore ký APK**. Không tạo khóa mới tùy tiện: OTA cũ và APK đã cài sẽ không chấp nhận chữ ký lạ.
- Sau khi commit 3 file giao diện và chạy test, vào **Actions → Publish Orbic Recorder OTA → Run workflow**. Workflow tự tạo gói mới (tối thiểu `8.0.4`), ký `ota/latest.json`, tải lên `ota/bundles/8.0.4.zip` rồi commit. Không tự sửa chữ ký manifest bằng tay.
- Trên thiết bị: **⚙ CÀI ĐẶT → ↻ CẬP NHẬT OTA → KIỂM TRA → CẬP NHẬT**, chỉ khi đã dừng ghi và nhạc. Trong Settings có thể QUAY LẠI OTA trước nếu có.
- OTA **không thể** thay `RecorderService.java`, các câu lệnh Android Bridge, logic xóa WAV hay cấu hình `AndroidManifest.xml`. Cần cài APK mới một lần để có tính năng native. Các APK v9.4.2 trong repo trước đã có ba chức năng; APK cũ hơn có thể chỉ dùng được giao diện xoay CSS.
- Phần OTA vẫn giữ `nativeApi=8`; UI chủ động khóa BÀI 3/hủy nếu không phát hiện APK hỗ trợ. Bộ kiểm tra chữ ký RSA, SHA-256, kích thước ZIP và nguồn URL vẫn giữ nguyên.

## 5. Các thay đổi kỹ thuật chính v9.5.0

| Hạng mục | Vấn đề quan sát trong repo cũ | Cách đã xử lý |
|---|---|---|
| OTA sau cài đè APK | Đã tải OTA 8.0.2 có thể che giao diện tích hợp của APK mới 8.0.0 | Nâng giao diện nhúng lên 8.0.3, ưu tiên bản nhúng nếu OTA đã lưu cũ hơn; ngăn rollback về giao diện cũ hơn bản nhúng |
| Nút native trên APK cũ | OTA có thể hiện nút dù lệnh native chưa tồn tại | Thêm `supportsThirdTrack` và `supportsSwipeDiscard` vào snapshot Android; OTA giao diện khóa nút khi APK không chứng minh được hỗ trợ (nhận diện tương thích v9.4.2) |
| Vuốt hủy | Mới kiểm tra quãng đường nên vẫn có khả năng va chạm thao tác quá nhanh/đúp | Yêu cầu vuốt đủ hơn 92% và tối thiểu 180 ms; tránh gửi trùng lệnh; giữ dấu hủy bền vững và xóa theo session |
| Âm thanh | Việc chỉ nghe nhạc vẫn yêu cầu quyền micro | Chỉ xin `RECORD_AUDIO` khi START ghi; nhạc có thể phát trước |
| Khôi phục khi giao diện lỗi | Không có điều khiển nhạc/ghi qua notification dù đã tạo PendingIntent | Thêm PAUSE/TIẾP GHI và DỪNG & LƯU vào notification; không có nút hủy bằng một lần chạm |
| Đóng gói | Các hướng dẫn README cũ trộn nhiều đời APK, có link lịch sử đã chuyển | README mới mô tả đúng v9.5.0 và giữ bản lịch sử trong `docs/` |

## 6. Giới hạn xác nhận

Các unit/source tests và JavaScript tests có thể chạy không cần SDK. **Không tuyên bố đã biên dịch APK hoặc cài/đo trên thiết bị thật** nếu workflow GitHub Actions chưa chạy. Khóa riêng của APK Release và của OTA không nằm trong repo ZIP. Không có APK phát hành/signed OTA mới được upload lên GitHub từ bản mã nguồn này.
