package com.echohall.kgvn;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client gọi thẳng server Node (đã deploy trên Render) — không đụng gì tới
 * /admin (route đó chỉ dùng qua web, app không cần biết ADMIN_PASSWORD).
 *
 * LƯU Ý QUAN TRỌNG: free tier Render "ngủ" sau ~15 phút không traffic —
 * request đầu tiên có thể mất 20-30s (cold start). Set timeout đủ lớn
 * (xem CONNECT_TIMEOUT_MS/READ_TIMEOUT_MS) và luôn hiện loading rõ ràng
 * trong UI khi gọi API lần đầu trong phiên.
 */
public class ApiClient {

    // Dùng chung với trang "Tạo Mod" (WebView) — xem AppConfig.java.
    private static final String BASE_URL = AppConfig.WEB_BASE_URL;

    private static final int CONNECT_TIMEOUT_MS = 35_000; // chịu cold start Render
    private static final int READ_TIMEOUT_MS = 35_000;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class WemItem {
        public String id;
        public String name;
        public Integer durationMs;
        public List<String> keywords = new ArrayList<>();
    }

    public static class VideoItem {
        public String id;
        public String name;
        // /api/video-list trả thẳng URL công khai (Supabase Storage) — KHÔNG
        // ẩn như wem_url, nên có thể phát trực tiếp trong VideoView để preview.
        public String videoUrl;
        public String thumbnailUrl;
    }

    public static class BuildResult {
        public byte[] zipBytes;
        public String reportJson; // header X-Build-Report, để hiển thị debug nếu cần
    }

    // ───────────────────────── GET /api/wem-list ─────────────────────────
    public void fetchWemList(Callback<List<WemItem>> cb) {
        executor.execute(() -> {
            try {
                String json = httpGet(BASE_URL + "/api/wem-list");
                JSONObject root = new JSONObject(json);
                if (!root.optBoolean("ok", false)) {
                    postError(cb, root.optString("error", "Lỗi không rõ"));
                    return;
                }
                JSONArray arr = root.getJSONArray("wems");
                List<WemItem> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    WemItem w = new WemItem();
                    w.id = o.optString("id");
                    w.name = o.optString("name");
                    if (o.has("duration_ms") && !o.isNull("duration_ms")) {
                        w.durationMs = o.optInt("duration_ms");
                    }
                    JSONArray kw = o.optJSONArray("keywords");
                    if (kw != null) {
                        for (int k = 0; k < kw.length(); k++) w.keywords.add(kw.optString(k));
                    }
                    out.add(w);
                }
                postSuccess(cb, out);
            } catch (Exception e) {
                postError(cb, describeError(e));
            }
        });
    }

    // ──────────────────────── GET /api/video-list ────────────────────────
    public void fetchVideoList(Callback<List<VideoItem>> cb) {
        executor.execute(() -> {
            try {
                String json = httpGet(BASE_URL + "/api/video-list");
                JSONObject root = new JSONObject(json);
                if (!root.optBoolean("ok", false)) {
                    postError(cb, root.optString("error", "Lỗi không rõ"));
                    return;
                }
                JSONArray arr = root.getJSONArray("videos");
                List<VideoItem> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    VideoItem v = new VideoItem();
                    v.id = o.optString("id");
                    v.name = o.optString("name");
                    // optString() của org.json trả về CHUỖI CHỮ "null" (không
                    // phải Java null) khi field tồn tại nhưng có giá trị JSON
                    // null — nếu không lọc lại, "null" bị coi là 1 URL thật,
                    // Glide/MediaMetadataRetriever báo MalformedURLException
                    // "no protocol: null". Dùng optNullableString() bên dưới
                    // để trả đúng Java null trong trường hợp đó.
                    v.videoUrl = optNullableString(o, "video_url");
                    v.thumbnailUrl = optNullableString(o, "thumbnail_url");
                    out.add(v);
                }
                postSuccess(cb, out);
            } catch (Exception e) {
                postError(cb, describeError(e));
            }
        });
    }

    // ───────────────────── GET /api/wem-preview/:id ──────────────────────
    // Trả về bytes .wem thô — Android tự đưa qua WebView ẩn (wemogg.js) để
    // decode ra .ogg rồi phát, xem PreviewDecoder.
    public void fetchWemPreviewBytes(String wemId, Callback<byte[]> cb) {
        executor.execute(() -> {
            try {
                byte[] bytes = httpGetBytes(BASE_URL + "/api/wem-preview/" + wemId);
                postSuccess(cb, bytes);
            } catch (Exception e) {
                postError(cb, describeError(e));
            }
        });
    }

    // ───────────────────────── POST /api/build ───────────────────────────
    // videoId HOẶC videoFileBytes(+videoFileName) — chỉ truyền 1 trong 2,
    // giống rule server: "Cần chọn videoId từ thư viện hoặc upload file video".
    public void build(String wemId, String videoId, byte[] videoFileBytes, String videoFileName,
                       Callback<BuildResult> cb) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String boundary = "----EchohallBoundary" + UUID.randomUUID();
                URL url = new URL(BASE_URL + "/api/build");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                try (OutputStream os = conn.getOutputStream()) {
                    writeFormField(os, boundary, "wemId", wemId);
                    if (videoId != null) {
                        writeFormField(os, boundary, "videoId", videoId);
                    } else if (videoFileBytes != null) {
                        writeFormFile(os, boundary, "video", videoFileName == null ? "video.mp4" : videoFileName, videoFileBytes);
                    }
                    os.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                if (code != 200) {
                    String err = readAll(conn.getErrorStream());
                    postError(cb, "Build thất bại (HTTP " + code + "): " + err);
                    return;
                }

                String reportHeader = conn.getHeaderField("X-Build-Report");
                byte[] zipBytes = readAllBytes(conn.getInputStream());

                BuildResult result = new BuildResult();
                result.zipBytes = zipBytes;
                result.reportJson = reportHeader;
                postSuccess(cb, result);
            } catch (Exception e) {
                postError(cb, describeError(e));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ──────────────────────────── helpers ────────────────────────────────

    private String httpGet(String urlStr) throws IOException {
        return new String(httpGetBytes(urlStr), "UTF-8");
    }

    private byte[] httpGetBytes(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = readAllBytes(is);
            if (code != 200) {
                throw new IOException("HTTP " + code + ": " + new String(bytes, "UTF-8"));
            }
            return bytes;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * optString() của org.json trả về chuỗi CHỮ "null" thay vì Java null khi
     * field tồn tại với giá trị JSON null (khác với field hoàn toàn không có
     * — trường hợp đó optString mới trả về đúng null/fallback). Hàm này lọc
     * lại cả 2 trường hợp về đúng 1 kết quả: Java null.
     */
    private static String optNullableString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, null);
        return (v == null || "null".equals(v) || v.isEmpty()) ? null : v;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    private static String readAll(InputStream is) {
        try {
            return new String(readAllBytes(is), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeFormField(OutputStream os, String boundary, String name, String value) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        os.write((value == null ? "" : value).getBytes("UTF-8"));
        os.write("\r\n".getBytes("UTF-8"));
    }

    private static void writeFormFile(OutputStream os, String boundary, String fieldName, String fileName, byte[] data) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes("UTF-8"));
        os.write("Content-Type: application/octet-stream\r\n\r\n".getBytes("UTF-8"));
        os.write(data);
        os.write("\r\n".getBytes("UTF-8"));
    }

    private static String describeError(Exception e) {
        // java.net.SocketTimeoutException trên free tier Render thường là cold
        // start — chú thích rõ để UI hiển thị thông báo đúng, không phải lỗi code.
        if (e instanceof java.net.SocketTimeoutException) {
            return "Server phản hồi chậm (có thể đang khởi động lại sau thời gian ngủ) — thử lại sau ít giây.";
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private <T> void postSuccess(Callback<T> cb, T result) {
        mainHandler.post(() -> cb.onSuccess(result));
    }

    private <T> void postError(Callback<T> cb, String message) {
        mainHandler.post(() -> cb.onError(message));
    }
}
