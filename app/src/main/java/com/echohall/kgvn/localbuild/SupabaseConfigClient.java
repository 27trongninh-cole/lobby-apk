package com.echohall.kgvn.localbuild;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.echohall.kgvn.AppConfig;

/**
 * Đọc THẲNG 2 bảng Supabase (bnk_settings, lobby_profiles) bằng anon public
 * key — KHÔNG qua server Node, KHÔNG dùng service_role key. Đây là dữ liệu
 * admin cập nhật thường xuyên nên không hard-code trong app; anon key chỉ có
 * quyền SELECT theo RLS policy được cấp riêng cho 2 bảng này (đọc-only).
 *
 * Yêu cầu ở phía Supabase (1 lần, làm trong SQL editor của dashboard):
 *   alter table bnk_settings enable row level security;
 *   create policy "public read bnk_settings" on bnk_settings
 *     for select using (true);
 *   alter table lobby_profiles enable row level security;
 *   create policy "public read active lobby_profiles" on lobby_profiles
 *     for select using (active = true);
 * (Không cấp insert/update/delete cho anon — chỉ đọc.)
 */
public final class SupabaseConfigClient {

    private static final int TIMEOUT_MS = 20_000;

    private SupabaseConfigClient() {}

    public static class BnkSettings {
        public String bnkUrl;
        public long replacementId;
    }

    public static class LobbyProfile {
        public String name;
        public long sourceId;
        public String videoFilename;
    }

    public static class Config {
        public BnkSettings bnkSettings;
        public List<LobbyProfile> lobbyProfiles;
    }

    /** Ném IllegalStateException rõ ràng nếu chưa điền SUPABASE_URL/ANON_KEY trong AppConfig. */
    private static void requireConfigured() {
        if (AppConfig.SUPABASE_URL == null || AppConfig.SUPABASE_URL.isEmpty()
                || AppConfig.SUPABASE_ANON_KEY == null || AppConfig.SUPABASE_ANON_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "Chưa cấu hình SUPABASE_URL / SUPABASE_ANON_KEY trong AppConfig.java — " +
                    "cần điền trước khi dùng tính năng build nội bộ (nhạc tự tải lên).");
        }
    }

    public static Config fetchConfig() throws Exception {
        requireConfigured();
        Config cfg = new Config();
        cfg.bnkSettings = fetchBnkSettings();
        cfg.lobbyProfiles = fetchActiveLobbyProfiles();
        return cfg;
    }

    private static BnkSettings fetchBnkSettings() throws Exception {
        String url = AppConfig.SUPABASE_URL + "/rest/v1/bnk_settings?id=eq.1&select=bnk_url,replacement_id";
        JSONArray rows = new JSONArray(new String(restGet(url), "UTF-8"));
        if (rows.length() == 0) {
            throw new IllegalStateException("bnk_settings rỗng — vào /admin (web) cấu hình bnk_url + replacement_id trước.");
        }
        JSONObject row = rows.getJSONObject(0);
        BnkSettings s = new BnkSettings();
        s.bnkUrl = row.optString("bnk_url", null);
        s.replacementId = row.optLong("replacement_id", -1);
        if (s.bnkUrl == null || s.bnkUrl.isEmpty()) {
            throw new IllegalStateException("Chưa cấu hình Music_Login.bnk (bnk_url rỗng trong bnk_settings).");
        }
        if (s.replacementId < 0) {
            throw new IllegalStateException("Chưa cấu hình replacement_id trong bnk_settings.");
        }
        return s;
    }

    private static List<LobbyProfile> fetchActiveLobbyProfiles() throws Exception {
        String url = AppConfig.SUPABASE_URL
                + "/rest/v1/lobby_profiles?active=eq.true&select=name,source_id,video_filename&order=created_at.asc";
        JSONArray rows = new JSONArray(new String(restGet(url), "UTF-8"));
        List<LobbyProfile> list = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            LobbyProfile p = new LobbyProfile();
            p.name = row.optString("name", "");
            p.sourceId = row.optLong("source_id", -1);
            p.videoFilename = row.optString("video_filename", "");
            list.add(p);
        }
        if (list.isEmpty()) {
            throw new IllegalStateException("Chưa có sảnh nào đang bật (lobby_profiles active=true rỗng) — vào /admin thêm ít nhất 1 sảnh.");
        }
        return list;
    }

    private static byte[] restGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + AppConfig.SUPABASE_ANON_KEY);
        try {
            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = readAll(is);
            if (code != 200) {
                throw new IOException("Supabase REST lỗi (HTTP " + code + "): " + new String(bytes, "UTF-8"));
            }
            return bytes;
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }
}
