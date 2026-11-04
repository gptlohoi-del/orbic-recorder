# ORBIC RECORDER v9.5.0 — Android 9+ · 3 bài nhạc · Xoay 180° · Ghi/WAV an toàn

**Đây là toàn bộ mã nguồn Android, không phải APK cài trực tiếp.** Build APK bằng GitHub Actions (đã cấu hình trong `.github/workflows/`). Bản v9.5.0 dựa trên repo v9.4.2 được cung cấp, giữ nguyên `applicationId`, chứng chỉ OTA công khai và các gói OTA đã xuất bản.

## Chức năng chính

- **Xoay đúng 180°:** nút ở góc phải tiêu đề `ORBIC RECORDER`; xoay toàn bộ Android WebView 180° cùng vùng cảm ứng, không xoay 90°. Ghi nhớ trạng thái khi mở lại. Không xoay thanh hệ thống Android.
- **3 bài nhạc chọn nhanh:** nút `▶ BÀI 1`, `▶ BÀI 2`, `▶ BÀI 3` và từng nút 📂. Nhấn 📂 chọn bài mặc định, được lưu bằng quyền SAF. `+ BÀI KHÁC` phát file bất kỳ. Giữ fade giữa các bài; PAUSE và STOP nhạc không làm mất bản ghi.
- **DỪNG & KHÔNG LƯU:** nằm dưới `START`, `PAUSE`, `DỪNG & LƯU`; mở hộp thoại, vuốt thực sự trái → phải gần hết thanh để xác nhận hủy **toàn bộ các file thuộc phiên ghi hiện tại**, bao gồm các đoạn đã xuất. Hủy không ảnh hưởng bản ghi các phiên cũ. Lỗi xóa được thông báo và đánh dấu thử lại sau sự cố.
- **Bật/tắt ngưỡng phát hiện** trong Settings → GHI ÂM: tắt thì lưu cả khung PCM yên lặng, cập nhật khoảng 100 ms khi đang thu. Vẫn không mở mic trong lúc phát nhạc; `PAUSE` vẫn tạm dừng thu. Giữ tùy chọn backup RAW, chia WAV định kỳ, limiter, AGC và 4 kiểu sóng âm.
- **Điều khiển Android notification:** `PAUSE/TIẾP GHI` và `DỪNG & LƯU` (không bao giờ đặt lệnh hủy file trên notification).
- **Phát nhạc không đòi cấp quyền mic.** Chỉ `START` ghi âm mới xin `RECORD_AUDIO`.
- **Cơ chế OTA ký RSA:** chỉ cập nhật HTML/CSS/JS. APK cũ chưa hỗ trợ chức năng native sẽ khóa các nút tương ứng thay vì báo thành công sai. APK có giao diện nhúng `8.0.6`, mới hơn OTA `8.0.5`, để các nút nghe lại bản ghi không bị OTA cũ che mất; OTA tiếp theo tối thiểu là `8.0.7`.

## Cài lại / cập nhật APK **không mất dữ liệu**

1. Đọc **[HƯỚNG DẪN BUILD, CÀI VÀ OTA v9.5.0](HUONG-DAN-BUILD-OTA-v9.5.0.md)** trước khi thao tác.
2. Upload **nội dung bên trong** thư mục `orbic-recorder-main/` của ZIP lên repository GitHub `gptlohoi-del/orbic-recorder`, giữ nguyên `ota/latest.json`, `ota/bundles/*`, `scripts/publish_ota.py` và `.github/workflows/*`.
3. Vào GitHub **Actions → Optional signed Release APK (cần Release Key) → Run workflow**; dùng **chính keystore đã ký APK đang cài** để có thể cài đè không mất dữ liệu. Tải artifact `Recorder-9.5.0.<run_number>` và giải nén APK.
4. Nếu chỉ cần cài thử mới, dùng **Actions → Tạo APK Orbic Recorder (không cần Release Key)**; APK Debug thường **không thể cài đè APK Release** do khác chứng chỉ.
5. **Tuyệt đối không gỡ ứng dụng đang cài trước khi sao lưu WAV**; trên Android 9 một số bản ghi có thể lưu trong bộ nhớ riêng sẽ bị xóa khi uninstall. `versionCode` được tăng lên nhóm `150000+` để hỗ trợ nâng cấp từ v9.4.2 thông thường.
6. Sau khi đã cài APK mới, muốn phát hành thay đổi **chỉ giao diện**, chạy workflow **Publish Orbic Recorder OTA** sau khi thiết lập đúng `OTA_SIGNING_KEY_B64`. Không thay các bản ZIP OTA cũ và không bỏ bước xác minh chữ ký.

## Giới hạn OTA của 3 yêu cầu

| Yêu cầu | OTA lên APK cũ | Cài APK v9.5.0 |
|---|---|---|
| Nút xoay giao diện 180° | **Có thể**: JS/CSS fallback cho APK cũ; không xoay system bars | **Có**: xoay WebView native chuẩn, tọa độ chạm đi theo |
| Bài nhạc số 3 | **Chỉ nếu APK cũ đã hỗ trợ** native slot 3; OTA đơn thuần không bổ sung Android bridge | **Có** |
| DỪNG & KHÔNG LƯU bằng vuốt | **Chỉ nếu APK cũ đã có** `STOP_DISCARD` + engine xóa phiên. OTA không thể thêm việc xóa file Android | **Có** |

APK v9.4.2 trong ZIP trước đó đã có mã native cho 3 tính năng; các APK **cũ hơn** có thể không có. Giao diện mới tự phát hiện khả năng hỗ trợ; khi không chắc, ưu tiên vô hiệu hóa thao tác nguy hiểm.

Xem thêm [báo cáo rà soát và khuyến nghị](BAO-CAO-RASOAT-REPO-v9.5.0.md), [changelog](CHANGELOG.md).

## Cấu trúc repo

- `app/src/main/java/com/example/orbicrecorder/`: thu âm nền, phát nhạc, lưu WAV, Android WebView bridge, OTA có chữ ký.
- `app/src/main/assets/ui/`: `index.html`, `app.css`, `app.js` — ba file có thể cập nhật OTA.
- `.github/workflows/`: CI build APK Debug/Release và công bố OTA.
- `ota/latest.json`, `ota/bundles/`: **lịch sử OTA đã ký, không sửa**.
- `scripts/publish_ota.py`: đóng gói OTA, kiểm tra khóa và ký manifest.
- `tests/`: kiểm tra JS, xử lý tiếng, lưu trữ an toàn và hợp đồng tính năng.
- `README-HISTORY.md`: các hướng dẫn lịch sử để tra cứu (không phải quy trình v9.5.0).

## Kiểm thử

```bash
node --check app/src/main/assets/ui/app.js
for t in tests/test_*.py; do python3 "$t"; done
# Với Java 17, Android SDK và Gradle 8.10.2:
gradle --no-daemon :app:assembleDebug
```

CI build APK với `compileSdk 35`, `minSdk 28` (Android 9), `targetSdk 32`, Java 17. **Các test mã nguồn không thay thế thử nghiệm Android 9/10/13 trên thiết bị thật**, nhất là quyền SAF, xóa WAV và nhạc/micro luân phiên. Cần xem `HUONG-DAN-BUILD-OTA-v9.5.0.md` để kiểm thử trước khi phát hành chính thức.


## Thư viện nghe lại bản ghi WAV (cập nhật giao diện 8.0.6)

Hai nút nhỏ ở phía phải dòng **🎙 GHI THEO TIẾNG ĐỘNG**:

- **📁**: mở thư viện WAV ngay trong ứng dụng; file mới nhất luôn nằm đầu danh sách; mỗi file có nút nghe, ngày giờ, loại bản ghi (đã lọc/gốc) và dung lượng.
- **▶**: mở thư viện và tự động phát file WAV mới nhất vừa được lưu, không phải duyệt thủ công.

Trình nghe lại có PAUSE/TIẾP, STOP, TRƯỚC/SAU và tua thời gian. Dùng chung cơ chế nhường micro của trình phát nhạc hiện tại, không thay đổi cách thu hay xoá WAV. Danh sách được làm mới bằng **↻** và tự cập nhật khi app hoàn tất lưu file mới trong khi thư viện đang mở. File WAV được đọc từ thư mục SAF người dùng đã chọn và thư mục dự phòng OrbicRecorder trên Android 9–15 (tối đa 500 file gần nhất). 

**Cần build/cài APK mới:** tính năng đọc danh sách, phát file chọn và tua phụ thuộc Java native mới trong `RecordingLibrary.java` / `MainActivity.java` / `MusicPlayer.java`; chỉ đẩy `app.js/index.html/app.css` qua OTA lên APK cũ sẽ bị khoá hai nút. Xem thêm `README-THU-VIEN-GHI-AM.md`.
