# ORBIC RECORDER v6 — phát nhạc + ghi âm theo tiếng động + OTA

Mã nguồn đầy đủ Android (Java + WebView offline UI), dựa trên Orbic Recorder v5.1. App đã có APK và OTA từ phiên bản gốc; đây là bản **nâng cấp native v6**, không phải một web-wrapper khác.

## 1. Chức năng

- Giao diện **OneView không cuộn**: phát nhạc phía trên, ghi theo tiếng động phía dưới; dọc và ngang đều có nút chính trên màn hình. Settings là một hộp nổi **cuộn riêng**.
- **Bài 1, Bài 2**: chạm biểu tượng thư mục cạnh mỗi bài để chọn tệp trong điện thoại. Tên/địa chỉ tệp được Android lưu lại sau khi khởi động lại. `BÀI KHÁC` để chọn và nghe tạm, không ghi đè hai bài mặc định. Chỉ phát **một bài mỗi lần**, hết bài tự dừng (không lặp/không tự qua bài khác).
- **Fade In/Fade Out**: áp dụng trực tiếp đến âm lượng MediaPlayer; theo thời gian phát và thời lượng còn lại, không chỉnh âm lượng hệ thống; hỗ trợ cài đặt thời gian, âm lượng nhạc.
- Khi đang thu và bấm phát nhạc, native recorder **hoàn thiện bộ đệm đang giữ, đồng bộ dữ liệu WAV và giải phóng AudioRecord**. Chỉ khi micro đã tắt thì âm nhạc mới bắt đầu. Sau khi nhạc kết thúc hoặc dừng bằng nút STOP, chờ khoảng trễ cấu hình rồi mở micro lại. Recorder vẫn ở phiên cũ và **chỉ giữ âm thanh khi tiếng động vượt ngưỡng** (VAD), như chế độ ghi âm gốc. Không tự chuyển về chế độ ghi nếu người dùng đã chọn PAUSE thủ công; để tránh hiểu nhầm, chương trình yêu cầu tiếp tục hoặc dừng PAUSE trước khi bấm phát nhạc.
- Nếu trước lúc phát chưa mở phiên ghi, khi nhạc kết thúc chương trình **tự bắt đầu phiên lắng nghe** nếu có quyền micro. Lần đầu phát có thể yêu cầu cấp quyền micro.
- Recorder PAUSE, STOP và SAVE có hộp xác nhận. `SAVE` đóng/xuất **đoạn WAV hiện tại và tiếp tục lắng nghe**, `STOP` kết thúc và lưu phiên, `PAUSE` là tạm dừng thủ công chỉ tiếp tục khi bấm `TIẾP TỤC`.
- Cơ chế cũ vẫn còn: bộ lọc khoảng lặng, giữ trước/sau tiếng động, WAV gốc tùy chọn, chia WAV theo thời gian, xử lý file tạm, tự khôi phục sau sự cố, chọn thư mục xuất WAV. Nghe lại bản ghi gần nhất tại **Cài đặt → Lưu trữ** (cũng dùng cơ chế tạm ngưng micro).
- SETTINGS học **cách điều khiển** từ Tụng Kinh (nút ±, slider, nhập số, giá trị hiện tại, MIN/MAX/STEP/DEFAULT, nhóm tùy chọn, tự lưu) nhưng dùng thông số đặc thù của Orbic. Dữ liệu cài đặt lưu trong Android `SharedPreferences` (WebView không bật localStorage). Android kiểm tra giới hạn an toàn kể cả khi JavaScript gửi giá trị xấu.

> Lưu ý: tùy chọn **Lưu WAV gốc** chủ động lưu cả tiếng im lặng, phù hợp làm bản dự phòng. WAV đã lọc mới là bản chỉ lưu đoạn vượt ngưỡng. Ghi âm bằng VAD dựa trên dB không tự phân biệt lời nói với tiếng động khác.

## 2. Tạo APK trên GitHub mà không cần Release Key

1. Giải nén **toàn bộ** mã nguồn vào repository GitHub `gptlohoi-del/orbic-recorder` (hoặc thay cấu hình OTA nếu dùng repo khác), giữ nguyên đường dẫn `app/`, `ota/`, `.github/`, `scripts/`.
2. GitHub → **Actions** → `Tạo APK Orbic Recorder (không cần Release Key)` → `Run workflow`, hoặc push lên nhánh `main` để tự chạy.
3. Khi workflow thành công, vào mục **Artifacts** của lần chạy, tải `ORBIC-RECORDER-v6-debug` (APK trong file nén artifact). Cài APK trên Android.
4. Lần đầu sử dụng cấp quyền micro, vào thư mục chọn hai bài nhạc và chọn thư mục xuất WAV nếu muốn.

APK dạng **debug-signed** không cần Release Key/Secrets. *Không bảo đảm cập nhật APK khác lần build qua cài chồng*: GitHub runner có thể dùng khóa debug khác; trong trường hợp đó phải gỡ APK cũ trước khi cài APK mới, **hãy sao lưu dữ liệu ghi âm và cài đặt** trước khi gỡ. Để cài chồng APK không mất dữ liệu về lâu dài cần một khóa ký Android cố định. OTA giao diện không phụ thuộc việc cài APK mới.

`Build Recorder Signed Release APK` vẫn có trong repository như **lựa chọn bổ sung**, chỉ dùng nếu muốn APK ký release cố định và đã tự thiết lập Secrets tương ứng. Không cần cho quy trình debug bên trên.

## 3. OTA đã có sẵn — nay dành cho nativeApi 2

Đây là **OTA giao diện web** có chữ ký số: chỉ `index.html`, `app.css`, `app.js` được tải và cài qua mạng. Java native (MusicPlayer, ghi âm, quyền micro, AndroidManifest, audio-focus) **không thể cập nhật bằng OTA**: phải tạo/cài APK mới khi cần sửa phần đó.

**An toàn tương thích:** native API tăng từ `1` lên `2`, giao diện tích hợp sẵn là `2.0.0`. Khi cập nhật APK từ v5.1 lên v6, OTA giao diện đời cũ sẽ bị vô hiệu để không ghi đè UI mới. `ota/latest.json` trong repo ban đầu là bản `1.0.2`/API `1`, do đó app v6 sẽ báo OTA hiện tại chưa tương thích **cho đến khi bạn xuất bản OTA v2 mới**. Đây không phải lỗi ghi âm.

### Xuất bản OTA mới

1. Điều chỉnh file UI trong `app/src/main/assets/ui/`.
2. Trong repository → **Settings → Secrets and variables → Actions**, thiết lập `OTA_SIGNING_KEY_B64` từ **khóa riêng RSA khớp với public key** đã gắn sẵn trong `OtaManager.java` (nếu trước đó đã cấu hình khóa OTA, giữ nguyên Secret). **Không commit khóa riêng** lên GitHub.
3. Actions → `Publish Orbic Recorder OTA` → chạy với version `2.0.1` hoặc số lớn hơn (mẫu `N.N.N`, tăng dần). Workflow đóng gói, ký và commit `ota/latest.json`, `ota/bundles/VERSION.zip`.
4. Mở ứng dụng có Internet, vào **Cài đặt → Cập nhật OTA → Kiểm tra → Cập nhật**. Có thể quay lại giao diện trước qua nút Quay lại (nếu còn bản hợp lệ).

Nếu **không còn khóa riêng RSA** của dự án gốc, phải tạo *cặp khóa OTA mới*, thay `PUBLIC_KEY_B64` bằng public key tương ứng trong Java, thiết lập secret riêng rồi **build và cài APK v6 mới một lần** trước khi OTA hoạt động. Cần lưu ý **khóa ký OTA khác hoàn toàn khóa ký APK**; chỉ khóa OTA là bắt buộc để phát hành OTA có chữ ký.

### Repo khác gptlohoi-del/orbic-recorder?

Hãy sửa cặp đường dẫn URL trong `OtaManager.java` (`MANIFEST_URL`, `BUNDLE_PREFIX`) và biến `OWNER`, `REPO` trong `scripts/publish_ota.py` theo tên repository **public** thực tế. Các URL raw.githubusercontent.com yêu cầu tệp có thể truy cập công khai. APK cần build lại một lần sau khi thay URL. Đừng chỉ đổi tên repository mà giữ nguyên URL cũ.

## 4. Cấu trúc chính

```
app/src/main/java/com/example/orbicrecorder/
  MainActivity.java      # WebView bridge, chọn nhạc, điều phối nhạc-micro, settings
  MusicPlayer.java       # MediaPlayer, audio-focus, fade-in/fade-out, kết thúc bài
  RecorderService.java   # VAD/WAV; nhường micro cho nhạc và nối lại phiên
  RecordingArchive.java  # WAV gốc/WAV đã lọc, chia file, khôi phục
  OtaManager.java        # OTA web đã ký, nativeApi 2, chống OTA cũ
app/src/main/assets/ui/
  index.html             # OneView và Settings
  app.css                # Responsive dọc/ngang
  app.js                 # Controls, xác nhận, thông số số học, tự lưu
.github/workflows/
  build.yml              # Debug APK tự động, không cần release key
  build-release.yml      # Release APK tùy chọn (cần Secrets)
  publish-ota.yml        # OTA có chữ ký (cần OTA_SIGNING_KEY_B64)
scripts/publish_ota.py  # Đóng gói, ký và kiểm tra khóa OTA
```

## 5. Kiểm thử thiết bị nên thực hiện trước khi dùng thật

- Bật lắng nghe, phát ra tiếng động ngắn rồi kiểm tra WAV đã lọc và WAV gốc; thử SAVE và STOP.
- Đang có tiếng động → phát Bài 1/Bài 2, xác nhận micro tắt khi nhạc chạy; hết bài, dừng giữa bài, Pause/Resume nhạc, rồi tạo tiếng động mới để kiểm tra ghi tiếp.
- Thử PAUSE thủ công, cuộc gọi/tai nghe, khóa màn hình, màn hình dọc/ngang và lưu hai bài từ bộ nhớ điện thoại.
- Đổi MIN/MAX/STEP/DEFAULT; thử giá trị vượt giới hạn an toàn; xác nhận cấu hình tự lưu sau khi đóng mở app.
- Chạy build GitHub Actions thực tế trước khi phân phối APK. Mã nguồn ở đây đã qua kiểm tra tĩnh; **chưa build Android APK hay chạy trên điện thoại thật** trong môi trường tạo bản ZIP.

## 6. Quyền riêng tư

Chương trình chỉ truy cập micro khi người dùng bắt đầu chế độ lắng nghe hoặc khi tự khởi động lại lắng nghe sau bài nhạc, có yêu cầu cấp quyền Android. Không tải file ghi âm lên máy chủ. Chỉ các file UI được tải từ GitHub dưới dạng OTA đã ký; hai bài nhạc người dùng chọn nằm trên thiết bị.
