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
 * "Máy giải mã + phát nhạc preview" chạy bằng 1 WebView ẩn (visibility=GONE)
 * load assets/preview.html (include wemogg.js — port JS thuần của ww2ogg).
 * Người dùng không thấy WebView này — nó không phải UI hiển thị, chỉ là
 * "công cụ" tái dùng code JS đã chạy ổn trên bản web.
 *
 * QUAN TRỌNG: giải mã .wem -> Ogg VÀ PHÁT NHẠC đều xảy ra ngay TRONG
 * WebView (qua thẻ <audio>), KHÔNG trả bytes .ogg ra native MediaPlayer.
 * Lý do: Ogg do wemToOgg() tự dựng lại container có thể đủ hợp lệ để
 * trình duyệt phát (khoan dung với sai lệch nhẹ) nhưng KHÔNG đủ chuẩn để
 * Android MediaPlayer chấp nhận toàn bộ — quan sát thực tế: phát được vài
 * giây đầu rồi lỗi MEDIA_ERROR_UNKNOWN giữa chừng. Giữ nguyên trong cùng 1
 * engine (trình duyệt) loại bỏ hẳn phụ thuộc vào độ khắt khe của
 * MediaPlayer native.
 *
 * Cách dùng:
 *   PreviewDecoder decoder = new PreviewDecoder(activity, rootViewGroup);
 *   decoder.playWem(wemBytes, new PreviewDecoder.PlaybackCallback() {
 *       public void onPlaybackStarted() { ... }
 *       public void onError(String message) { ... }
 *   });
 *   ...
 *   decoder.stopAudio();
 *
 * Gọi decoder.destroy() trong onDestroy() của Activity để giải phóng WebView.
 */
public class PreviewDecoder {

    public interface PlaybackCallback {
        void onPlaybackStarted();
        void onError(String message);
    }

    private final WebView webView;
    private PlaybackCallback pendingCallback; // chỉ hỗ trợ 1 request tại 1 thời điểm — đủ dùng cho "nghe thử"

    @SuppressLint("SetJavaScriptEnabled")
    public PreviewDecoder(Context context, ViewGroup attachTo) {
        webView = new WebView(context);
        webView.setVisibility(View.GONE); // KHÔNG hiển thị — chỉ dùng làm engine JS + phát audio ẩn
        webView.getSettings().setJavaScriptEnabled(true);
        // Mặc định WebView chặn tự động phát media có âm thanh nếu không có
        // "cử chỉ người dùng" trực tiếp trong chính WebView — ở đây lệnh phát
        // đến từ native (evaluateJavascript), không tính là cử chỉ người dùng
        // theo con mắt của WebView, nên PHẢI tắt yêu cầu này, nếu không
        // audio.play() sẽ bị từ chối âm thầm (promise reject).
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/preview.html");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        attachTo.addView(webView, lp);
    }

    /** Giải mã .wem -> Ogg rồi phát ngay trong WebView (loop). */
    public void playWem(byte[] wemBytes, PlaybackCallback callback) {
        this.pendingCallback = callback;
        String b64 = Base64.encodeToString(wemBytes, Base64.NO_WRAP);
        webView.post(() -> webView.evaluateJavascript("playWemBase64('" + b64 + "')", null));
    }

    public void stopAudio() {
        webView.post(() -> webView.evaluateJavascript("stopWemAudio()", null));
    }

    public void destroy() {
        webView.destroy();
    }

    /** Trạng thái playback hiện tại — dùng để cập nhật SeekBar native. */
    public static class PlaybackState {
        public boolean hasSrc;
        public boolean playing;
        public double currentTimeSec;
        public double durationSec;
    }

    public interface StateCallback {
        void onState(PlaybackState state);
    }

    /** Hỏi trạng thái playback hiện tại (gọi định kỳ ~300ms để cập nhật SeekBar). */
    public void queryPlaybackState(StateCallback callback) {
        webView.post(() -> webView.evaluateJavascript("getPlaybackState()", json -> {
            try {
                // evaluateJavascript trả về chuỗi JSON đã escape thêm 1 lớp (ví dụ
                // \"key\":\"value\") — cần unescape trước khi parse bằng org.json.
                String unescaped = json == null ? "{}" : json;
                if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                    unescaped = unescaped.substring(1, unescaped.length() - 1)
                            .replace("\\\"", "\"").replace("\\\\", "\\");
                }
                org.json.JSONObject obj = new org.json.JSONObject(unescaped);
                PlaybackState state = new PlaybackState();
                state.hasSrc = obj.optBoolean("hasSrc", false);
                state.playing = obj.optBoolean("playing", false);
                state.currentTimeSec = obj.optDouble("currentTime", 0);
                state.durationSec = obj.optDouble("duration", 0);
                callback.onState(state);
            } catch (Exception e) {
                PlaybackState empty = new PlaybackState();
                callback.onState(empty);
            }
        }));
    }

    public void togglePlayPause() {
        webView.post(() -> webView.evaluateJavascript("togglePlayPauseWem()", null));
    }

    public void seekTo(double seconds) {
        webView.post(() -> webView.evaluateJavascript("seekWemTo(" + seconds + ")", null));
    }

    private class Bridge {
        @JavascriptInterface
        public void onPlaybackStarted() {
            PlaybackCallback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                webView.post(cb::onPlaybackStarted);
            }
        }

        @JavascriptInterface
        public void onError(String message) {
            PlaybackCallback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                webView.post(() -> cb.onError(message));
            }
        }
    }
}
