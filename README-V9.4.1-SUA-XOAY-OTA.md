# ORBIC RECORDER v9.4.1 — SỬA NÚT XOAY 180° / OTA

## Tóm tắt

Trên APK v9.4, nút `⤾ XOAY 180°` gọi `setRequestedOrientation()` để yêu cầu Android đổi hướng màn hình. Một số thiết bị/ROM (bao gồm khả năng xảy ra trên Android 9) không chấp nhận hướng ngược, nên bấm nút không thấy màn hình đổi. Không có xác nhận kết quả ở mã gốc. Đây là nguyên nhân **suy ra từ mã nguồn**, chưa được chứng thực trên thiết bị của người dùng.

**Có thể sửa qua OTA cho APK v9.4 đã cài**: `app.js` sẽ nhận biết Android bridge cũ, rồi xoay **giao diện bên trong ứng dụng** bằng CSS `rotate(180deg)`. Bấm lần nữa về vị trí cũ. Cách này KHÔNG xoay thanh trạng thái/thanh điều hướng của hệ thống Android hoặc màn hình ngoài ứng dụng.

**Có bản sửa native để dùng cho APK mới (tùy chọn)**: `MainActivity.java` sử dụng `WebView.setRotation(180f)`, có lưu trạng thái xoay, hỗ trợ tọa độ chạm do Android xử lý. JS kiểm tra `supportsReliableRotation()` để tránh xoay hai lần. Bản native chỉ có tác dụng sau khi build và cài APK, KHÔNG thể chép Java qua OTA.

Các chức năng nhạc (3 bài), ghi WAV, DỪNG & KHÔNG LƯU và các luồng ghi âm giữ nguyên. Phần xử lý swipe được điều chỉnh để vẫn thao tác đúng hướng khi giao diện xoay bằng CSS.

## 1. Phát hành OTA từ TOÀN BỘ repo này (không cần APK mới)

Cần đủ **3 điều kiện**:

1. APK đang cài là bản v9.4 dùng `OtaManager.NATIVE_API = 8` và trỏ tới `https://raw.githubusercontent.com/gptlohoi-del/orbic-recorder/main/ota/latest.json`.
2. Có quyền ghi vào repo GitHub `gptlohoi-del/orbic-recorder`, nhánh `main`.
3. Repository GitHub Actions có secret `OTA_SIGNING_KEY_B64`, chứa private PEM (mã hóa base64) **khớp với** `PUBLIC_KEY_B64` ghim trong APK đang cài. Không có khóa private tương ứng thì **không thể** tạo OTA được APK chấp nhận, dù có mã nguồn đầy đủ.

Cách thực hiện:

1. Sao lưu repo hiện tại, giải nén ZIP, và đẩy **nội dung bên trong thư mục `orbic-recorder-main/`** lên **gốc nhánh `main`** của repo `gptlohoi-del/orbic-recorder`. Không tải mỗi file ZIP lên GitHub.
2. Giữ nguyên khóa ký OTA đã sử dụng để build APK v9.4; **không** tự thay `PUBLIC_KEY_B64` khi chỉ muốn OTA. Kiểm tra secret ở **Settings → Secrets and variables → Actions → `OTA_SIGNING_KEY_B64`**. Không đưa secret vào file, chat, Issue hay commit.
3. Mở **GitHub → Actions → Publish Orbic Recorder OTA → Run workflow**. Workflow gọi `scripts/publish_ota.py` để đóng gói đúng **3 file** `index.html`, `app.css`, `app.js`, tăng version ít nhất lên `8.0.1`, tạo SHA-256, ký RSA và commit các file `ota/latest.json` cùng `ota/bundles/<version>.zip`.
4. Trên điện thoại đang chạy APK v9.4, kết nối Internet, mở **CÀI ĐẶT → CẬP NHẬT OTA → KIỂM TRA → CẬP NHẬT**. Thoát phiên ghi trước khi cập nhật. Sau khi cập nhật, bấm **⤾ XOAY 180°**, xác nhận toàn bộ UI xoay và bấm lại để quay về. Thử cả hộp thoại, nút phát nhạc, và vuốt hủy trên bản ghi thử (không dùng bản ghi quan trọng).
5. Nếu OTA báo *không tương thích*, xem `nativeApi`: bản này yêu cầu **8**. Nếu báo *chữ ký không hợp lệ*, kiểm tra cặp khóa **đang được ghim trong APK v9.4**, không thử tắt kiểm tra chữ ký.

**Quan trọng về repo đang công khai:** khi soát ngày 04/11/2026, `ota/latest.json` trên đường dẫn ghim vẫn có `nativeApi: 1` và `version: 1.0.1`. Đây **không phải** OTA cho APK v9.4. Phải phát hành bản OTA **nativeApi: 8** mới, theo các bước trên; chỉ upload repo mà không chạy workflow OTA sẽ chưa khiến điện thoại có bản mới.

## 2. Tệp đã sửa

- `app/src/main/assets/ui/app.js` — kiểm tra Android bridge, dùng CSS fallback ở APK v9.4, giữ đúng hướng vuốt xác nhận trong màn hình đã xoay.
- `app/src/main/assets/ui/app.css` — xoay toàn bộ `body` 180°, gồm bảng điều khiển và modal.
- `app/src/main/java/com/example/orbicrecorder/MainActivity.java` — tùy chọn nếu build APK mới: xoay native WebView, ghi nhớ trạng thái, xuất khả năng hỗ trợ cho UI.
- `app/build.gradle` — tăng versionName/versionCode cho APK v9.4.1 tùy chọn.
- `tests/test_rotation_180.py` — kiểm thử fallback cũ / bridge mới, không dùng Android SDK.

Không cần thay `nativeApi=8` hoặc thay địa chỉ `MANIFEST_URL` của APK v9.4 đã cài.

## 3. Thử mã nguồn trước khi xuất bản

```bash
node --check app/src/main/assets/ui/app.js
python3 tests/test_rotation_180.py
python3 tests/test_project.py
python3 tests/test_v94_features.py
python3 tests/test_android9_compat.py
python3 tests/test_audio_dynamics.py
```

Đây là kiểm thử logic/tĩnh, **không thay thế chạy thử trên Android 9**. Môi trường tạo repo không có Android SDK, nên chưa biên dịch APK, chưa test trực tiếp trên điện thoại và **chưa** phát hành OTA lên GitHub (cần người sở hữu repo + khóa ký).

## 4. Nếu muốn APK native mới

Dùng `.github/workflows/build-release.yml` với **đúng cùng khóa ký Release APK cũ**, rồi cài đè APK cũ. Không gỡ app để tránh mất dữ liệu hoặc quyền truy cập thư mục đã chọn. Nếu build APK debug với khóa khác, Android sẽ không cho cài đè APK Release. Vui lòng sao lưu các file WAV quan trọng trước khi nâng cấp.

Lưu ý: xoay native WebView cũng chỉ xoay giao diện ứng dụng, không xoay thanh điều hướng/trạng thái Android. Giải pháp này không thể điều khiển hướng màn hình của các ứng dụng khác.
