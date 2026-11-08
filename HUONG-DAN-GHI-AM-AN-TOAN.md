# HƯỚNG DẪN GHI ÂM AN TOÀN — v9.5.5

1. Cài đè APK mới với cùng khóa ký bản cũ; không gỡ bản đang cài nếu chưa sao lưu.
2. Cài đặt → GHI ÂM: bật **Lưu thêm WAV gốc**. Chọn thư mục WAV còn nhiều dung lượng; kiểm tra micro đang sử dụng.
3. Dừng nhạc hoàn toàn, nhấn **● START**. Giọng nói dù dưới mức nhỏ vẫn được giữ lại trong trục thời gian, nếu micro thu được.
4. Trong lúc ghi, không có PAUSE và không thể phát nhạc bằng ứng dụng. Khi cần lưu, bấm **DỪNG & LƯU** rồi **vuốt sang phải** hết thanh.
5. Nếu thật sự muốn loại bỏ toàn bộ dữ liệu phiên đang thu, bấm **DỪNG & KHÔNG LƯU** rồi **vuốt sang phải**. Các đoạn trước của phiên đó cũng sẽ bị xóa.
6. Sau khi lưu, kiểm tra `_voice.wav` và `_raw.wav` nếu bật sao lưu, nghe lại đầu/giữa/cuối và so thời lượng với video.

Đề nghị thử nghiệm: thu 30 giây, xen kẽ nói nhỏ và im lặng; kiểm tra WAV đủ 30 giây. Mở hộp Lưu rồi chạm/vuốt chưa hết phải **không dừng**. Thử tương tự với Không lưu; thử vuốt hết 2 chiều để xác nhận chỉ **trái → phải** có hiệu lực. Kiểm tra thông báo không còn nút dừng một chạm. Thử phát nhạc trong lúc ghi và START khi nhạc đang phát — cả hai phải từ chối. Thử nguồn micro bị rút và bộ nhớ gần đầy trên máy thật.

**Lưu ý:** Các thử nghiệm mã nguồn chỉ phát hiện hồi quy tĩnh/JavaScript, không thay thế biên dịch Android và thử trực tiếp trên máy.


### Chế độ tự kích hoạt một lần (v9.5.5)
Bấm nút xám **BẮT ĐẦU GHI KHI CÓ TÍN HIỆU**, nút chuyển xanh trong lúc chờ. Micro nghe nền nhưng **chưa tạo file WAV**. Sau ~100ms mức âm cao hơn cấu hình, lưu lại ~1 giây trước lúc kích hoạt và tiếp tục ghi liên tục cho đến khi người dùng vuốt từ trái sang phải để Lưu hoặc Không lưu. Chạm lại khi đang chờ để hủy. Không tắt micro/giảm âm xuống dưới ngưỡng sau khi bắt đầu.

Trước khi quay quan trọng, thử vặn PHONES từ MIN lên mức dự định với micro cắm đúng CTIA/adapter. MIN không phải luôn bằng 0; bộ khuếch đại/AGC có thể làm ứng dụng kích hoạt nhầm. Dùng mức âm ổn định, theo dõi dBFS, tránh gần 0 dBFS.
