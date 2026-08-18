package com.echohall.kgvn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * "Máy giải mã" .wem -> .ogg chạy bằng 1 WebView ẩn (visibility=GONE) load
 * assets/preview.html, vốn chỉ include wemogg.js (port JS thuần của
 * ww2ogg, không phụ thuộc DOM) + cầu nối JS. Người dùng không thấy WebView
 * này — nó không phải UI, chỉ là "công cụ tính toán" tái dùng code JS đã
 * chạy ổn trên bản web, tránh phải port thuật toán Vorbis/RIFF sang Kotlin
 * (rất dễ sai offset).
 *
 * Cách dùng:
 *   PreviewDecoder decoder = new PreviewDecoder(activity, rootViewGroup);
 *   decoder.decode(wemBytes, new PreviewDecoder.Callback() {
 *       public void onOggReady(byte[] oggBytes) { ... phát bằng MediaPlayer ... }
 *       public void onError(String message) { ... }
 *   });
 *
 * Gọi decoder.destroy() trong onDestroy() của Activity để giải phóng WebView.
 */
public class PreviewDecoder {

    public interface Callback {
        void onOggReady(byte[] oggBytes);
        void onError(String message);
    }

    private final WebView webView;
    private Callback pendingCallback; // chỉ hỗ trợ 1 request tại 1 thời điểm — đủ dùng cho "nghe thử"

    @SuppressLint("SetJavaScriptEnabled")
    public PreviewDecoder(Context context, ViewGroup attachTo) {
        webView = new WebView(context);
        webView.setVisibility(View.GONE); // KHÔNG hiển thị — chỉ dùng làm engine JS
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/preview.html");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        attachTo.addView(webView, lp);
    }

    public void decode(byte[] wemBytes, Callback callback) {
        this.pendingCallback = callback;
        String b64 = Base64.encodeToString(wemBytes, Base64.NO_WRAP);
        // Escape an toàn: base64 chỉ gồm [A-Za-z0-9+/=], không cần escape thêm.
        webView.post(() -> webView.evaluateJavascript("decodeWemBase64('" + b64 + "')", null));
    }

    public void destroy() {
        webView.destroy();
    }

    private class Bridge {
        @JavascriptInterface
        public void onOggReady(String oggBase64) {
            byte[] oggBytes = Base64.decode(oggBase64, Base64.NO_WRAP);
            Callback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                webView.post(() -> cb.onOggReady(oggBytes));
            }
        }

        @JavascriptInterface
        public void onError(String message) {
            Callback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                webView.post(() -> cb.onError(message));
            }
        }
    }
}
