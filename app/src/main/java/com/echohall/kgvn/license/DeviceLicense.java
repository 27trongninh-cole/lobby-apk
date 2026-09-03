package com.echohall.kgvn.license;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.echohall.kgvn.AppConfig;

/**
 * "Device-based Manual License Activation": mỗi thiết bị có 1 Device ID cố
 * định — TỰ SINH 1 lần (UUID random, KHÔNG dùng ANDROID_ID). Người dùng gửi
 * Device ID này cho admin (qua Zalo/Discord ngoài app); admin tự tay thêm 1
 * dòng vào bảng "device_licenses" trên Supabase (qua trang /admin —
 * service_role, không qua app). App CHỈ ĐỌC bảng này bằng anon key để kiểm
 * tra device_id của MÌNH có đang active hay không — không có đường ghi/tự
 * kích hoạt nào từ app, nên không ai tự mở khoá được nếu admin chưa duyệt.
 *
 * LƯU Ý QUAN TRỌNG — vì sao KHÔNG dùng ANDROID_ID: theo tài liệu Android,
 * ANDROID_ID đổi khi CHỮ KÝ KÝ APK đổi. App này build qua GitHub Actions,
 * chưa có keystore cố định commit vào repo -> MỖI LẦN CI build lại là 1 chữ
 * ký khác -> nếu dùng ANDROID_ID, Device ID sẽ đổi liên tục mỗi lần cập
 * nhật app (dù không hề gỡ cài đặt), làm vô hiệu license đã duyệt. Dùng UUID
 * tự sinh + lưu SharedPreferences thay vì ANDROID_ID để tránh hẳn vấn đề
 * này — chỉ đổi khi gỡ hẳn app hoặc xoá dữ liệu app (đúng như kỳ vọng
 * "theo thiết bị", không bị ảnh hưởng bởi việc build lại APK).
 *
 * Kích hoạt xong -> cache local vĩnh viễn (không hết hạn, không cần check
 * mạng lại mỗi lần mở app) — đúng yêu cầu "kích hoạt 1 lần dùng vĩnh viễn".
 */
public final class DeviceLicense {

    private static final String PREFS_NAME = "device_license";
    private static final String KEY_ACTIVATED = "activated";
    private static final String KEY_ACTIVATED_FOR_DEVICE_ID = "activated_for_device_id";
    private static final String KEY_GENERATED_DEVICE_ID = "generated_device_id";
    private static final int TIMEOUT_MS = 20_000;

    private DeviceLicense() {}

    /**
     * UUID tự sinh 1 LẦN DUY NHẤT cho máy này, lưu vĩnh viễn trong
     * SharedPreferences — không phụ thuộc chữ ký ký APK, không đổi khi cập
     * nhật/build lại app, chỉ đổi khi gỡ cài đặt hẳn hoặc xoá dữ liệu app.
     */
    public static String getDeviceId(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_GENERATED_DEVICE_ID, null);
        if (existing != null && !existing.isEmpty()) return existing;

        String fresh = java.util.UUID.randomUUID().toString();
        prefs.edit().putString(KEY_GENERATED_DEVICE_ID, fresh).apply();
        return fresh;
    }

    /** Đã kích hoạt sẵn trong cache local (đúng device hiện tại) chưa — dùng để bật/khoá UI ngay, không cần chờ mạng. */
    public static boolean isCachedActivated(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean activated = prefs.getBoolean(KEY_ACTIVATED, false);
        String cachedDeviceId = prefs.getString(KEY_ACTIVATED_FOR_DEVICE_ID, null);
        return activated && getDeviceId(ctx).equals(cachedDeviceId);
    }

    private static void setCachedActivated(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVATED, true)
                .putString(KEY_ACTIVATED_FOR_DEVICE_ID, getDeviceId(ctx))
                .apply();
    }

    public static class CheckResult {
        public boolean activated;
        public String message; // để hiện cho người dùng, không phải log kỹ thuật
    }

    /**
     * Gọi mạng — chạy trong background thread, KHÔNG gọi ở main thread.
     * Chỉ đọc (SELECT) bảng device_licenses ở đúng device_id hiện tại.
     */
    public static CheckResult checkActivationOnline(Context ctx) {
        CheckResult res = new CheckResult();
        try {
            if (AppConfig.SUPABASE_URL == null || AppConfig.SUPABASE_URL.isEmpty()
                    || AppConfig.SUPABASE_ANON_KEY == null || AppConfig.SUPABASE_ANON_KEY.isEmpty()) {
                res.activated = false;
                res.message = "Chưa cấu hình Supabase trong app (AppConfig.java).";
                return res;
            }

            String deviceId = getDeviceId(ctx);
            String url = AppConfig.SUPABASE_URL + "/rest/v1/device_licenses?device_id=eq."
                    + java.net.URLEncoder.encode(deviceId, "UTF-8") + "&select=status&limit=1";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + AppConfig.SUPABASE_ANON_KEY);

            int code;
            byte[] bytes;
            try {
                code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                bytes = readAll(is);
            } finally {
                conn.disconnect();
            }

            if (code != 200) {
                res.activated = false;
                res.message = "Lỗi kiểm tra (HTTP " + code + ") — thử lại sau.";
                return res;
            }

            JSONArray rows = new JSONArray(new String(bytes, "UTF-8"));
            if (rows.length() == 0) {
                res.activated = false;
                res.message = "Device ID này CHƯA được duyệt. Gửi Device ID ở trên cho admin để xin kích hoạt.";
                return res;
            }

            JSONObject row = rows.getJSONObject(0);
            String status = row.optString("status", "");
            if ("active".equals(status)) {
                setCachedActivated(ctx);
                res.activated = true;
                res.message = "✓ Đã kích hoạt! Tính năng tự tải nhạc lên đã mở khoá vĩnh viễn trên máy này.";
            } else {
                res.activated = false;
                res.message = "Device ID này đã bị khoá (status=" + status + "). Liên hệ admin nếu có nhầm lẫn.";
            }
            return res;
        } catch (Exception e) {
            res.activated = false;
            res.message = "Lỗi kiểm tra: " + e.getMessage();
            return res;
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }
}
