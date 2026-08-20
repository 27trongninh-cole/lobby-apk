# Echohall Installer — bản khung test (giai đoạn: chỉ cài mod)

## Phạm vi bản này
APK này CHỈ làm nhiệm vụ cài/gỡ mod từ file zip đã tải sẵn từ web
(theo quyết định đã thống nhất: tạo mod vẫn làm trên web, APK chỉ cài).

`ApiClient.java` và `PreviewDecoder.java` (gọi API web, nghe thử nhạc) đã
viết khung sẵn từ trước nhưng **CHƯA được nối vào MainActivity** — vì web
báo chưa hoàn thiện, tạm gác lại tính năng đó. Không xoá 2 file này vì sẽ
dùng lại khi quay lại giai đoạn "build mod trong app".

## Đã hoạt động trong bản này
- Xin quyền Shizuku (`ShizukuPermissionHelper`)
- Chọn file zip mod bằng Storage Access Framework
- **Xem trước mod ngay sau khi chọn zip** (`ModPreviewLocator`): giải nén
  tạm, tìm file `.wem` đầu tiên và file video đầu tiên trong zip, phát
  video loop tắt tiếng + audio giải mã từ `.wem` (qua WebView ẩn chạy
  `wemogg.js` — `PreviewDecoder`) phát riêng, đồng bộ gần đúng — có toggle
  bật/tắt overlay UI mô phỏng giống trên web. KHÔNG đụng đến file trong
  game, chỉ đọc từ zip đã chọn.
- Giải nén zip, tự suy đường dẫn đích từ cấu trúc thư mục trong zip
  (không cần khai báo trước danh sách file)
- Backup từng file bị ghi đè bằng rename `<tên gốc> -> <tên gốc>.nins`,
  quyết định dựa vào trạng thái filesystem thật (không phụ thuộc hoàn
  toàn vào 1 manifest nội bộ dễ mất khi xoá data app)
- Nhận diện riêng file mới cố định tên `30082005.wem` (không có gốc để
  backup) để xoá thẳng khi gỡ mod — áp dụng cả lúc CÀI (không backup nhầm
  file của chính app từ lần cài trước) lẫn lúc GỠ
- "Fix ISPDiff" 1 lần duy nhất (`IspdiffFixer`) — khắc phục việc Android
  chặn ghi file media (.mp4) không-root trong `Android/data/`, bằng cách
  tạo lại thư mục do chính app sở hữu
- Gỡ toàn bộ mod: khôi phục ISPDiff nguyên khối trước, sau đó quét/khôi
  phục file lẻ ở phần còn lại — có loại trừ vùng đã xử lý xong để tránh
  đụng lại

## UI
Chỉ có **1 Activity duy nhất** (`MainActivity`), theme **Nod-Krai** (xanh
băng, lấy đúng bảng màu từ CSS `[data-theme="snezhnaya"]` trên web) —
nút pill bo tròn, card viền mảnh, tái hiện ngôn ngữ thiết kế của web.

## Build APK bằng GitHub Actions (không cần Android Studio)

1. Push toàn bộ nội dung thư mục này lên 1 repo GitHub (nhánh `main` hoặc `master`).
2. Vào tab **Actions** trên GitHub → chọn workflow **"Build debug APK"** →
   bấm **Run workflow** (hoặc tự chạy khi bạn push code, vì workflow đã
   set trigger cả `push`).
3. Đợi build xong (vài phút) → vào lượt chạy đó → mục **Artifacts** ở cuối
   trang → tải file `echohall-installer-debug-apk` (đây là file `.apk`,
   nén trong 1 zip do GitHub tự đóng gói artifact).
4. Cài file `.apk` đó vào máy Android test (nhớ bật "Cài từ nguồn không
   xác định" nếu máy chặn mặc định) — đây là bản debug, ký bằng debug
   key mặc định của Android Gradle Plugin, không cần bạn tự tạo keystore
   cho bản test.

**Vì sao workflow không dùng `./gradlew`:** repo hiện chưa có sẵn
`gradle/wrapper/gradle-wrapper.jar` (file nhị phân, không tạo được lúc
soạn code ở môi trường không có mạng). Workflow dùng action
`gradle/actions/setup-gradle` để cài thẳng Gradle 8.5 lên runner GitHub
và chạy lệnh `gradle` trực tiếp — không ảnh hưởng gì đến kết quả build,
chỉ khác cách gọi. Nếu muốn dùng `./gradlew` như dự án Android chuẩn,
bạn có thể tự chạy `gradle wrapper` một lần trên máy có cài Gradle rồi
commit thư mục `gradle/wrapper/` vào repo, sau đó sửa bước cuối trong
`.github/workflows/build.yml` thành `./gradlew assembleDebug`.


## Vì sao KHÔNG dùng `Environment.getExternalStorageDirectory()` là hack
Đường dẫn này chỉ được ĐƯA VÀO LỆNH SHELL chạy qua Shizuku (quyền shell,
nằm ngoài sandbox scoped storage) — app không tự mở file bằng
`File`/`FileInputStream` trực tiếp vào đó, nên không bị chặn bởi scoped
storage của Android 11+.

## CẦN LÀM TRƯỚC KHI TEST TRÊN MÁY THẬT
1. Cài & chạy Shizuku trên máy test (theo hướng dẫn chính thức tại
   shizuku.rikka.app, mục "Start via adb") — APK không tự khởi động
   Shizuku được, đây là bước thủ công (trừ khi máy có root và dùng
   Magisk module thì Shizuku tự khởi động cùng máy).
2. Cài sẵn game Liên Quân (`com.garena.game.kgvn`) trên máy test, để có
   sẵn thư mục `Android/data/com.garena.game.kgvn/files/...` cho app ghi vào.
3. Cài file `.apk` tải từ Artifacts vào máy.

## Danh sách việc nên test kỹ trên máy thật (biết trước sẽ cần tối ưu)
- Thời gian giải nén + cài khi zip có nhiều file cùng lúc (hiện chạy
  tuần tự từng file qua Shizuku — có thể chậm nếu 1 lượt mod chứa vài
  chục file, khi đó cân nhắc gộp nhiều lệnh cp/mv thành 1 lượt gọi
  Shizuku thay vì gọi riêng từng file).
- Hành vi khi Shizuku bị mất kết nối giữa chừng lúc đang cài (app hiện
  chỉ bắt exception rồi báo lỗi, dừng — các file đã cài trước đó vẫn
  đúng trạng thái, phần chưa cài thì thôi, không có file dở dang).
- Test gỡ mod sau khi xoá data app (kiểm tra đúng cơ chế "nguồn sự thật
  là filesystem" đã thiết kế — xoá data app rồi thử gỡ mod, xem có khôi
  phục đúng không dù manifest nội bộ đã mất).

## Ý tưởng để SAU (chưa làm)
- Tự kết nối ADB (wireless debugging) ngay trong app, bỏ phụ thuộc app
  Shizuku ngoài — về mặt kỹ thuật khả thi (Shizuku mã nguồn mở, có thể
  tham khảo lại logic tương tự) nhưng là 1 hạng mục lớn (tự làm client
  ADB, xử lý pairing/key/kết nối bền vững), không phải việc nhỏ. Chỉ làm
  sau khi luồng cài/gỡ mod qua Shizuku đã ổn định hoàn toàn qua nhiều máy
  test.
