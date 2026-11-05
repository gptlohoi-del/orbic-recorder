# Bản cập nhật v9.5.2 — điều khiển xoay màn hình (Android 9+)

- **Mặc định tắt xoay tự động theo điện thoại**. Trong Setting → GIAO DIỆN có công tắc **Tự động xoay màn hình theo điện thoại**, lưu vào Android SharedPreferences. Bật thì trở về chế độ `FULL_SENSOR`. Tắt thì khóa chiều dọc.
- Hai nút ngang tiêu đề **PHÁT NHẠC**: **XOAY 90°** màu cam (mỗi lần tăng 90°) nằm bên trái **XOAY 180°** màu xanh (tăng 180°). Bấm được khi cảm biến bị khóa. Xoay cả WebView và các hộp thoại, đồng thời đảo kích thước WebView ở góc 90°/270° để không cắt nội dung.
- Giữ tùy chọn xoay 180° cũ khi nâng cấp; bộ nhớ giao diện mới dùng `uiRotationDegrees` và `uiRotated180` để tương thích.
- Nâng native OTA API lên **9**, HTML tích hợp **8.0.9** để bản OTA cũ không vô tình đè giao diện mới. OTA về sau phải được phát hành và ký cho `nativeApi=9`. Không được tự sửa `ota/latest.json` nếu thiếu khóa ký.
- Tăng Android `versionCode` lên **170000+**, `versionName` 9.5.2. `minSdk 28` (Android 9).
- **Phải cài APK mới một lần** vì điều khiển xoay màn hình là mã Android native. Những OTA HTML/CSS/JS đã ký sau đó sẽ vẫn tự cập nhật theo công tắc OTA.

> Lưu ý: kiểm tra mã nguồn không thay thế biên dịch và thử APK trên điện thoại Android 9. Nếu cập nhật đè, APK mới phải dùng **đúng chứng chỉ ký** của APK đang cài. Hãy sao lưu WAV trước khi thao tác với ứng dụng.

---

# ORBIC RECORDER v9.5.2 — Android 9+ · 3 bài nhạc · Xoay 180° · Ghi/WAV an toàn

**Đây là toàn bộ mã nguồn Android, không phải APK cài trực tiếp.** Build APK bằng GitHub Actions (đã cấu hình trong `.github/workflows/`). Bản v9.5.2 tiếp tục repo v9.5.0 (nền v9.4.2) được cung cấp, giữ nguyên `applicationId`, chứng chỉ OTA công khai và các gói OTA đã xuất bản.

## Tự động OTA (tiếp tục từ bản 9.5.1, mặc định BẬT)

- Trong **⚙ Cài đặt → ↻ CẬP NHẬT OTA**, công tắc **TỰ ĐỘNG CẬP NHẬT OTA** mặc định BẬT; thay đổi được tự lưu trong Android (ngay cả khi cài nâng cấp APK).
- Khi **mở ứng dụng**, native tự kiểm tra manifest OTA trên GitHub. Nếu phiên bản mới hơn giao diện có sẵn trong APK, đúng `nativeApi`, có chữ ký RSA hợp lệ và SHA-256 trùng, ứng dụng **tự tải/cài/gọi tải lại giao diện**.
- **Không bắt đầu cài khi đang ghi, đang khởi động ghi, đang lưu WAV, khôi phục file hoặc phát nhạc**; ở trạng thái bận, ứng dụng chờ rảnh và thử lại khoảng 3 giây một lần **chỉ khi ứng dụng đang ở phía trước**. Tắt công tắc không cản trở nút **KIỂM TRA**/**CẬP NHẬT** thủ công.
- Sau thao tác **QUAY LẠI** bản trước, không tự áp dụng lại **chính phiên bản bị từ chối**. Vẫn có thể cài thủ công, hoặc tự lên phiên bản OTA kế tiếp.
- **Cài APK mới một lần** để có khả năng tự động OTA native; **OTA không thể thay Java/RecorderService hoặc tự nâng cấp APK**. Phát hành OTA sau này cần workflow **Publish Orbic Recorder OTA**, khóa ký `OTA_SIGNING_KEY_B64` phù hợp và manifest mới hơn 8.0.9 (nativeApi 9). Các file ghi âm WAV hiện có không bị xóa.

## Chức năng chính

- **Xoay thủ công 90°/180°:** hai nút ở góc phải hàng `♫ PHÁT NHẠC`, 90° màu cam bên trái 180° màu xanh. Mỗi nút xoay thêm 90° hoặc 180°; lưu góc qua lần mở ứng dụng. Khi xoay 90°/270°, WebView đổi kích thước để phù hợp vùng hiển thị và nhận chạm. Không xoay thanh hệ thống Android. **Tự xoay theo điện thoại mặc định TẮT**, có công tắc riêng trong Setting → GIAO DIỆN.
- **3 bài nhạc chọn nhanh:** nút `▶ BÀI 1`, `▶ BÀI 2`, `▶ BÀI 3` và từng nút 📂. Nhấn 📂 chọn bài mặc định, được lưu bằng quyền SAF. `+ BÀI KHÁC` phát file bất kỳ. Giữ fade giữa các bài; PAUSE và STOP nhạc không làm mất bản ghi.
- **DỪNG & KHÔNG LƯU:** nằm dưới `START`, `PAUSE`, `DỪNG & LƯU`; mở hộp thoại, vuốt thực sự trái → phải gần hết thanh để xác nhận hủy **toàn bộ các file thuộc phiên ghi hiện tại**, bao gồm các đoạn đã xuất. Hủy không ảnh hưởng bản ghi các phiên cũ. Lỗi xóa được thông báo và đánh dấu thử lại sau sự cố.
- **Bật/tắt ngưỡng phát hiện** trong Settings → GHI ÂM: tắt thì lưu cả khung PCM yên lặng, cập nhật khoảng 100 ms khi đang thu. Vẫn không mở mic trong lúc phát nhạc; `PAUSE` vẫn tạm dừng thu. Giữ tùy chọn backup RAW, chia WAV định kỳ, limiter, AGC và 4 kiểu sóng âm.
- **Điều khiển Android notification:** `PAUSE/TIẾP GHI` và `DỪNG & LƯU` (không bao giờ đặt lệnh hủy file trên notification).
- **Phát nhạc không đòi cấp quyền mic.** Chỉ `START` ghi âm mới xin `RECORD_AUDIO`.
- **Cơ chế OTA ký RSA:** chỉ cập nhật HTML/CSS/JS. APK cũ chưa hỗ trợ chức năng native sẽ khóa các nút tương ứng thay vì báo thành công sai. APK có giao diện nhúng `8.0.9`, mới hơn OTA lịch sử `8.0.5`; OTA tiếp theo tối thiểu là `8.0.10` với nativeApi 9.

Xem hướng dẫn chi tiết về hai nút xoay và khóa cảm biến: [HUONG-DAN-XOAY-MAN-HINH-v9.5.2.md](HUONG-DAN-XOAY-MAN-HINH-v9.5.2.md).

## Cài lại / cập nhật APK **không mất dữ liệu**

1. Đọc **[HƯỚNG DẪN TỰ ĐỘNG OTA / ANDROID 9](HUONG-DAN-TU-DONG-OTA-ANDROID9.md)** và tài liệu build/signing v9.5.0 (quy trình ký vẫn áp dụng).
2. Upload **nội dung bên trong** thư mục `orbic-recorder-main/` của ZIP lên repository GitHub `gptlohoi-del/orbic-recorder`, giữ nguyên `ota/latest.json`, `ota/bundles/*`, `scripts/publish_ota.py` và `.github/workflows/*`.
3. Vào GitHub **Actions → Optional signed Release APK (cần Release Key) → Run workflow**; dùng **chính keystore đã ký APK đang cài** để có thể cài đè không mất dữ liệu. Tải artifact `Recorder-9.5.2.<run_number>` và giải nén APK.
4. Nếu chỉ cần cài thử mới, dùng **Actions → Tạo APK Orbic Recorder (không cần Release Key)**; APK Debug thường **không thể cài đè APK Release** do khác chứng chỉ.
5. **Tuyệt đối không gỡ ứng dụng đang cài trước khi sao lưu WAV**; trên Android 9 một số bản ghi có thể lưu trong bộ nhớ riêng sẽ bị xóa khi uninstall. `versionCode` được tăng lên nhóm `170000+` để hỗ trợ nâng cấp từ v9.4.2 thông thường.
6. Sau khi đã cài APK mới, muốn phát hành thay đổi **chỉ giao diện**, chạy workflow **Publish Orbic Recorder OTA** sau khi thiết lập đúng `OTA_SIGNING_KEY_B64`. Không thay các bản ZIP OTA cũ và không bỏ bước xác minh chữ ký.

## Giới hạn OTA của 3 yêu cầu

| Yêu cầu | OTA lên APK cũ | Cài APK v9.5.2 |
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
- `README-HISTORY.md`: các hướng dẫn lịch sử để tra cứu (không phải quy trình v9.5.2).

## Kiểm thử

```bash
node --check app/src/main/assets/ui/app.js
for t in tests/test_*.py; do python3 "$t"; done
# Với Java 17, Android SDK và Gradle 8.10.2:
gradle --no-daemon :app:assembleDebug
```

CI build APK với `compileSdk 35`, `minSdk 28` (Android 9), `targetSdk 32`, Java 17. **Các test mã nguồn không thay thế thử nghiệm Android 9/10/13 trên thiết bị thật**, nhất là quyền SAF, xóa WAV và nhạc/micro luân phiên. Cần xem `HUONG-DAN-BUILD-OTA-v9.5.0.md` để kiểm thử trước khi phát hành chính thức.


## Thư viện nghe lại bản ghi WAV (cập nhật giao diện 8.0.7)

Hai nút nhỏ ở phía phải dòng **🎙 GHI THEO TIẾNG ĐỘNG**:

- **📁**: mở thư viện WAV ngay trong ứng dụng; file mới nhất luôn nằm đầu danh sách; mỗi file có nút nghe, ngày giờ, loại bản ghi (đã lọc/gốc) và dung lượng.
- **▶**: mở thư viện và tự động phát file WAV mới nhất vừa được lưu, không phải duyệt thủ công.

Trình nghe lại có PAUSE/TIẾP, STOP, TRƯỚC/SAU và tua thời gian. Dùng chung cơ chế nhường micro của trình phát nhạc hiện tại, không thay đổi cách thu hay xoá WAV. Danh sách được làm mới bằng **↻** và tự cập nhật khi app hoàn tất lưu file mới trong khi thư viện đang mở. File WAV được đọc từ thư mục SAF người dùng đã chọn và thư mục dự phòng OrbicRecorder trên Android 9–15 (tối đa 500 file gần nhất). 

**Cần build/cài APK mới:** tính năng đọc danh sách, phát file chọn và tua phụ thuộc Java native mới trong `RecordingLibrary.java` / `MainActivity.java` / `MusicPlayer.java`; chỉ đẩy `app.js/index.html/app.css` qua OTA lên APK cũ sẽ bị khoá hai nút. Xem thêm `README-THU-VIEN-GHI-AM.md`.

## Tùy chỉnh bổ sung (giới thiệu từ giao diện 8.0.7)

- Chỉ hiển thị một dòng **ORBIC RECORDER** do thanh tiêu đề Android quản lý. Nút **XOAY 90°** màu cam và **XOAY 180°** màu xanh nằm cùng hàng với **♫ PHÁT NHẠC** để tiết kiệm không gian.
- Cài đặt → **🔔 VÙNG THÔNG BÁO**: chỉnh riêng chữ trong khung thông báo từ **75% đến 200%**; mặc định 100%, tự lưu trong Android SharedPreferences. Thanh chỉnh chữ chung vẫn độc lập.
- Cài đặt → **🎨 GIAO DIỆN**: kiểu **Đường sóng mềm** là mặc định khi chưa có lựa chọn đã lưu.
- Cài đặt → **🎙 GHI ÂM**: mặc định **tắt ngưỡng phát hiện (ghi liên tục)**, **không lưu WAV gốc**, **chia file mỗi 60 phút**. Đây là giá trị mặc định thật ở Java RecorderService, không chỉ checkbox HTML. **Cấu hình cũ do người dùng lưu vẫn được ưu tiên**, không tự ghi đè khi nâng cấp.
- Danh sách nghe lại: có thể bấm **tên file, ngày giờ của file hoặc nút ▶** để phát/tạm dừng cùng một bản ghi; giữ thứ tự bản ghi mới nhất lên đầu.
- Không thay `applicationId`, `minSdk=28`, targetSdk=32, cấu trúc SAF và quyền ghi âm. Cần build APK mới do có chỉnh sửa native Java; bản hiện tại nhúng giao diện 8.0.9 sẽ thay thế các bản OTA giao diện cũ <=8.0.9.

**Lưu ý Android 9:** Mã nguồn có kiểm thử hợp đồng minSdk 28 và đường lưu tệp SAF/nội bộ, nhưng vẫn cần chạy bản APK trên điện thoại Android 9 thật để xác nhận mic, quyền, ghi dài 60 phút và phát WAV. Không gỡ APK cũ hoặc xóa dữ liệu ứng dụng trước khi sao lưu bản ghi.
