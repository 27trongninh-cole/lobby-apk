package com.echohall.kgvn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private TextView tvLog;
    private TextView tvProgressLabel;
    private ProgressBar progressBar;
    private Button btnRequestPermission;
    private Button btnPickZip;
    private Button btnInstall;
    private Button btnUninstallAll;

    private ShizukuPermissionHelper shizukuHelper;
    private ModInstaller modInstaller;

    private Uri selectedZipUri;
    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;

    private final ActivityResultLauncher<String[]> pickZipLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                // Giữ quyền đọc uri này lâu dài trong phiên (không bắt buộc phải
                // persistable vì mình đọc ngay, không cần giữ qua lần khởi động app).
                selectedZipUri = uri;
                tvSelectedFile.setText("Đã chọn: " + uri.getLastPathSegment());
                updateInstallButtonEnabled();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvLog = findViewById(R.id.tvLog);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        progressBar = findViewById(R.id.progressBar);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnPickZip = findViewById(R.id.btnPickZip);
        btnInstall = findViewById(R.id.btnInstall);
        btnUninstallAll = findViewById(R.id.btnUninstallAll);

        modInstaller = new ModInstaller(this);

        shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);

        btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
        btnPickZip.setOnClickListener(v -> pickZipLauncher.launch(new String[]{"application/zip", "application/octet-stream"}));
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());

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
                tvShizukuStatus.setText("Trạng thái Shizuku: CHƯA sẵn sàng — mở app Shizuku và khởi động service trước (qua ADB hoặc root), rồi quay lại đây.");
                btnRequestPermission.setEnabled(true);
                btnRequestPermission.setText("Kiểm tra lại Shizuku");
                break;
            case PERMISSION_DENIED:
                tvShizukuStatus.setText("Trạng thái Shizuku: đã kết nối nhưng CHƯA cấp quyền cho app này.");
                btnRequestPermission.setEnabled(true);
                btnRequestPermission.setText("Xin quyền Shizuku");
                break;
            case GRANTED:
                tvShizukuStatus.setText("Trạng thái Shizuku: ĐÃ sẵn sàng ✓");
                btnRequestPermission.setEnabled(false);
                btnRequestPermission.setText("Đã cấp quyền");
                break;
        }
        updateInstallButtonEnabled();
    }

    private void updateInstallButtonEnabled() {
        btnInstall.setEnabled(selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED);
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
        btnInstall.setEnabled(!busy && selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED);
        btnUninstallAll.setEnabled(!busy);
        btnPickZip.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        tvProgressLabel.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progressBar.setProgress(0);
            tvProgressLabel.setText(initialLabel == null ? "" : initialLabel);
        }
    }

    private void log(String message) {
        String time = DateFormat.format("HH:mm:ss", new Date()).toString();
        String current = tvLog.getText().toString();
        tvLog.setText(current + "[" + time + "] " + message + "\n");
    }
}
