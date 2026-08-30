# Melo Ninstaller — bản "cài trực tiếp" (không qua zip/WebView)

## Thay đổi so với bản trước
Bản trước có 2 tab: "Cài Mod" (native, chọn file .zip đã tải sẵn từ web) và
"Tạo Mod" (nhúng WebView load thẳng trang web builder — đây là phần gây lag,
vì phải cold-start Chromium + tải cả trang web).

Bản này **gộp làm 1 màn hình, không còn zip và không còn WebView**:
1. Người dùng chọn 1 bài nhạc (danh sách từ `/api/wem-list`, nghe thử qua
   `/api/wem-preview/:id`) và 1 video (từ thư viện `/api/video-list`, hoặc tự
   upload từ máy).
2. Xem trước ngay trong app: video phát câm tiếng (loop) + nhạc phát riêng từ
   `.wem` đã giải mã — y hệt cơ chế preview trên web.
3. Bấm "📦 Cài Mod" → app tự gọi `POST /api/build` (multipart: `wemId` +
   `videoId` hoặc file video) → nhận về zip bytes trong bộ nhớ → lưu tạm vào
   cache app (`last_build.zip`) → đưa THẲNG vào `ModInstaller.installFromZip()`
   để cài — người dùng không hề thấy bước file zip nào, không cần chọn file gì
   thêm.
4. Nút "🔁 Cài lại bản gần nhất" dùng lại `last_build.zip` trong cache, không
   gọi lại server — hữu ích nếu muốn cài lại nhanh hoặc cài giữa chừng bị lỗi.
5. "🗑️ Gỡ tất cả Mod" giữ nguyên logic cũ, không đổi gì.

`ApiClient.java` (gọi API web) và các phần cài/gỡ mod thật
(`ModInstaller`/`ModBackupManager`/`IspdiffFixer`) đều **giữ nguyên logic**,
chỉ khác nguồn `Uri` đưa vào `installFromZip()` — trước là file zip user tự
chọn qua SAF, giờ là file zip build ra nằm trong cache riêng của app.

Đã xoá `MainActivityWebStyle.java`, `ModPreviewLocator.java` và layout
`activity_main_web_style.xml` (không còn dùng — luồng cũ đọc file/wem/video
*bên trong 1 zip đã có sẵn*; giờ nhạc/video được chọn từ trước lúc build nên
không cần dò tìm trong zip nữa).

## UI
Vẫn 1 Activity duy nhất (`MainActivity`), theme Nod-Krai (xanh băng) — pill bo
tròn, card viền mảnh, style y hệt bản trước.

## Build APK bằng GitHub Actions (không cần Android Studio)
Giữ nguyên hướng dẫn bản gốc:
1. Push toàn bộ nội dung thư mục này lên 1 repo GitHub (nhánh `main`).
2. Tab **Actions** → workflow **"Build debug APK"** → **Run workflow** (hoặc
   tự chạy khi push).
3. Đợi build xong → mục **Artifacts** → tải `echohall-installer-debug-apk`
   (1 file zip do GitHub tự đóng gói, bên trong là `.apk`).
4. Cài vào máy Android test (bật "Cài từ nguồn không xác định" nếu cần).

## CẦN LÀM TRƯỚC KHI TEST TRÊN MÁY THẬT
1. Cài & chạy Shizuku trên máy test (shizuku.rikka.app, mục "Start via adb").
2. Cài sẵn game Liên Quân (`com.garena.game.kgvn`) trên máy test.
3. Cài file `.apk` tải từ Artifacts vào máy.
4. Máy test có mạng ổn định — bấm "Cài Mod" sẽ gọi server Render
   (`melodinity.onrender.com`) để build zip trước khi cài, cần vài giây tới
   vài chục giây tuỳ dung lượng video.

## Danh sách việc nên test kỹ
- Video tự upload dung lượng lớn (đọc hết vào RAM dưới dạng `byte[]` trước
  khi gửi multipart — cân nhắc giới hạn dung lượng hoặc stream trực tiếp nếu
  gặp OutOfMemory với video quá nặng).
- Lỗi nghiệp vụ từ server (bài nhạc chưa có `duration_ms`, chưa có sảnh nào
  đang bật...) — cần hiện đúng thông báo, đã có sẵn qua `ApiClient` trả về
  `message` là `error` gốc từ server.
- Preview video từ thư viện dùng thẳng `video_url` public (stream qua mạng),
  khác với trước đây tải cả file về máy để đọc — cần mạng ổn định lúc xem
  trước, nếu mạng yếu video có thể load chậm/giật (không ảnh hưởng chất lượng
  file cài thật, chỉ ảnh hưởng preview).
- Cài lại bản gần nhất sau khi tắt/mở lại app (cache app có bị hệ thống dọn
  giữa chừng không, tuỳ chính sách cache của từng máy).

## Ý tưởng để SAU (chưa làm)
- Upload nhạc (.wav) rồi tự convert sang .wem ngay trong app/server (hiện tại
  vẫn phải quản lý bài nhạc sẵn trong thư viện admin).
- Giới hạn/nén video tự upload trước khi gửi lên server để giảm thời gian chờ
  build với video dung lượng lớn.
