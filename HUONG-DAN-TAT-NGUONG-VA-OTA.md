# ORBIC RECORDER v9.4.2 — TẮT NGƯỠNG NHẬN DIỆN / OTA

## Thay đổi

Trong **CÀI ĐẶT → 🎙 GHI ÂM** có công tắc **Bật ngưỡng nhận diện âm thanh (lọc khoảng lặng)**.

- **Bật** (mặc định, kể cả sau khi nâng cấp): dùng ngưỡng dBFS và các bộ đệm lọc khoảng lặng như trước. Có thể điều chỉnh mức ngưỡng.
- **Tắt**: trực tiếp ghi **tất cả khung âm thanh** vào WAV chính, bao gồm tín hiệu cực nhỏ, số 0 và khoảng lặng; không cần vượt qua ngưỡng hoặc chờ `minSpeech`. Không đổi giá trị ngưỡng cũ và không vô hiệu hóa Limiter/AGC.
- Công tắc lưu vào Android, có hiệu lực trong vòng **tối đa khoảng 100 ms** khi micro đang hoạt động. PAUSE, STOP, chia file và việc **tắt micro trong lúc phát nhạc** vẫn như trước.
- Màn hình chính hiển thị `ĐÃ TẮT` thay vì dBFS; thanh vạch ngưỡng và các nút ± ẩn/vô hiệu tương ứng. Bật lại để khôi phục ngưỡng dBFS đã lưu.

**Lưu ý:** Ghi liên tục làm file WAV chính lớn hơn; các khoảng lặng cũng được giữ lại. Nếu chỉ muốn giữ bản ghi gốc, tùy chọn **Lưu WAV gốc** đã tồn tại từ trước và hoạt động độc lập.

## Đưa lên điện thoại qua OTA: có giới hạn kỹ thuật

OTA hiện tại **chỉ thay 3 file** `index.html`, `app.css`, `app.js`. Nó **không thể đổi** `RecorderService.java` hoặc `MainActivity.java` trong APK cũ. Vì vậy, để tắt ngưỡng thật sự phải **cài cập nhật APK v9.4.2 một lần** (bản build có mã native mới). Cài đè bằng cùng chứng chỉ ký và versionCode mới; **không gỡ ứng dụng** để tránh mất các file/dữ liệu đang nằm trong bộ nhớ riêng.

Nếu gửi bản OTA giao diện cho APK cũ trước khi nâng cấp APK, công tắc sẽ **bị khóa** và hiển thị hướng dẫn cài APK v9.4.2, thay vì báo sai là đã tắt ngưỡng. `nativeApi=8` và khóa xác minh OTA được **giữ nguyên** cho mục đích tương thích.

### Xuất bản OTA giao diện (sau khi có source mới trong GitHub)

1. Cập nhật **toàn bộ nội dung** thư mục dự án này lên nhánh `main` của repository đang được cấu hình `gptlohoi-del/orbic-recorder`. **Giữ** lịch sử `ota/latest.json` và các ZIP cũ trong `ota/bundles`.
2. Từ GitHub: **Actions → Publish Orbic Recorder OTA → Run workflow**. Workflow gọi `scripts/publish_ota.py` để tự tăng số phiên bản (từ 8.0.2 sẽ là **8.0.3** nếu chưa có bản mới), tạo gói, SHA-256 và ký RSA, sau đó commit manifest cùng bundle lên `main`.
3. Bắt buộc có GitHub Secret **`OTA_SIGNING_KEY_B64`** khớp với public key ghim trong APK. Không gửi hoặc commit khóa riêng. Nếu thiếu key, **không thể tạo OTA dùng được**. Không sửa tay chữ ký, SHA hay URL để vượt qua kiểm tra.
4. Cài **APK v9.4.2** đã được ký bởi cùng chứng chỉ release của app đang cài (Actions → Build signed Release; cần release keystore secrets). Trên thiết bị vào **CÀI ĐẶT → CẬP NHẬT OTA → KIỂM TRA → CẬP NHẬT** để nhận giao diện mới. Nếu APK mới đã đi kèm UI mới và chưa có OTA cũ được lưu, chức năng hiện ngay.

*Không có khóa ký riêng của dự án và quyền GitHub, bản ZIP mã nguồn này **không tự phát hành OTA** hay tạo được APK release cài đè. `ota/latest.json` có chữ ký cũ được giữ nguyên, không thay bằng manifest chưa ký.*

## Thử nghiệm trước khi dùng bản ghi quan trọng

1. Mặc định **bật** ngưỡng, xác nhận tính năng phát hiện tiếng động vẫn như cũ.
2. Tắt ngưỡng trong Settings khi đang START, nói nhỏ rồi để **im lặng** trong hơn 5 giây: thời gian **Đã giữ** tiếp tục tăng và WAV chính giữ đoạn im lặng.
3. Đổi ngưỡng qua ON/OFF trong lúc ghi, kiểm tra UI cập nhật trong khoảng 100 ms và giá trị dBFS cũ được giữ.
4. Dùng PAUSE/TIẾP, phát nhạc, chia file, DỪNG & LƯU; kiểm tra không có nhạc thu vào micro và WAV cuối đúng thời lượng.
5. Trên APK cũ chỉ OTA web: công tắc phải bị khóa và nhắc nâng APK; không giả vờ đã ghi liên tục.

Kiểm tra mã nguồn (không thay cho test trên Android thật):

```bash
python3 tests/test_threshold_toggle.py
python3 tests/test_project.py
python3 tests/test_v94_features.py
python3 tests/test_rotation_180.py
python3 tests/test_android9_compat.py
python3 tests/test_audio_dynamics.py
```
