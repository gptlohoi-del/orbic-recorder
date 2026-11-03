# ORBIC RECORDER v9.1 — Full Repo Android APK + OTA + AGC/Limiter

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
3. Tải artifact **`ORBIC-RECORDER-v9.1-debug`**, giải nén và cài APK trên Android.

**Phải tạo APK v9.1 mới lần này** vì mã Java đã đổi; chạy OTA riêng không cập nhật được các hàm native. APK debug được ký tự động, nhưng khóa ký **có thể thay đổi giữa các lần GitHub build**, khiến không thể cài đè bản debug trước. Nếu dự định dùng lâu dài, nên tạo và lưu an toàn khóa ký Android ổn định, rồi sử dụng workflow build-release có sẵn.

## OTA giao diện

- OTA **chỉ cập nhật giao diện `index.html`, `app.css`, `app.js`**, không thay đổi Java, quyền Android hay file APK. `OtaManager` giới hạn và kiểm tra chữ ký RSA trước khi cài. **v9.1 dùng `nativeApi=6`, giao diện nhúng `6.0.0`**. OTA cũ `nativeApi=5` không tương thích với v9.1, không được ép cài.
- Sau khi cài APK v9.1, chỉnh các file UI trong repo, Commit, vào **Actions → Publish Orbic Recorder OTA → Run workflow**. Script tự tăng số phiên bản: `6.0.1` → `6.0.2` … (hoặc cao hơn nếu GitHub đã có bản mới).
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
| `app/src/main/java/com/example/orbicrecorder/OtaManager.java` | OTA có chữ ký, nativeApi 6 |
| `.github/workflows/build.yml` | Build APK debug trên GitHub |
| `.github/workflows/publish-ota.yml` | Tự tăng và phát hành OTA có chữ ký |

## Kiểm thử bắt buộc trên điện thoại thật

Trước khi dùng cho các bản ghi quan trọng: thử START khi đang phát bài; PAUSE/STOP nhạc khi bài còn nhiều hơn 2 giây và ít hơn 2 giây; xác nhận không có nhạc trong WAV; nhấn PAUSE ghi âm thủ công; DỪNG & LƯU giữa lúc nhạc phát; chuyển bài; xoay ngược 180°; đổi ngưỡng ± khi đang nghe và kiểm tra thay đổi ngưỡng có hiệu lực; thử OTA sau khi cài APK v9.1.

Mã nguồn đã được kiểm tra cú pháp JavaScript và qua các test tĩnh. **Chưa biên dịch thành APK và chưa kiểm thử audio/micro trên Android thật trong môi trường này.**


## Kiểm tra AGC của máy ngay trong Settings (v9.1)

Mở **Settings → 🛡 CHỐNG QUÁ TẢI ÂM THANH**: dòng **Hỗ trợ AGC trên điện thoại** sẽ hiển thị **CÓ HỖ TRỢ (API Android)** hoặc **KHÔNG HỖ TRỢ (API Android)** ngay cả khi chưa bấm START (không cần quyền micro để hỏi hỗ trợ API). Khi bắt đầu ghi, dòng **Trạng thái khi thu** mới báo AGC hệ thống có được kích hoạt trên nguồn âm đang dùng hay không. `AutomaticGainControl.isAvailable()` chỉ xác nhận tính sẵn có của effect Android, **không cam kết chất lượng chống méo của phần cứng**.

Phải cài **APK v9.1 mới** để có kiểm tra API native này, chỉ OTA không đủ. APK mới có thể cài đè APK cũ **nếu được ký bằng cùng chứng chỉ Android và `versionCode` tăng**. Nếu GitHub debug dùng khóa ký khác, Android có thể yêu cầu gỡ bản cũ; **hãy xuất/sao lưu file ghi âm trước**, vì gỡ ứng dụng có thể xóa dữ liệu riêng của ứng dụng. Sau khi cài APK v9.1, OTA UI thuộc `nativeApi=6`.
