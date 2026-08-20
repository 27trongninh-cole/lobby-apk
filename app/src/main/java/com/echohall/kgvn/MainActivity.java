package com.echohall.kgvn;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

/**
 * Màn hình chính — theme Nod-Krai (xanh băng), tái hiện ngôn ngữ thiết kế
 * của web (nút pill bo tròn, card viền mảnh). Đây là Activity DUY NHẤT của
 * app (bản card-style trước đó đã được thay thế hoàn toàn).
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuDot;
    private TextView tvShizukuStatus;
    private TextView tvSelectedFile;
    private TextView tvLog;
    private TextView tvProgressLabel;
    private TextView tvHandleLogLabel;
    private ProgressBar progressBar;
    private TextView btnRequestPermission;
    private TextView btnPickZip;
    private TextView btnInstall;
    private TextView btnUninstallAll;
    private View handleBarLog;
    private View layoutLog;

    // Preview
    private FrameLayout previewVideoWrap;
    private VideoView previewVideo;
    private ImageView previewOverlayImg;
    private View overlayToggleChip;
    private TextView tvOverlayToggleState;
    private View previewEmptyOverlay;
    private TextView tvPreviewEmptyLabel;
    private TextView tvPreviewCaption;
    private boolean overlayOn = true;
    private MediaPlayer previewAudioPlayer;
    private PreviewDecoder previewDecoder;

    private ShizukuPermissionHelper shizukuHelper;
    private ModInstaller modInstaller;
    private ModPreviewLocator previewLocator;

    private Uri selectedZipUri;
    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;
    private boolean logExpanded = false;

    private final ActivityResultLauncher<String[]> pickZipLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedZipUri = uri;
                tvSelectedFile.setText(uri.getLastPathSegment());
                updateInstallButtonEnabled();
                loadPreview(uri);
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
        progressBar = findViewById(R.id.progressBar);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnPickZip = findViewById(R.id.btnPickZip);
        btnInstall = findViewById(R.id.btnInstall);
        btnUninstallAll = findViewById(R.id.btnUninstallAll);
        handleBarLog = findViewById(R.id.handleBarLog);
        layoutLog = findViewById(R.id.layoutLog);

        previewVideoWrap = findViewById(R.id.previewVideoWrap);
        previewVideo = findViewById(R.id.previewVideo);
        previewOverlayImg = findViewById(R.id.previewOverlayImg);
        overlayToggleChip = findViewById(R.id.overlayToggleChip);
        tvOverlayToggleState = findViewById(R.id.tvOverlayToggleState);
        previewEmptyOverlay = findViewById(R.id.previewEmptyOverlay);
        tvPreviewEmptyLabel = findViewById(R.id.tvPreviewEmptyLabel);
        tvPreviewCaption = findViewById(R.id.tvPreviewCaption);

        modInstaller = new ModInstaller(this);
        previewLocator = new ModPreviewLocator(this);
        previewDecoder = new PreviewDecoder(this, (ViewGroup) findViewById(android.R.id.content));
        shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);

        btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
        btnPickZip.setOnClickListener(v -> pickZipLauncher.launch(new String[]{"application/zip", "application/octet-stream"}));
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());
        handleBarLog.setOnClickListener(v -> toggleLog());
        overlayToggleChip.setOnClickListener(v -> toggleOverlay());

        shizukuHelper.checkAndNotify();
    }

    @Override
    protected void onResume() {
        super.onResume();
        shizukuHelper.checkAndNotify();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shizukuHelper.unregister();
        stopPreview();
        previewDecoder.destroy();
    }

    // ─────────────────────────── Preview mod (wem+video trong zip) ───────────────────────────

    private void loadPreview(Uri zipUri) {
        stopPreview();
        previewVideoWrap.setVisibility(View.VISIBLE);
        previewEmptyOverlay.setVisibility(View.VISIBLE);
        tvPreviewEmptyLabel.setText("Đang tải preview...");

        new Thread(() -> {
            try {
                ModPreviewLocator.PreviewFiles files = previewLocator.locate(zipUri);
                runOnUiThread(() -> applyPreviewFiles(files));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    tvPreviewEmptyLabel.setText("Không đọc được preview: " + msg);
                    log("⚠ Preview lỗi: " + msg);
                });
            }
        }).start();
    }

    private void applyPreviewFiles(ModPreviewLocator.PreviewFiles files) {
        if (files.videoFile == null && files.wemFile == null) {
            tvPreviewEmptyLabel.setText("File mod này không có nhạc/video để xem trước");
            return;
        }

        if (files.videoFile != null) {
            previewVideo.setVideoURI(Uri.fromFile(files.videoFile));
            previewVideo.setOnPreparedListener(mp -> {
                mp.setVolume(0f, 0f); // video câm tiếng — audio phát riêng từ .wem, giống cơ chế trên web
                mp.setLooping(true);
                previewVideo.start();
                previewEmptyOverlay.setVisibility(View.GONE);
            });
            previewVideo.setOnErrorListener((mp, what, extra) -> {
                log("⚠ Lỗi phát video preview (what=" + what + ")");
                return true;
            });
        } else {
            // Không có video, chỉ có audio — vẫn ẩn overlay rỗng để không che mất
            // trạng thái "đang phát nhạc", chỉ còn màn hình đen làm nền.
            previewEmptyOverlay.setVisibility(View.GONE);
        }

        if (files.wemFile != null) {
            try {
                byte[] wemBytes = java.nio.file.Files.readAllBytes(files.wemFile.toPath());
                previewDecoder.decode(wemBytes, new PreviewDecoder.Callback() {
                    @Override
                    public void onOggReady(byte[] oggBytes) {
                        runOnUiThread(() -> playDecodedAudio(oggBytes));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> log("⚠ Giải mã nhạc preview lỗi: " + message));
                    }
                });
            } catch (Exception e) {
                log("⚠ Không đọc được file .wem để preview: " + e.getMessage());
            }
        }
    }

    private void playDecodedAudio(byte[] oggBytes) {
        try {
            File tmp = new File(getCacheDir(), "preview_audio.ogg");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(oggBytes);
            }
            if (previewAudioPlayer != null) {
                previewAudioPlayer.release();
            }
            previewAudioPlayer = new MediaPlayer();
            previewAudioPlayer.setDataSource(tmp.getAbsolutePath());
            previewAudioPlayer.setLooping(true);
            previewAudioPlayer.setOnPreparedListener(MediaPlayer::start);
            previewAudioPlayer.setOnErrorListener((mp, what, extra) -> {
                log("⚠ Lỗi phát audio preview (what=" + what + ")");
                return true;
            });
            previewAudioPlayer.prepareAsync();
        } catch (Exception e) {
            log("⚠ Không phát được audio preview: " + e.getMessage());
        }
    }

    private void toggleOverlay() {
        overlayOn = !overlayOn;
        previewOverlayImg.setVisibility(overlayOn ? View.VISIBLE : View.GONE);
        tvOverlayToggleState.setTextColor(overlayOn ? 0xFF80c8f8 : 0xFF386080);
    }

    private void stopPreview() {
        if (previewVideo != null) {
            previewVideo.stopPlayback();
        }
        if (previewAudioPlayer != null) {
            previewAudioPlayer.release();
            previewAudioPlayer = null;
        }
        if (previewVideoWrap != null) {
            previewVideoWrap.setVisibility(View.GONE);
        }
    }

    private void onShizukuStateChanged(ShizukuPermissionHelper.State state) {
        currentShizukuState = state;
        switch (state) {
            case BINDER_NOT_AVAILABLE:
                tvShizukuDot.setTextColor(0xFF386080);
                tvShizukuStatus.setText("Chưa sẵn sàng");
                btnRequestPermission.setText("🔑  Kiểm tra lại Shizuku");
                btnRequestPermission.setAlpha(1f);
                break;
            case PERMISSION_DENIED:
                tvShizukuDot.setTextColor(0xFFe9c846);
                tvShizukuStatus.setText("Chưa cấp quyền");
                btnRequestPermission.setText("🔑  Xin quyền Shizuku");
                btnRequestPermission.setAlpha(1f);
                break;
            case GRANTED:
                tvShizukuDot.setTextColor(0xFF6fcf6f);
                tvShizukuStatus.setText("Đã sẵn sàng ✓");
                btnRequestPermission.setText("🔑  Đã cấp quyền");
                btnRequestPermission.setAlpha(0.6f);
                break;
        }
        updateInstallButtonEnabled();
    }

    private void updateInstallButtonEnabled() {
        boolean enabled = selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        btnInstall.setEnabled(enabled);
        btnInstall.setAlpha(enabled ? 1f : 0.4f);
    }

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
                // uninstallAll hiện chạy gọn 1 lượt, không báo progress từng file.
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

    private void setBusyUi(boolean busy, String initialLabel) {
        boolean canInstall = !busy && selectedZipUri != null && currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        btnInstall.setEnabled(canInstall);
        btnInstall.setAlpha(canInstall ? 1f : 0.4f);
        btnUninstallAll.setEnabled(!busy);
        btnPickZip.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        tvProgressLabel.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progressBar.setProgress(0);
            tvProgressLabel.setText(initialLabel == null ? "" : initialLabel);
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
