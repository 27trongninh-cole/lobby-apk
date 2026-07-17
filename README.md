# AOV Map Mod — Android (Native Compose + Chaquopy)

## Cách build APK (không cần Android Studio)

1. Tạo repo GitHub mới, push toàn bộ thư mục này lên (`main` branch)
2. Vào tab **Actions** trên GitHub → workflow "Build APK" sẽ tự chạy
   (hoặc bấm **Run workflow** để chạy thủ công)
3. Sau khi build xong (~5-10 phút lần đầu), vào job vừa chạy → mục
   **Artifacts** → tải file `aov-map-mod-debug-apk.zip` → giải nén ra file `.apk`
4. Copy file `.apk` vào điện thoại, bật "Cài từ nguồn không xác định" → cài đặt

## Việc còn thiếu cần làm tiếp

- [ ] Chuyển logic `rebuild_bundle()` trong `app.py` thành hàm Python thuần
  (bỏ phần Flask route `@app.route`, giữ lại phần xử lý UnityPy/PIL) → đặt vào
  `app/src/main/python/rebuild_engine.py`
- [ ] Copy `AssetbundleUtils/UnityPy_AOV`, `texture_naming.py`, `config.py`
  vào `app/src/main/python/`
- [ ] Kiểm tra UnityPy_AOV có phần compiled/native riêng không — nếu có,
  cần build lại cho `arm64-v8a`/`armeabi-v7a` (đây là rủi ro lớn nhất)
- [ ] Viết màn hình lưới 16 ô nền map bằng `LazyVerticalGrid` + Coil load ảnh
  từ URL Supabase Storage (thay cho `/api/texture/<level>/<filename>`)
- [ ] Viết màn hình upload (file picker Android) + gọi `pyRebuildEngine`
- [ ] Test file `.unity3d` gốc + textures tải từ Supabase Storage về máy
  trước khi rebuild

## Cấu trúc thư mục
```
app/src/main/java/...    → code Kotlin (UI Compose)
app/src/main/python/      → code Python (rebuild engine, chạy qua Chaquopy)
app/build.gradle          → khai báo Chaquopy pip install + Compose deps
.github/workflows/        → CI tự build APK
```
