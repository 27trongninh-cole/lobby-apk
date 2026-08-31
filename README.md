# Melo Ninstaller — bản redesign v2 (1 màn hình, không cuộn)

## Tổng quan luồng
Chọn nhạc + video → xem trước ngay (crop 16:9 lấp đầy, không viền đen) →
"Cài Mod" tự build trên server rồi cài thẳng vào game, không lộ file zip
trung gian nào ra UI. Chi tiết luồng cài/build xem README gốc phần lịch sử
bên dưới — phần này chỉ mô tả redesign UI.

## Bố cục màn hình chính (không cuộn — mọi thứ nằm gọn trong 1 màn hình)
1. **Header gọn**: logo + trạng thái Shizuku (chấm màu) + nút góc phải.
   Nút góc phải hiện là icon Terminal mở **log debug** (bản test). Khi build
   release chính thức, đổi hành vi tại đúng 1 chỗ —
   `MainActivity.onTopRightActionClicked()` — dựa vào `BuildConfig.DEBUG`
   để chuyển sang icon Settings (giao diện sáng/tối, ngôn ngữ, tài khoản —
   CHƯA code, để sau khi có yêu cầu cụ thể).
2. **Khung preview 16:9** — dùng `CropVideoView` (tự viết, xem file cùng tên)
   để giả lập đúng hành vi CSS `object-fit:cover` của web: đo kích thước lớn
   hơn khung rồi để `FrameLayout` cha clip phần dư, crop lấp đầy khung dù
   nguồn là video dọc 9:16, không còn lộ viền đen như `VideoView` mặc định.
   Overlay UI + nút bật/tắt overlay giữ nguyên logic gốc.
3. **2 thẻ chọn Nhạc / Video** dạng compact, bấm mở dialog picker riêng
   (`dialog_picker.xml`) — nhạc là list có nút nghe thử inline
   (`WemListAdapter`), video là lưới 2 cột có thumbnail qua Glide
   (`VideoGridAdapter`), cả hai đều có thanh tìm kiếm lọc theo tên (bỏ dấu).
4. **Khu "Cài gần đây"** — lưu tối đa 3 tổ hợp nhạc+video đã cài thành công
   gần nhất vào `SharedPreferences` (key `melo_ninstaller_prefs`), bấm vào
   1 dòng để tự động chọn lại. **Chỉ áp dụng cho video lấy từ thư viện**
   (có id ổn định) — video tự upload không lưu lại lịch sử vì Uri SAF có
   thể mất quyền đọc sau khi khởi động lại app.
5. **Cụm nút hành động**: "Cài Mod" (chính) + "Cài lại gần nhất" / "Gỡ Mod"
   (phụ, xếp ngang).

## File mới thêm trong bản redesign này
- `CropVideoView.java` — crop video lấp đầy khung, xem doc trong file.
- `WemListAdapter.java`, `VideoGridAdapter.java` — RecyclerView adapter.
- `dialog_picker.xml`, `item_wem_row.xml`, `item_video_grid.xml` — UI picker.
- `dialog_log.xml`, `item_recent_row.xml` — log debug + dòng lịch sử.
- Thêm dependency: `recyclerview`, `constraintlayout`, `glide` (build.gradle).

## Việc CHƯA làm (để sau, đã thống nhất với chủ dự án)
- Màn hình Settings thật (sáng/tối, ngôn ngữ, tài khoản) — chỉ mới chừa chỗ
  (`onTopRightActionClicked()`), chưa code UI/logic thật.
- Chuyển toàn bộ build mod (patch `Music_Login.bnk`, ghép zip) vào chạy
  ngay trên app thay vì gọi server — đã bàn và xác nhận khả thi (thuần thao
  tác buffer nhị phân, không phụ thuộc API riêng của Node), nhưng để làm
  sau khi web hoàn thiện thêm, lúc đó sẽ xem lại `bnkParser.js`/
  `bnkPatcher.js` cùng nhau trước khi port sang Java.

## Build APK bằng GitHub Actions (không cần Android Studio)
1. Push toàn bộ nội dung thư mục này lên 1 repo GitHub (nhánh `main`).
2. Tab **Actions** → workflow build APK → **Run workflow**.
3. Tải APK từ mục **Artifacts** sau khi build xong.
4. Cài vào máy Android test (bật "Cài từ nguồn không xác định" nếu cần).

## Cần trước khi test trên máy thật
1. Cài & chạy Shizuku trên máy test.
2. Cài sẵn game Liên Quân (`com.garena.game.kgvn`).
3. Máy có mạng ổn định (gọi Render để build, load thumbnail qua Glide, load
   video thư viện để preview).

## Việc nên test kỹ
- Video dọc (9:16) từ thư viện lẫn tự upload — kiểm tra `CropVideoView` crop
  đúng, không méo tỉ lệ, không viền đen.
- Dialog picker khi thư viện có nhiều mục (test cuộn trong RecyclerView,
  tìm kiếm có dấu/không dấu).
- "Cài gần đây" sau khi admin xoá/đổi 1 bài nhạc hoặc video đã lưu trong
  lịch sử — app cần báo đúng "không còn trong thư viện" thay vì crash.
- Video tự upload dung lượng lớn (đọc hết vào RAM trước khi gửi multipart).


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

## Bản sửa lỗi v4 (từ phản hồi test thực tế)
1. **Nút "Cấp quyền Shizuku" biến mất** — do neo cùng vị trí với khung
   preview (cả 2 cùng `topToBottomOf logoChip`), preview vẽ sau nên che kín
   nút, chỉ lộ 1 sợi màu mỏng ở viền trên. Đã sửa: preview giờ neo
   `topToBottomOf` chính nút đó — khi nút ẩn, preview tự nhảy lên đúng vị trí
   cũ nhờ ConstraintLayout collapse view GONE, không cần logic riêng.
2. **Nút "Gỡ Mod" lệch sát mép trái** — khi nút "Cài lại gần nhất" đang ẩn
   (GONE), ConstraintLayout tự triệt tiêu margin của cạnh nối tới nó về 0,
   khiến "Gỡ Mod" mất luôn margin trái 18dp. Sửa bằng
   `app:layout_goneMarginStart="18dp"`.
3. **Toggle overlay + chú thích preview lệch về góc trên-trái** — nguyên
   nhân: lúc chuyển khung preview từ `FrameLayout` sang `ConstraintLayout`
   (bản v3), quên đổi `layout_gravity="top|end"` sang
   `app:layout_constraintTop_toTopOf`/`End_toEndOf` — `ConstraintLayout`
   không hiểu `layout_gravity` nên các phần tử đó rơi về (0,0) mặc định. Đã
   thêm constraint đúng, đồng thời đổi sang bên PHẢI theo yêu cầu.
4. **Thumbnail video vẫn không hiện** — thêm timeout 6s cho việc trích khung
   hình (`MediaMetadataRetriever`) vì video mp4 không bật "faststart" (moov
   atom ở cuối file) có thể khiến việc trích xuất từ xa treo rất lâu/vô thời
   hạn. Nếu vẫn không thấy thumbnail sau bản này, nhiều khả năng cần xuất
   lại video bằng `ffmpeg -movflags +faststart` ở phía server/nguồn video.
5. **Thanh playback nhạc** — thêm hẳn dưới khung preview (nút play/pause,
   SeekBar tua được, hiển thị thời gian), nối qua API mới thêm vào
   `preview.html`/`PreviewDecoder` (`getPlaybackState`, `togglePlayPauseWem`,
   `seekWemTo`), polling mỗi 300ms khi Activity đang hiển thị.
6. **Nhạc phát nền sau khi thoát app** — trước chỉ dừng nhạc ở `onDestroy()`
   (Activity bị huỷ hẳn), nhưng bấm Home chỉ gọi `onPause()`/`onStop()`,
   Activity vẫn sống nên nhạc vẫn phát ngầm. Giờ dừng nhạc ngay tại
   `onPause()`.
7. **Giao diện làm mới** — logo thật (`logo_header.png`) thay chữ "M", khung
   preview bo góc thật (`clipToOutline`), gradient CTA đổi tông xanh dương→
   tím thay vì baby-blue phẳng.
