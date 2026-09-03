package com.echohall.kgvn;

/**
 * Cấu hình dùng chung — chỉ 1 nơi khai báo URL server web (Render), để
 * ApiClient (gọi API) và trang "Tạo Mod" (nhúng WebView) luôn trỏ cùng 1
 * chỗ, tránh lệch nhau nếu sau này đổi domain.
 */
public final class AppConfig {
    public static final String WEB_BASE_URL = "https://melodinity.onrender.com";

    // ───────────────────────── Build nội bộ (offline) ─────────────────────────
    // Dùng khi nhạc được TỰ TẢI LÊN + convert ngay trên máy (không có wemId
    // trong thư viện) — lúc đó app tự lấy cấu hình bnk/sảnh thẳng từ Supabase
    // (đọc-only, qua anon public key, KHÔNG đụng gì server Node) rồi tự patch
    // + đóng gói zip, không gọi /api/build.
    //
    // TODO: điền link project Supabase + anon public key ở đây (Settings ->
    // API trong dashboard Supabase). Anon key chỉ có quyền đọc theo RLS policy
    // đã cấp cho bảng bnk_settings/lobby_profiles — KHÔNG phải service key.
    public static final String SUPABASE_URL = "https://rwzrfcoslstiwndivznu.supabase.co";
    public static final String SUPABASE_ANON_KEY = "sb_publishable_leXAB0M5UDz8uwYiwVy8Fg_qhRmwK-b";

    private AppConfig() {}
}
