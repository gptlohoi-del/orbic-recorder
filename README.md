**CẬP NHẬT 04/11/2026:** Để sửa nút **XOAY 180°** trên APK v9.4 qua **OTA** mà không cần cài lại, xem [`README-V9.4.1-SUA-XOAY-OTA.md`](README-V9.4.1-SUA-XOAY-OTA.md). Phải phát hành OTA có `nativeApi=8` và chữ ký khớp APK hiện hữu. Các phần hướng dẫn v5.x bên dưới chỉ mang tính tham khảo lịch sử.

# ORBIC RECORDER v9.4.1 — sửa nút XOAY 180° (APK mới / OTA fallback)

**Đọc trước:** [`README-V9.4.1-XOAY-180.md`](README-V9.4.1-XOAY-180.md) giải thích cách sửa native Android, cách cập nhật bằng OTA cho APK v9.4 cũ và hướng dẫn build full repo. Giao diện 3 bài hát, ghi WAV và nút vuốt DỪNG & KHÔNG LƯU được giữ nguyên.

## Thông tin lịch sử v9.4


**Mã nguồn đầy đủ, hướng dẫn build và quy trình cập nhật:** đọc [`README-V9.4-NANG-CAP.md`](README-V9.4-NANG-CAP.md). Bản v9.4 thêm xoay Android 180°, 3 bài nhạc chọn nhanh, và DỪNG & KHÔNG LƯU xác nhận bằng thao tác vuốt. OTA nativeApi 8, phiên bản nhúng 8.0.0, Android 9 trở lên.

**Lưu ý:** Các phần bên dưới là lịch sử hướng dẫn của v9.3 và cũ hơn. Để build APK v9.4, ưu tiên tài liệu v9.4 ở trên.

---

## Sửa lỗi GitHub Actions `android:icon` / `aapt dump badging`

Bản này thay icon hệ thống `@android:drawable/ic_btn_speak_now` trong `AndroidManifest.xml` thành icon **đóng gói ngay trong APK**: `@mipmap/ic_launcher` (5 mật độ màn hình trong `app/src/main/res/mipmap-*/ic_launcher.png`). Nhờ vậy công cụ `aapt dump badging` có thể đọc icon từ gói ứng dụng, tránh lỗi `attribute value reference does not exist` tại bước xác minh APK. **Không cần thay đổi ngưỡng Android 9 (`minSdk 28`), mã Java, hay OTA.** Sau khi cập nhật, hãy chạy lại workflow **Tạo APK Orbic Recorder**; không phải chạy OTA.

# ORBIC RECORDER v9.3 – tương thích Android 9 (API 28)

**Đã sửa lỗi không cài được trên Android 9 do `minSdk 29` trong bản v9.2.**
Bản này sử dụng `minSdk 28`, vẫn giữ nguyên tên gói
`com.example.orbicrecorder`, các chức năng ghi theo tiếng động, hai bài nhạc,
Fade, AGC/Limiter (nếu thiết bị hỗ trợ), bốn kiểu sóng âm, và OTA `nativeApi=7`.
Không cần loại bỏ chức năng nâng cao để sửa lỗi cài đặt này.

## Hướng dẫn Android 9

1. Upload mã nguồn lên GitHub, giữ thư mục `ota/` đã có. Vào **Actions → Tạo APK Orbic Recorder (không cần Release Key)**, rồi tải artifact `ORBIC-RECORDER-v9.3-debug` và giải nén APK.
2. APK được build với **minSdk 28**. Workflow dùng `aapt dump badging` để kiểm tra chính APK đã biên dịch; vẫn cần thử cài và ghi WAV trên Android 9 thực tế.
3. **Rất quan trọng về WAV:** Android 9 không có API MediaStore `RELATIVE_PATH` / `IS_PENDING` của Android 10. Vì thế nếu **không chọn thư mục**, WAV mặc định được lưu ở vùng `Android/data/com.example.orbicrecorder/files/Music/OrbicRecorder/` (thường trong `DaLoc/` và `Goc/`); dữ liệu ở đây **có thể bị xóa khi gỡ ứng dụng**. Hãy vào **Settings → Chọn thư mục lưu WAV**, chọn thư mục riêng như `Music/OrbicRecorder` trên bộ nhớ chung bằng trình chọn thư mục Android (SAF). WAV đã xuất sang thư mục này tồn tại độc lập với dữ liệu riêng của ứng dụng. Một số trình chọn thư mục của thiết bị có hạn chế riêng.
4. Trên Android 10 trở lên, không chọn thư mục sẽ tiếp tục xuất WAV vào `Music/OrbicRecorder` bằng MediaStore như v9.2.
5. Nếu gặp **“Ứng dụng chưa được cài đặt”** dù APK đã có `minSdk 28`: kiểm tra bộ nhớ trống và tình trạng APK đã tải đủ; nếu máy đã có ứng dụng cùng tên gói nhưng khác chữ ký thì phải dùng cùng keystore để cài đè, hoặc **sao lưu WAV và cấu hình trước khi gỡ bản cũ**. Nếu báo `INSTALL_FAILED_VERSION_DOWNGRADE`, hãy nâng `versionCode` thay vì gỡ ứng dụng khi còn dữ liệu cần giữ. GitHub debug mới có thể ký bằng chứng chỉ khác các bản cũ.
6. WebView trên Android 9 nên được cập nhật qua Google Play nếu có thể; mã giao diện cũng đã được điều chỉnh để tránh các cú pháp JavaScript/CSS quá mới. OTA chỉ cập nhật giao diện; **lỗi minSdk phải cài APK mới**.

**Giới hạn kiểm thử:** Trong môi trường chuẩn bị mã nguồn chưa có Android SDK/emulator hoặc điện thoại Android 9, nên chưa build và cài APK thực tế. Các phép thử nguồn và JavaScript được chạy tại đây; GitHub Actions sẽ build và kiểm tra APK.

---

# ORBIC RECORDER v9.2 — 4 kiểu sóng âm

**Mới:** Trong **Settings → GIAO DIỆN**, chọn một trong bốn kiểu sóng âm có hình xem trước: **Cột đứng**, **Cột đối xứng**, **Đường sóng mềm**, **Chấm nhịp**. Thay đổi hiện ngay, tự lưu trong Android và được khôi phục ở lần mở sau. Vạch ngưỡng dBFS vẫn có ở cả bốn kiểu; thay đổi kiểu không tác động micro, mức độ nhạy hay dữ liệu WAV. Mục **Độ cao vùng sóng âm** tiếp tục hoạt động như trước.

**Cần APK mới** vì việc lưu kiểu sóng đã bổ sung vào Android Native (`MainActivity.java`); OTA của APK v9.1 không đủ để lưu lựa chọn. Bản v9.2 dùng `nativeApi=7`, giao diện gốc `7.0.0`. Các cập nhật giao diện sau khi cài APK mới có thể phát hành OTA có chữ ký với `nativeApi=7`. Không xoá các bản OTA đã phát hành trong repository.

**Lưu ý kỹ thuật:** Các kiểu này biểu diễn lịch sử mức âm thanh RMS (dBFS) theo thời gian, không phải đồ thị mẫu PCM liên tục. Thanh ngưỡng được đổi tương ứng cùng hệ đo. Chế độ 'Đối xứng' mang tính trình bày; ngưỡng hiển thị đối xứng. Không có thay đổi phần ghi âm AGC/Limiter.

---

# Hướng dẫn đầy đủ — Android APK + OTA + AGC/Limiter

Đây là mã nguồn đầy đủ Java/Android + giao diện HTML/CSS/JavaScript ngoại tuyến, phát triển từ Orbic Recorder v8 (bản đã sửa tăng âm khi TIẾP). **Không phải file APK đã biên dịch.** Android SDK/Gradle sẽ được GitHub Actions dùng để tạo APK.

## Chức năng và nguyên lý

- **OneView không cuộn**: bỏ tiêu đề dài, phát nhạc phía trên, ghi âm phía dưới; vùng **THÔNG BÁO** sau hàng nút nhạc chứa trạng thái nhạc, trạng thái micro, thời gian/file ghi và nút **⚙ CÀI ĐẶT**. Chỉ bảng Settings cuộn độc lập.
- **Hai nút ▶ BÀI 1 (trái) / ▶ BÀI 2 (phải)** đối xứng qua giữa. Chọn hai file mặc định từ điện thoại; có thể phát file BÀI KHÁC. Hết bài tự dừng, không tự chuyển bài. Chuyển bài khi đang chơi hỏi xác nhận, bài cũ sẽ fade trước khi chuyển.
- **TIẾP sau PAUSE cũng tăng âm dần**: dùng chung thời gian cấu hình với PAUSE/STOP (mặc định 2.000 ms), âm lượng khởi đầu bằng 0 và tăng êm theo đường cong smoothstep, không giật âm. Thao tác với micro vẫn như trước: micro nhường quyền trước khi phát lại.
- **PAUSE và STOP nhạc đều có fade**: khi bấm, nhạc **vẫn phát trong thời gian fade** và âm lượng giảm đều về 0; mặc định **2.000 ms**. Nếu còn ít hơn 2 giây, fade được rút ngắn bằng thời lượng còn lại; nếu hết bài tự nhiên thì tự dừng. Cuối fade, PAUSE mới thực sự tạm dừng MediaPlayer, STOP mới đóng player. Thời gian này có trong Settings với MIN/MAX/STEP/DEFAULT, chỉnh số trực tiếp và tự lưu.
- **Micro không ghi khi nhạc còn phát**, kể cả đang fade về 0. Vẫn thao tác được tất cả lệnh ghi âm hợp lệ khi nhạc phát: **START**, **PAUSE/TIẾP TỤC**, **DỪNG & LƯU**. START trong lúc nhạc phát ghi nhận yêu cầu mở phiên ghi **nhưng không mở micro**, sau khi nhạc hết hoặc thật sự PAUSE thì trở lại nghe tiếng động. Nếu người dùng chưa START thì nhạc tự dừng **không tự kích hoạt phiên ghi**.
- Nếu đã nhấn **PAUSE ghi âm thủ công**, nhạc dừng vẫn giữ PAUSE cho tới khi người dùng TIẾP TỤC. Nếu **DỪNG & LƯU ghi âm**, WAV được hoàn tất an toàn, phiên thu kết thúc và không ảnh hưởng nhạc. PAUSE và DỪNG & LƯU có xác nhận.
- Khi **tiếp tục phát nhạc từ PAUSE**, micro đang hoạt động phải giải phóng trước khi phát lại (có chờ xác nhận); nhạc tăng âm nhẹ để tránh giật tiếng. Khi PAUSE nhạc hoàn tất và được phát lại, cơ chế giám sát tiếng động được bảo toàn.
- **Vạch ngang ngưỡng tiếng động** trên biểu đồ mức dBFS, trùng thang đo với chiều cao các cột. Có nút **− / +** ngay dưới sóng âm; đổi ngưỡng và lưu tức thì, bộ ghi cập nhật trong khoảng 100 ms (khi đang nhận các frame micro bình thường).
- Settings có **độ cao vùng sóng âm**, độ lớn chữ, chủ đề, fade-in/out của bài, fade khi PAUSE/STOP, thời gian đợi mở lại micro, ngưỡng VAD, các thông số thu, giới hạn nâng cao MIN/MAX/STEP/DEFAULT. **Cảnh báo không tín hiệu mặc định là 9999 giây** (giá trị cũ đã lưu có thể vẫn được sử dụng).
- Điện thoại có thể **xoay ngược 180°** qua `screenOrientation="fullSensor"`; thay đổi cấu hình khi xoay không khởi tạo lại Activity, tránh ngắt MediaPlayer. Thử trên máy thật vì một số thiết bị có thể khóa xoay của hệ thống.
- Lọc khoảng lặng và cơ chế WAV/raw/chia file/khôi phục bản ghi kế thừa từ dự án cũ. Nhạc không lọt vào cả file raw WAV vì micro đã giải phóng trong khi nhạc phát.

## Chống quá tải — chức năng mới ở v9

Trong **Settings → 🛡 CHỐNG QUÁ TẢI ÂM THANH** có các mục tự lưu:

| Thông số | Mặc định | Tác dụng |
| --- | --- | --- |
| **AGC tự động của Android** | Bật | Dùng `AutomaticGainControl` của Android nếu thiết bị thực sự hỗ trợ. Trạng thái hỗ trợ được hiển thị trong Settings. Không ép được trên tất cả điện thoại. |
| **Limiter WAV đã lọc** | Bật | Giảm mức tín hiệu PCM **trước khi lưu WAV đã lọc khoảng lặng** để hạn chế peak/vỡ số. |
| **Cảnh báo đầu vào gần quá tải** | Bật | Thông báo khi peak của tín hiệu thu gần đầy thang số (dBFS). |
| **Ngưỡng Limiter** | **−6 dBFS** | Mức trần cho file WAV đã lọc; dải an toàn −30 đến −1 dBFS. |
| **Attack** | **10 ms** | Thời gian điều chỉnh gain theo chiều giảm (1–250 ms); limiter vẫn chặn ngay peak vượt trần để bảo vệ PCM. |
| **Release** | **500 ms** | Thời gian khôi phục gain sau peak (20–5000 ms). |
| **Ngưỡng cảnh báo** | **−1 dBFS** | Báo trước khi tín hiệu chạm trần số; dải −12 đến −1 dBFS. |

Các thông số số học có **thanh trượt, −/+, ô nhập trực tiếp, MIN/MAX/STEP/DEFAULT trong Nâng cao**, áp dụng gần như tức thì (khoảng 100 ms khi thu). Công tắc cũng tự lưu. **Ngưỡng phát hiện tiếng động (VAD) tiếp tục đo trên PCM đầu vào trước Limiter**, không phải mức âm đã nén, vì vậy việc giới hạn đầu ra không làm đổi ngưỡng VAD của ứng dụng.

**Lưu ý chất lượng:** Limiter chỉ ngăn biên độ số của tín hiệu còn nguyên khỏi vượt trần được đặt. Nếu âm đã méo ở micro/mạch ADC hoặc Android đã làm clipping trước khi trả dữ liệu, phần mềm **không thể khôi phục âm thanh**. Khi thu sát nguồn rất lớn nên giảm âm lượng nguồn, tăng khoảng cách micro, hoặc dùng micro/thiết bị có gain phần cứng. Android AGC đôi lúc làm tăng cả tiếng ồn nền và độ nhạy VAD, nên có công tắc tắt.

**WAV dự phòng (RAW):** lưu PCM sau đường thu Android nhưng **trước Limiter phần mềm**, không cắt khoảng lặng. Đây là bản đối chiếu, **không cam kết hết rè**; WAV lọc tiếng động là file được Limiter bảo vệ. Tắt backupRaw nếu không muốn file dự phòng, nhưng nên giữ khi cần đối chiếu. Khi phát nhạc, micro vẫn được nhả hoàn toàn; không thêm nhạc vào file RAW lẫn WAV đã lọc.

## Đưa mã nguồn lên GitHub và tạo APK

1. Giải nén **nội dung** thư mục `orbic-recorder-main/` lên thư mục gốc repository `gptlohoi-del/orbic-recorder` (địa chỉ OTA đang cấu hình sẵn). Giữ lại `ota/latest.json` và các file cũ trong `ota/bundles/` trên GitHub; không xóa lịch sử phát hành OTA.
2. Commit lên nhánh `main`. Vào **Actions → Tạo APK Orbic Recorder (không cần Release Key) → Run workflow**. Workflow cũng chạy sau push mã nguồn.
3. Tải artifact **`ORBIC-RECORDER-v9.2-debug`**, giải nén và cài APK trên Android.

**Phải tạo APK v9.2 mới lần này** vì mã Java đã đổi; chạy OTA riêng không cập nhật được các hàm native. APK debug được ký tự động, nhưng khóa ký **có thể thay đổi giữa các lần GitHub build**, khiến không thể cài đè bản debug trước. Nếu dự định dùng lâu dài, nên tạo và lưu an toàn khóa ký Android ổn định, rồi sử dụng workflow build-release có sẵn.

## OTA giao diện

- OTA **chỉ cập nhật giao diện `index.html`, `app.css`, `app.js`**, không thay đổi Java, quyền Android hay file APK. `OtaManager` giới hạn và kiểm tra chữ ký RSA trước khi cài. **v9.2 dùng `nativeApi=7`, giao diện nhúng `7.0.0`**. OTA của API 6 trở xuống không tương thích với v9.2, không được ép cài.
- Sau khi cài APK v9.2, chỉnh các file UI trong repo, Commit, vào **Actions → Publish Orbic Recorder OTA → Run workflow**. Script tự tăng số phiên bản: `7.0.1` → `7.0.2` … (hoặc cao hơn nếu GitHub đã có bản mới).
- GitHub cần Secret **`OTA_SIGNING_KEY_B64`** của RSA private key khớp public key trong `OtaManager.java`. Secret này **khác** khóa ký APK. Nếu không có hoặc không khớp, không thể phát hành OTA hợp lệ; giữ an toàn private key. URL OTA hiện mặc định cho repo `gptlohoi-del/orbic-recorder`; khi đổi repo phải sửa cả URL Java và OWNER/REPO trong `scripts/publish_ota.py` rồi build APK mới.

## File chính

| File | Vai trò |
| --- | --- |
| `app/src/main/assets/ui/index.html` | OneView, vùng thông báo, bố cục nút, ngưỡng tiếng động, Settings |
| `app/src/main/assets/ui/app.css` | CSS responsive dọc/ngang, bố cục Play đối xứng, sóng âm |
| `app/src/main/assets/ui/app.js` | Settings số học, điều khiển độc lập, trạng thái, ngưỡng ± |
| `app/src/main/java/com/example/orbicrecorder/AudioDynamics.java` | Limiter PCM, Attack/Release, trần peak |
| `app/src/main/java/com/example/orbicrecorder/MusicPlayer.java` | Fade PAUSE/STOP với thời gian theo phần nhạc còn lại |
| `app/src/main/java/com/example/orbicrecorder/MainActivity.java` | WebView bridge, micro handoff, chọn nhạc, cập nhật cấu hình |
| `app/src/main/java/com/example/orbicrecorder/RecorderService.java` | VAD, AGC Android, Limiter, cảnh báo peak, WAV, tạm nhường mic |
| `app/src/main/AndroidManifest.xml` | Xoay 180°, quyền và service |
| `app/src/main/java/com/example/orbicrecorder/OtaManager.java` | OTA có chữ ký, nativeApi 7 |
| `.github/workflows/build.yml` | Build APK debug trên GitHub |
| `.github/workflows/publish-ota.yml` | Tự tăng và phát hành OTA có chữ ký |

## Kiểm thử bắt buộc trên điện thoại thật

Trước khi dùng cho các bản ghi quan trọng: thử START khi đang phát bài; PAUSE/STOP nhạc khi bài còn nhiều hơn 2 giây và ít hơn 2 giây; xác nhận không có nhạc trong WAV; nhấn PAUSE ghi âm thủ công; DỪNG & LƯU giữa lúc nhạc phát; chuyển bài; xoay ngược 180°; đổi ngưỡng ± khi đang nghe và kiểm tra thay đổi ngưỡng có hiệu lực; thử OTA sau khi cài APK v9.2.

Mã nguồn đã được kiểm tra cú pháp JavaScript và qua các test tĩnh. **Chưa biên dịch thành APK và chưa kiểm thử audio/micro trên Android thật trong môi trường này.**


## Kiểm tra AGC của máy ngay trong Settings (v9.1)

Mở **Settings → 🛡 CHỐNG QUÁ TẢI ÂM THANH**: dòng **Hỗ trợ AGC trên điện thoại** sẽ hiển thị **CÓ HỖ TRỢ (API Android)** hoặc **KHÔNG HỖ TRỢ (API Android)** ngay cả khi chưa bấm START (không cần quyền micro để hỏi hỗ trợ API). Khi bắt đầu ghi, dòng **Trạng thái khi thu** mới báo AGC hệ thống có được kích hoạt trên nguồn âm đang dùng hay không. `AutomaticGainControl.isAvailable()` chỉ xác nhận tính sẵn có của effect Android, **không cam kết chất lượng chống méo của phần cứng**.

Bản v9.2 tiếp tục có phần kiểm tra AGC này. Vì phần lưu kiểu sóng âm mới dùng Android Native, phải cài **APK v9.2** để sử dụng đầy đủ (chỉ OTA từ v9.1 là không đủ). APK mới có thể cài đè APK cũ **nếu được ký bằng cùng chứng chỉ Android và `versionCode` tăng**. Nếu GitHub debug dùng khóa ký khác, Android có thể yêu cầu gỡ bản cũ; **hãy xuất/sao lưu file ghi âm trước**, vì gỡ ứng dụng có thể xóa dữ liệu riêng của ứng dụng. Sau khi cài APK v9.2, OTA UI thuộc `nativeApi=7`.
