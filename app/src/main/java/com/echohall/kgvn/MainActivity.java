package com.echohall.kgvn;

import android.net.Uri;
import android.os.Build;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * Màn hình chính — theme Nod-Krai (xanh băng).
 *
 * BẢN NÀY: không còn khái niệm "tải zip / chọn zip" cho người dùng. Người
 * dùng chỉ chọn 1 bài nhạc + 1 video (từ thư viện web hoặc tự upload), bấm
 * "Cài Mod" — app tự gọi server build zip (ApiClient) rồi đưa THẲNG vào
 * ModInstaller để cài, không lộ bước file zip trung gian nào ra UI.
 *
 * Bản zip build gần nhất được giữ lại trong cache app (last_build.zip) để
 * có thể "Cài lại bản gần nhất" mà không cần gọi lại server.
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuDot;
    private TextView tvShizukuLabel;
    private TextView tvShizukuStatus;
    private TextView tvLog;
    private TextView tvProgressLabel;
    private TextView tvHandleLogLabel;
    private ProgressBar progressBar;
    private TextView btnRequestPermission;
    private TextView btnInstall;
    private TextView btnReinstallLast;
    private TextView btnUninstallAll;
    private View handleBarLog;
    private View layoutLog;

    // Chọn nhạc/video
    private TextView tvSelectedWem;
    private TextView btnChooseWem;
    private TextView btnPreviewWem;
    private TextView tvSelectedVideo;
    private TextView btnChooseVideoLibrary;
    private TextView btnChooseVideoUpload;

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
    private PreviewDecoder previewDecoder;

    private ShizukuPermissionHelper shizukuHelper;
    private ModInstaller modInstaller;
    private final ApiClient apiClient = new ApiClient();

    // Trạng thái lựa chọn hiện tại
    private List<ApiClient.WemItem> wemLibrary;
    private List<ApiClient.VideoItem> videoLibrary;
    private ApiClient.WemItem selectedWem;
    private ApiClient.VideoItem selectedVideoFromLibrary; // null nếu đang dùng video tự upload
    private Uri selectedVideoUploadUri;                    // null nếu đang dùng video từ thư viện
    private String selectedVideoUploadName;

    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;
    private boolean logExpanded = false;
    private boolean busy = false;

    private File lastBuildZipFile;

    private final boolean isPreScopedStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

    // Chọn file video từ máy để tự upload (không lấy từ thư viện web).
    private final ActivityResultLauncher<String[]> pickVideoUploadLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedVideoUploadUri = uri;
                selectedVideoFromLibrary = null;
                selectedVideoUploadName = queryDisplayName(uri);
                tvSelectedVideo.setText("📁 " + selectedVideoUploadName);
                updateInstallButtonEnabled();
                refreshPreview();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvShizukuDot = findViewById(R.id.tvShizukuDot);
        tvShizukuLabel = findViewById(R.id.tvShizukuLabel);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvLog = findViewById(R.id.tvLog);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        tvHandleLogLabel = findViewById(R.id.tvHandleLogLabel);
        progressBar = findViewById(R.id.progressBar);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        btnInstall = findViewById(R.id.btnInstall);
        btnReinstallLast = findViewById(R.id.btnReinstallLast);
        btnUninstallAll = findViewById(R.id.btnUninstallAll);
        handleBarLog = findViewById(R.id.handleBarLog);
        layoutLog = findViewById(R.id.layoutLog);

        tvSelectedWem = findViewById(R.id.tvSelectedWem);
        btnChooseWem = findViewById(R.id.btnChooseWem);
        btnPreviewWem = findViewById(R.id.btnPreviewWem);
        tvSelectedVideo = findViewById(R.id.tvSelectedVideo);
        btnChooseVideoLibrary = findViewById(R.id.btnChooseVideoLibrary);
        btnChooseVideoUpload = findViewById(R.id.btnChooseVideoUpload);

        previewVideoWrap = findViewById(R.id.previewVideoWrap);
        previewVideo = findViewById(R.id.previewVideo);
        previewOverlayImg = findViewById(R.id.previewOverlayImg);
        overlayToggleChip = findViewById(R.id.overlayToggleChip);
        tvOverlayToggleState = findViewById(R.id.tvOverlayToggleState);
        previewEmptyOverlay = findViewById(R.id.previewEmptyOverlay);
        tvPreviewEmptyLabel = findViewById(R.id.tvPreviewEmptyLabel);
        tvPreviewCaption = findViewById(R.id.tvPreviewCaption);

        modInstaller = new ModInstaller(this);
        previewDecoder = new PreviewDecoder(this, (ViewGroup) findViewById(android.R.id.content));

        btnChooseWem.setOnClickListener(v -> openWemPickerDialog());
        btnPreviewWem.setOnClickListener(v -> previewSelectedWemOnly());
        btnChooseVideoLibrary.setOnClickListener(v -> openVideoLibraryDialog());
        btnChooseVideoUpload.setOnClickListener(v ->
                pickVideoUploadLauncher.launch(new String[]{"video/*"}));
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnReinstallLast.setOnClickListener(v -> onReinstallLastClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());
        handleBarLog.setOnClickListener(v -> toggleLog());
        overlayToggleChip.setOnClickListener(v -> toggleOverlay());

        if (isPreScopedStorage) {
            setupPreScopedStorageUi();
        } else {
            shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);
            btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
            shizukuHelper.checkAndNotify();
        }

        lastBuildZipFile = new File(getCacheDir(), "last_build.zip");
        updateReinstallButtonVisibility();

        loadLibraries();
    }

    private void setupPreScopedStorageUi() {
        tvShizukuDot.setTextColor(0xFF80c8f8);
        tvShizukuLabel.setText("Android");
        tvShizukuStatus.setText("API " + Build.VERSION.SDK_INT + " (không cần Shizuku)");
        btnRequestPermission.setVisibility(View.GONE);
        setActionButtonsLocked(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isPreScopedStorage && shizukuHelper != null) {
            shizukuHelper.checkAndNotify();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuHelper != null) {
            shizukuHelper.unregister();
        }
        stopPreview();
        previewDecoder.destroy();
    }

    // ─────────────────────────── Thư viện nhạc/video ───────────────────────────

    private void loadLibraries() {
        log("Đang tải thư viện nhạc/video từ server...");
        apiClient.fetchWemList(new ApiClient.Callback<List<ApiClient.WemItem>>() {
            @Override
            public void onSuccess(List<ApiClient.WemItem> result) {
                wemLibrary = result;
                log("✓ Tải xong " + result.size() + " bài nhạc.");
            }

            @Override
            public void onError(String message) {
                log("✗ Lỗi tải danh sách nhạc: " + message);
                Toast.makeText(MainActivity.this, "Lỗi tải danh sách nhạc: " + message, Toast.LENGTH_LONG).show();
            }
        });

        apiClient.fetchVideoList(new ApiClient.Callback<List<ApiClient.VideoItem>>() {
            @Override
            public void onSuccess(List<ApiClient.VideoItem> result) {
                videoLibrary = result;
                log("✓ Tải xong " + result.size() + " video.");
            }

            @Override
            public void onError(String message) {
                log("✗ Lỗi tải danh sách video: " + message);
                Toast.makeText(MainActivity.this, "Lỗi tải danh sách video: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openWemPickerDialog() {
        if (wemLibrary == null || wemLibrary.isEmpty()) {
            Toast.makeText(this, "Chưa tải được danh sách nhạc, thử lại sau.", Toast.LENGTH_SHORT).show();
            loadLibraries();
            return;
        }
        String[] names = new String[wemLibrary.size()];
        for (int i = 0; i < wemLibrary.size(); i++) names[i] = wemLibrary.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("Chọn bài nhạc")
                .setItems(names, (dialog, which) -> {
                    selectedWem = wemLibrary.get(which);
                    tvSelectedWem.setText("🎵 " + selectedWem.name);
                    btnPreviewWem.setAlpha(1f);
                    updateInstallButtonEnabled();
                    refreshPreview();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void openVideoLibraryDialog() {
        if (videoLibrary == null || videoLibrary.isEmpty()) {
            Toast.makeText(this, "Chưa tải được danh sách video, thử lại sau.", Toast.LENGTH_SHORT).show();
            loadLibraries();
            return;
        }
        String[] names = new String[videoLibrary.size()];
        for (int i = 0; i < videoLibrary.size(); i++) names[i] = videoLibrary.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("Chọn video (thư viện)")
                .setItems(names, (dialog, which) -> {
                    selectedVideoFromLibrary = videoLibrary.get(which);
                    selectedVideoUploadUri = null;
                    tvSelectedVideo.setText("📚 " + selectedVideoFromLibrary.name);
                    updateInstallButtonEnabled();
                    refreshPreview();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return "video_da_chon.mp4";
    }

    // ─────────────────────────── Preview (nhạc + video đã chọn) ───────────────────────────

    private void previewSelectedWemOnly() {
        if (selectedWem == null) return;
        Toast.makeText(this, "Đang tải nhạc thử...", Toast.LENGTH_SHORT).show();
        apiClient.fetchWemPreviewBytes(selectedWem.id, new ApiClient.Callback<byte[]>() {
            @Override
            public void onSuccess(byte[] result) {
                previewDecoder.playWem(result, new PreviewDecoder.PlaybackCallback() {
                    @Override
                    public void onPlaybackStarted() {
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> log("⚠ Nghe thử lỗi: " + message));
                    }
                });
            }

            @Override
            public void onError(String message) {
                log("✗ Lỗi tải nhạc thử: " + message);
                Toast.makeText(MainActivity.this, "Lỗi tải nhạc thử: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Xem trước cả nhạc + video đã chọn cùng lúc — video phát câm tiếng (URL
     * trực tiếp từ thư viện, hoặc file cục bộ nếu tự upload), audio phát riêng
     * từ .wem qua PreviewDecoder, y hệt cơ chế trên web.
     */
    private void refreshPreview() {
        boolean hasVideo = selectedVideoFromLibrary != null || selectedVideoUploadUri != null;
        boolean hasWem = selectedWem != null;
        if (!hasVideo && !hasWem) return;

        stopPreview();
        previewVideoWrap.setVisibility(View.VISIBLE);
        tvPreviewCaption.setVisibility(View.VISIBLE);
        previewEmptyOverlay.setVisibility(View.VISIBLE);
        tvPreviewEmptyLabel.setText("Đang tải preview...");

        if (hasVideo) {
            Uri videoUri = selectedVideoUploadUri != null
                    ? selectedVideoUploadUri
                    : Uri.parse(selectedVideoFromLibrary.videoUrl == null ? "" : selectedVideoFromLibrary.videoUrl);
            try {
                previewVideo.setVideoURI(videoUri);
                previewVideo.setOnPreparedListener(mp -> {
                    mp.setVolume(0f, 0f); // câm tiếng — audio phát riêng từ .wem
                    mp.setLooping(true);
                    previewVideo.start();
                    previewEmptyOverlay.setVisibility(View.GONE);
                });
                previewVideo.setOnErrorListener((mp, what, extra) -> {
                    log("⚠ Lỗi phát video preview (what=" + what + ")");
                    return true;
                });
            } catch (Exception e) {
                log("⚠ Không phát được video preview: " + e.getMessage());
            }
        } else {
            previewEmptyOverlay.setVisibility(View.GONE);
        }

        if (hasWem) {
            previewSelectedWemOnly();
        }
    }

    // ─────────────────────────── Cài mod (build + install) ───────────────────────────

    private void updateInstallButtonEnabled() {
        boolean permissionOk = isPreScopedStorage || currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        boolean hasVideo = selectedVideoFromLibrary != null || selectedVideoUploadUri != null;
        boolean enabled = !busy && permissionOk && selectedWem != null && hasVideo;
        btnInstall.setEnabled(enabled);
        btnInstall.setAlpha(enabled ? 1f : 0.4f);
    }

    private void updateReinstallButtonVisibility() {
        boolean exists = lastBuildZipFile != null && lastBuildZipFile.exists();
        btnReinstallLast.setVisibility(exists ? View.VISIBLE : View.GONE);
    }

    private void onInstallClicked() {
        if (selectedWem == null) {
            Toast.makeText(this, "Chưa chọn nhạc", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedVideoFromLibrary == null && selectedVideoUploadUri == null) {
            Toast.makeText(this, "Chưa chọn video", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentShizukuState != ShizukuPermissionHelper.State.GRANTED && !isPreScopedStorage) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusyUi(true, "Đang build trên server...");
        log("Bắt đầu build mod: nhạc=" + selectedWem.name);

        if (selectedVideoUploadUri != null) {
            // Đọc bytes video tự upload trong luồng nền trước khi gọi API.
            new Thread(() -> {
                try {
                    byte[] videoBytes = readAllBytesFromUri(selectedVideoUploadUri);
                    runOnUiThread(() -> callBuildApi(selectedWem.id, null, videoBytes, selectedVideoUploadName));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setBusyUi(false, null);
                        log("✗ Lỗi đọc file video: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Lỗi đọc file video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } else {
            callBuildApi(selectedWem.id, selectedVideoFromLibrary.id, null, null);
        }
    }

    private void callBuildApi(String wemId, String videoId, byte[] videoBytes, String videoFileName) {
        apiClient.build(wemId, videoId, videoBytes, videoFileName, new ApiClient.Callback<ApiClient.BuildResult>() {
            @Override
            public void onSuccess(ApiClient.BuildResult result) {
                log("✓ Build xong (" + result.zipBytes.length + " bytes). Đang lưu cache...");
                try (FileOutputStream fos = new FileOutputStream(lastBuildZipFile)) {
                    fos.write(result.zipBytes);
                } catch (Exception e) {
                    setBusyUi(false, null);
                    log("✗ Lỗi lưu file build: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Lỗi lưu file build: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                updateReinstallButtonVisibility();
                installFromCachedZip("Đang cài vào game...");
            }

            @Override
            public void onError(String message) {
                setBusyUi(false, null);
                log("✗ Build thất bại: " + message);
                Toast.makeText(MainActivity.this, "Build thất bại: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onReinstallLastClicked() {
        if (lastBuildZipFile == null || !lastBuildZipFile.exists()) {
            Toast.makeText(this, "Chưa có bản build nào trong cache", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentShizukuState != ShizukuPermissionHelper.State.GRANTED && !isPreScopedStorage) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusyUi(true, "Đang cài lại bản gần nhất...");
        log("Cài lại từ cache (không gọi server): " + lastBuildZipFile.getName());
        installFromCachedZip("Đang cài lại bản gần nhất...");
    }

    private void installFromCachedZip(String initialStatusLabel) {
        tvProgressLabel.setText(initialStatusLabel);
        modInstaller.installFromZip(Uri.fromFile(lastBuildZipFile), new ModInstaller.ProgressListener() {
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
        if (currentShizukuState != ShizukuPermissionHelper.State.GRANTED && !isPreScopedStorage) {
            Toast.makeText(this, "Chưa có quyền Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusyUi(true, "Đang gỡ mod...");
        log("Bắt đầu gỡ toàn bộ mod...");

        modInstaller.uninstallAll(new ModInstaller.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String currentFileName) {
            }

            @Override
            public void onFileSkipped(String relativePath, String reason) {
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

    private byte[] readAllBytesFromUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Không đọc được file đã chọn");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toByteArray();
        }
    }

    // ─────────────────────────── Overlay preview toggle ───────────────────────────

    private void toggleOverlay() {
        overlayOn = !overlayOn;
        previewOverlayImg.setVisibility(overlayOn ? View.VISIBLE : View.GONE);
        tvOverlayToggleState.setTextColor(overlayOn ? 0xFF80c8f8 : 0xFF386080);
    }

    private void stopPreview() {
        try {
            if (previewVideo != null && previewVideo.isPlaying()) previewVideo.stopPlayback();
        } catch (Exception ignored) {
        }
        previewDecoder.stopAudio();
    }

    // ─────────────────────────── Shizuku ───────────────────────────

    private void onShizukuStateChanged(ShizukuPermissionHelper.State state) {
        currentShizukuState = state;
        switch (state) {
            case BINDER_NOT_AVAILABLE:
                tvShizukuDot.setTextColor(0xFFe05a5a);
                tvShizukuStatus.setText("Shizuku chưa chạy");
                btnRequestPermission.setText("Kiểm tra");
                btnRequestPermission.setAlpha(1f);
                break;
            case PERMISSION_DENIED:
                tvShizukuDot.setTextColor(0xFFe9c846);
                tvShizukuStatus.setText("Chưa cấp quyền");
                btnRequestPermission.setText("Cấp quyền");
                btnRequestPermission.setAlpha(1f);
                break;
            case GRANTED:
                tvShizukuDot.setTextColor(0xFF6fcf6f);
                tvShizukuStatus.setText("Sẵn sàng ✓");
                btnRequestPermission.setText("✓ OK");
                btnRequestPermission.setAlpha(0.6f);
                break;
        }
        setActionButtonsLocked(state != ShizukuPermissionHelper.State.GRANTED);
        updateInstallButtonEnabled();
    }

    private void setActionButtonsLocked(boolean locked) {
        btnUninstallAll.setEnabled(!locked);
        btnUninstallAll.setAlpha(locked ? 0.4f : 1f);
        btnChooseWem.setEnabled(!locked);
        btnChooseVideoLibrary.setEnabled(!locked);
        btnChooseVideoUpload.setEnabled(!locked);
        updateInstallButtonEnabled();
    }

    private void setBusyUi(boolean isBusy, String initialLabel) {
        busy = isBusy;
        boolean permissionOk = isPreScopedStorage || currentShizukuState == ShizukuPermissionHelper.State.GRANTED;

        updateInstallButtonEnabled();

        boolean actionButtonsEnabled = !isBusy && permissionOk;
        btnUninstallAll.setEnabled(actionButtonsEnabled);
        btnUninstallAll.setAlpha(actionButtonsEnabled ? 1f : 0.4f);
        btnReinstallLast.setEnabled(actionButtonsEnabled);
        btnReinstallLast.setAlpha(actionButtonsEnabled ? 1f : 0.4f);

        progressBar.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        tvProgressLabel.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        if (isBusy) {
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
