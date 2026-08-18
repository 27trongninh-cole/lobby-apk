package com.echohall.kgvn;

import android.app.Activity;
import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

/**
 * Quản lý toàn bộ vòng đời quyền Shizuku: kiểm tra binder có sẵn không
 * (tức app Shizuku đã chạy chưa), xin quyền, và báo trạng thái ra ngoài
 * qua listener để UI (MainActivity) tự quyết định hiển thị gì.
 *
 * KHÔNG dùng route "chạy code" nào ở đây — chỉ xin quyền. Việc thực thi
 * lệnh mv/cp thật sự nằm ở ModBackupManager, tách riêng cho rõ trách nhiệm.
 */
public class ShizukuPermissionHelper {

    public static final int REQUEST_CODE = 9001;

    public enum State {
        // Shizuku app chưa cài, hoặc service chưa chạy (chưa pair qua ADB/root)
        BINDER_NOT_AVAILABLE,
        // Binder có sẵn, nhưng app này chưa được cấp quyền
        PERMISSION_DENIED,
        // Đã có quyền, sẵn sàng dùng
        GRANTED
    }

    public interface Listener {
        void onStateChanged(State state);
    }

    private final Activity activity;
    private final Listener listener;

    // Giữ tham chiếu để unregister trong onDestroy(), tránh leak listener
    // vào Shizuku (nó là static event bus toàn app).
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;
                notifyState(grantResult == PackageManager.PERMISSION_GRANTED
                        ? State.GRANTED : State.PERMISSION_DENIED);
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            () -> checkAndNotify();

    private final Shizuku.OnBinderDeadListener binderDeadListener =
            () -> notifyState(State.BINDER_NOT_AVAILABLE);

    public ShizukuPermissionHelper(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
    }

    /** Gọi trong onDestroy() của Activity để tránh leak. */
    public void unregister() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
    }

    /** Gọi khi mở màn hình cài đặt, để cập nhật trạng thái hiện tại ngay lập tức. */
    public void checkAndNotify() {
        if (!Shizuku.pingBinder()) {
            notifyState(State.BINDER_NOT_AVAILABLE);
            return;
        }
        if (Shizuku.isPreV11()) {
            // Bản Shizuku quá cũ — API mà app này dùng (Shizuku.newProcess qua
            // reflection) cần bản v11+ trở lên. Coi như chưa sẵn sàng.
            notifyState(State.BINDER_NOT_AVAILABLE);
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            notifyState(State.GRANTED);
        } else {
            notifyState(State.PERMISSION_DENIED);
        }
    }

    /**
     * Bắt đầu xin quyền. Chỉ gọi khi state hiện tại là PERMISSION_DENIED —
     * nếu BINDER_NOT_AVAILABLE thì xin quyền vô nghĩa (chưa có gì để xin),
     * cần hướng dẫn user mở app Shizuku và start service trước.
     */
    public void requestPermission() {
        if (!Shizuku.pingBinder()) {
            notifyState(State.BINDER_NOT_AVAILABLE);
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            notifyState(State.GRANTED);
            return;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            // User đã từ chối trước đó và tick "không hỏi lại" — Shizuku sẽ
            // không hiện dialog nữa. UI nên hướng dẫn user vào app Shizuku
            // để cấp quyền thủ công thay vì gọi requestPermission() lại.
            notifyState(State.PERMISSION_DENIED);
            return;
        }
        Shizuku.requestPermission(REQUEST_CODE);
    }

    private void notifyState(State state) {
        activity.runOnUiThread(() -> listener.onStateChanged(state));
    }
}
