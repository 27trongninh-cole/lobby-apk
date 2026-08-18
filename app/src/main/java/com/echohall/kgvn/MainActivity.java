package com.echohall.kgvn;

import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuDot;
    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private TextView tvLog;
    private TextView tvProgressLabel;
    private TextView tvHandleLogLabel;
    private TextView tvRequestPermissionTitle;
    private TextView tvRequestPermissionSubtitle;
    private ProgressBar progressBar;
    private LinearLayout btnRequestPermission;
    private LinearLayout btnPickZip;
    private LinearLayout btnInstall;
    private LinearLayout btnUninstallAll;
    private LinearLayout handleBarLog;
    private LinearLayout layoutLog;

    private ShizukuPermissionHelper shizukuHelper;
    private ModInstaller modInstaller;

    private Uri selectedZipUri;
    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;
    private boolean logExpanded = false;

    private final ActivityResultLauncher<String[]> pickZipLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedZipUri = uri;
                tvSelectedFile.setText(uri.getLastPathSegment());
                updateInstallButtonEnabled();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvShizukuDot = findViewById(R.id.tvShizukuDot);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvLog = findViewById(R.id.tvLog);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        tvHandleLogLabel = findViewById(R.id.tvHandleLogLabel);
        tvRequestPermissionTitle = findViewById(R.id.tvRequestPermissionTitle);
        tvRequestPermissionSubtitle = findViewById(R.id.tvRequestPermissionSubtitle);
        progressBar = findViewById(R.id.progressBar);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnPickZip = findViewById(R.id.btnPickZip);
        btnInstall = findViewById(R.id.btnInstall);
        btnUninstallAll = findViewById(R.id.btnUninstallAll);
        handleBarLog = findViewById(R.id.handleBarLog);
        layoutLog = findViewById(R.id.layoutLog);

        modInstaller = new ModInstaller(this);
        shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);

        btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
        btnPickZip.setOnClickListener(v -> pickZipLauncher.launch(new String[]{"application/zip", "application/octet-stream"}));
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());
        handleBarLog.setOnClickListener(v -> toggleLog());

        shizukuHelper.checkAndNotify();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lại app sau khi mở app Shizuku để cấp quyền thủ công — cập nhật
        // lại trạng thái ngay, không bắt user phải tự bấm gì thêm.
        shizukuHelper.checkAndNotify();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shizukuHelper.unregister();
    }

    // ─────────────────────────── Shizuku state ───────────────────────────

    private void onShizukuStateChanged(ShizukuPermissionHelper.State state) {
        currentShizukuState = state;
        switch (state) {
            case BINDER_NOT_AVAILABLE:
                tvShizukuDot.setTextColor(0xFF888888); // xám — chưa kết nối
                tvShizukuStatus.setText("Chưa sẵn sàng");
                tvRequestPermissionTitle.setText("Kiểm tra lại Shizuku");
                tvRequestPermissionSubtitle.setText("Mở app Shizuku, khởi động service rồi quay lại");
                break;
            case PERMISSION_DENIED:
                tvShizukuDot.setTextColor(0xFFe9c846); // vàng — kết nối nhưng chưa cấp quyền
                tvShizukuStatus.setText("Chưa cấp quyền");
                tvRequestPermissionTitle.setText("Xin quyền Shizuku");
                tvRequestPermissionSubtitle.setText("Cần cấp quyền trước khi cài mod");
                break;
            case GRANTED:
                tvShizukuDot.setTextColor(0xFF4caf50); // xanh — sẵn sàng
                tvShizukuStatus.setText("Đã sẵn sàng ✓");
                tvRequestPermissionTitle.setText("Đã cấp quyền");
                tvRequestPermissionSubtitle.setText("Có thể cài mod ngay");
                break;
        }
        // CardView cha của btnRequestPermission — làm mờ đi khi đã xong, không
        // cần chiếm sự chú ý nữa (nhưng vẫn bấm được để kiểm tra lại nếu cần).
        ((View) btnRequestPermission.getParent()).setAlpha(state == ShizukuPermissionHelper.State.GRANTED ? 0.6f : 1f);
        updateInstallButtonEnabled();
    }

    private void updateInstallButtonEnabled() {
        boolean enabled = selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        btnInstall.setEnabled(enabled);
        btnInstall.setAlpha(enabled ? 1f : 0.5f);
    }

    // ─────────────────────────── Install flow ───────────────────────────

    private void onInstallClicked() {
        if (selectedZipUri == null) {
            Toast.makeText(this, "Chưa chọn file zip mod", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentShizukuState != ShizukuPermissionHelper.State.GRANTED) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusyUi(true, "Đang chuẩn bị...");
        log("Bắt đầu cài mod từ: " + selectedZipUri);

        modInstaller.installFromZip(selectedZipUri, new ModInstaller.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String currentFileName) {
                runOnUiThread(() -> {
                    int percent = total == 0 ? 0 : (int) (100.0 * current / total);
                    progressBar.setProgress(percent);
                    tvProgressLabel.setText("Đang cài (" + current + "/" + total + "): " + currentFileName);
                    log("→ Cài: " + currentFileName);
                });
            }

            @Override
            public void onFileSkipped(String relativePath, String reason) {
                runOnUiThread(() -> log("⚠ Bỏ qua " + relativePath + " — " + reason));
            }

            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> {
                    tvProgressLabel.setText(message);
                    log("… " + message);
                });
            }

            @Override
            public void onDone(int installedCount) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    log("✓ Hoàn tất — đã cài " + installedCount + " file.");
                    Toast.makeText(MainActivity.this, "Cài mod thành công (" + installedCount + " file)", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    log("✗ Lỗi: " + message);
                    Toast.makeText(MainActivity.this, "Lỗi cài mod: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ─────────────────────────── Uninstall flow ───────────────────────────

    private void onUninstallAllClicked() {
        if (currentShizukuState != ShizukuPermissionHelper.State.GRANTED) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusyUi(true, "Đang gỡ mod...");
        log("Bắt đầu gỡ toàn bộ mod...");

        modInstaller.uninstallAll(new ModInstaller.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String currentFileName) {
                // uninstallAll hiện chạy gọn 1 lượt, không báo progress từng file — bỏ qua.
            }

            @Override
            public void onFileSkipped(String relativePath, String reason) {
                // Không dùng trong luồng gỡ mod hiện tại.
            }

            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> {
                    tvProgressLabel.setText(message);
                    log("… " + message);
                });
            }

            @Override
            public void onDone(int installedCount) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    log("✓ Đã gỡ xong, khôi phục " + installedCount + " file về gốc.");
                    Toast.makeText(MainActivity.this, "Gỡ mod thành công", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    log("✗ Lỗi khi gỡ mod: " + message);
                    Toast.makeText(MainActivity.this, "Lỗi gỡ mod: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ─────────────────────────── UI helpers ───────────────────────────

    private void setBusyUi(boolean busy, String initialLabel) {
        boolean canInstall = !busy && selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        btnInstall.setEnabled(canInstall);
        btnInstall.setAlpha(canInstall ? 1f : 0.5f);
        btnUninstallAll.setEnabled(!busy);
        btnPickZip.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        tvProgressLabel.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progressBar.setProgress(0);
            tvProgressLabel.setText(initialLabel == null ? "" : initialLabel);
            // Tự mở log khi đang chạy 1 thao tác — người test cần thấy ngay,
            // không phải nhớ bấm mở mỗi lần.
            setLogExpanded(true);
        }
    }

    private void toggleLog() {
        setLogExpanded(!logExpanded);
    }

    private void setLogExpanded(boolean expanded) {
        logExpanded = expanded;
        layoutLog.setVisibility(expanded ? View.VISIBLE : View.GONE);
        tvHandleLogLabel.setText(expanded ? "🧾 Ẩn log debug" : "🧾 Xem log debug");
    }

    private void log(String message) {
        String time = DateFormat.format("HH:mm:ss", new Date()).toString();
        String current = tvLog.getText().toString();
        tvLog.setText(current + "[" + time + "] " + message + "\n");
    }
}
