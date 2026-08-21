package com.echohall.kgvn;

/**
 * Cấu hình dùng chung — chỉ 1 nơi khai báo URL server web (Render), để
 * ApiClient (gọi API) và trang "Tạo Mod" (nhúng WebView) luôn trỏ cùng 1
 * chỗ, tránh lệch nhau nếu sau này đổi domain.
 */
public final class AppConfig {
    public static final String WEB_BASE_URL = "https://melodinity.onrender.com";

    private AppConfig() {}
}
