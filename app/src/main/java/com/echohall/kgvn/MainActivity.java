package com.echohall.kgvn;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Date;

/**
 * Màn hình chính — theme Nod-Krai (xanh băng), tái hiện ngôn ngữ thiết kế
 * của web (nút pill bo tròn, card viền mảnh). Đây là Activity DUY NHẤT của
 * app (bản card-style trước đó đã được thay thế hoàn toàn).
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvShizukuDot;
    private TextView tvShizukuLabel;
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

    // 2 trang
    private TextView tabInstall;
    private TextView tabCreate;
    private View tabBarTrack;
    private View tabIndicator;
    private int tabIndicatorWidth = 0;
    private View pageInstall;
    private View pageCreate;
    private WebView createModWebView;
    private View createModLoading;
    private boolean createModPageLoaded = false;

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
    private ModPreviewLocator previewLocator;

    private Uri selectedZipUri;
    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;
    private boolean logExpanded = false;

    // Android < 11 (API 30): scoped storage cho Android/data/ chưa bị siết,
    // về lý thuyết không CẦN Shizuku để ghi vào thư mục app khác. Cờ này chỉ
    // quyết định việc MỞ KHOÁ NÚT ở tầng UI theo đúng yêu cầu — lưu ý: logic
    // cài/gỡ mod (ModBackupManager/IspdiffFixer/ModInstaller) hiện VẪN gọi
    // lệnh qua Shizuku KHÔNG ĐIỀU KIỆN, nên trên máy <11 thật sự chạy các thao
    // tác đó vẫn cần Shizuku đang hoạt động cho đến khi có luồng ghi file trực
    // tiếp riêng (chưa làm) thay thế.
    private final boolean isPreScopedStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

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
        tvShizukuLabel = findViewById(R.id.tvShizukuLabel);
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

        tabInstall = findViewById(R.id.tabInstall);
        tabCreate = findViewById(R.id.tabCreate);
        tabBarTrack = findViewById(R.id.tabBarTrack);
        tabIndicator = findViewById(R.id.tabIndicator);
        pageInstall = findViewById(R.id.pageInstall);
        pageCreate = findViewById(R.id.pageCreate);
        createModWebView = findViewById(R.id.createModWebView);
        createModLoading = findViewById(R.id.createModLoading);

        tabInstall.setOnClickListener(v -> switchToPage(true));
        tabCreate.setOnClickListener(v -> switchToPage(false));

        // Đợi layout xong mới biết chiều rộng thật của track để tính đúng
        // kích thước/khoảng trượt của khối indicator — không thể tính trước
        // lúc view chưa được đo (width lúc này luôn = 0).
        tabBarTrack.post(this::setupTabIndicatorSize);

        modInstaller = new ModInstaller(this);
        previewLocator = new ModPreviewLocator(this);
        previewDecoder = new PreviewDecoder(this, (ViewGroup) findViewById(android.R.id.content));

        btnPickZip.setOnClickListener(v -> pickZipLauncher.launch(new String[]{"application/zip", "application/octet-stream"}));
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());
        handleBarLog.setOnClickListener(v -> toggleLog());
        overlayToggleChip.setOnClickListener(v -> toggleOverlay());

        if (isPreScopedStorage) {
            // KHÔNG khởi tạo shizukuHelper ở đây — nếu Shizuku tình cờ đang
            // chạy trên máy <11 này, các sự kiện binder của nó (đăng ký sticky)
            // có thể tự bắn callback và khoá lại nút, phá vỡ đúng mục đích
            // bypass. Không tạo listener thì không có gì để tự động khoá lại.
            setupPreScopedStorageUi();
        } else {
            shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);
            btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
            shizukuHelper.checkAndNotify();
        }

        // Tải sẵn trang "Tạo Mod" ngay từ lúc mở app — pageCreate đang ẩn
        // (visibility=gone) nên người dùng không thấy gì, chỉ khi họ bấm tab
        // mới lộ ra, lúc đó (nếu đã tải xong) sẽ gần như tức thì thay vì phải
        // đợi 7-8s Chromium cold-start + tải trang ngay lúc bấm.
        preloadCreateModWebView();
    }

    /**
     * Android < 11: dòng trạng thái đổi thành hiển thị phiên bản hệ điều
     * hành thay vì Shizuku, ẩn nút cấp quyền, và MỞ KHOÁ toàn bộ nút bên
     * dưới ngay từ đầu (không đợi Shizuku) — đúng yêu cầu ở tầng UI. Xem
     * ghi chú tại khai báo isPreScopedStorage về giới hạn thực tế còn lại.
     */
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
    public void onBackPressed() {
        if (pageCreate.getVisibility() == View.VISIBLE && createModWebView != null && createModWebView.canGoBack()) {
            createModWebView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuHelper != null) {
            shizukuHelper.unregister();
        }
        stopPreview();
        previewDecoder.destroy();
        if (createModWebView != null) {
            createModWebView.destroy();
        }
    }

    // ─────────────────────────── Preview mod (wem+video trong zip) ───────────────────────────

    private void loadPreview(Uri zipUri) {
        stopPreview();
        previewVideoWrap.setVisibility(View.VISIBLE);
        tvPreviewCaption.setVisibility(View.VISIBLE);
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
                previewDecoder.playWem(wemBytes, new PreviewDecoder.PlaybackCallback() {
                    @Override
                    public void onPlaybackStarted() {
                        // Không cần làm gì thêm — audio tự phát loop trong WebView.
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> log("⚠ Phát nhạc preview lỗi: " + message));
                    }
                });
            } catch (Exception e) {
                log("⚠ Không đọc được file .wem để preview: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────── 2 trang: Cài Mod / Tạo Mod ───────────────────────────

    private void setupTabIndicatorSize() {
        int trackWidth = tabBarTrack.getWidth();
        int trackPadding = tabBarTrack.getPaddingLeft() + tabBarTrack.getPaddingRight();
        tabIndicatorWidth = (trackWidth - trackPadding) / 2;

        ViewGroup.LayoutParams lp = tabIndicator.getLayoutParams();
        lp.width = tabIndicatorWidth;
        tabIndicator.setLayoutParams(lp);
        // Bắt đầu ở tab "Cài Mod" (mặc định mở app) — vị trí 0, không cần trượt.
        tabIndicator.setTranslationX(0f);
    }

    private void switchToPage(boolean install) {
        pageInstall.setVisibility(install ? View.VISIBLE : View.GONE);
        pageCreate.setVisibility(install ? View.GONE : View.VISIBLE);

        // Khối "kính" trượt qua lại phía sau nhãn — cảm giác mượt/độc đáo hơn
        // hẳn so với đổi hẳn màu nền 2 nút riêng biệt.
        float targetX = install ? 0f : tabIndicatorWidth;
        tabIndicator.animate()
                .translationX(targetX)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        tabInstall.setTextColor(install ? 0xFF0b0800 : 0xFFa0c8ee);
        tabCreate.setTextColor(install ? 0xFFa0c8ee : 0xFF0b0800);
        // KHÔNG còn khởi tạo WebView ở đây nữa — đã tải sẵn từ lúc mở app
        // (xem preloadCreateModWebView(), gọi trong onCreate) để tránh khoảng
        // trễ Chromium cold-start + tải trang lộ ra đúng lúc người dùng bấm tab.
    }

    /**
     * Nhúng thẳng trang builder thật trên web (WebView) — khoá cứng theme
     * Nod-Krai, ẩn bảng chọn 7 theme, và khử các dấu hiệu "đây là web"
     * (tap-highlight, overscroll glow, zoom, chớp nền trắng lúc tải) để cảm
     * giác liền mạch với phần native xung quanh — không phải bê nguyên
     * trình duyệt vào app.
     *
     * GỌI NGAY LÚC MỞ APP (không đợi user bấm tab "Tạo Mod"), vì phần lớn
     * độ trễ thực đo được (~7-8s) không đến từ mạng (trình duyệt ngoài tải
     * <1s) mà từ việc engine Chromium bên trong WebView phải khởi động lần
     * đầu (cold-start) — dồn hết vào đúng lúc user bấm tab thì mới thấy
     * giật. Tải sẵn ẩn phía sau trong lúc user còn thao tác ở trang "Cài
     * Mod" thì thời gian đó gần như vô hình.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void preloadCreateModWebView() {
        createModLoading.setVisibility(View.VISIBLE);

        createModWebView.setBackgroundColor(Color.parseColor("#060b12")); // khớp nền app — không chớp trắng lúc tải
        createModWebView.getSettings().setJavaScriptEnabled(true);
        createModWebView.getSettings().setDomStorageEnabled(true); // trang web dùng localStorage lưu theme
        createModWebView.getSettings().setMediaPlaybackRequiresUserGesture(false); // cho preview tự phát khi user bấm trong trang
        createModWebView.getSettings().setSupportZoom(false);
        createModWebView.getSettings().setBuiltInZoomControls(false);
        createModWebView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT); // dùng cache HTTP bình thường — lần mở SAU (kể cả sau khi tắt app) đỡ phải tải lại y nguyên
        createModWebView.setOverScrollMode(View.OVER_SCROLL_NEVER); // bỏ hiệu ứng "dội" khi cuộn hết trang — dấu hiệu rõ nhất của web
        createModWebView.setVerticalScrollBarEnabled(false);
        createModWebView.setHorizontalScrollBarEnabled(false);

        createModWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAppFeelTweaks(view);
                createModLoading.setVisibility(View.GONE);
                createModPageLoaded = true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                runOnUiThread(() -> {
                    createModLoading.setVisibility(View.GONE);
                    // Chỉ báo lỗi nếu người dùng ĐANG ở trang Tạo Mod — lỗi mạng
                    // lúc tải ẩn phía sau (chưa ai bấm tab) không cần làm phiền
                    // ngay, sẽ tự báo lại khi họ thực sự mở tab đó ra.
                    if (pageCreate.getVisibility() == View.VISIBLE) {
                        Toast.makeText(MainActivity.this, "Không tải được trang tạo mod: " + description, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        createModWebView.loadUrl(AppConfig.WEB_BASE_URL);
    }

    /**
     * Tiêm sau khi trang tải xong: khoá theme về Nod-Krai (snezhnaya), ẩn
     * hẳn bảng chọn 7 theme (không xoá code — chỉ ẩn qua CSS, để không đụng
     * vào logic web), và tắt các hiệu ứng đặc trưng của trình duyệt (bôi đen
     * chọn chữ, highlight khi chạm, popup giữ-lâu) — CHỪA lại khả năng gõ/
     * chọn chữ trong ô tìm kiếm, không thì người dùng không gõ được gì.
     */
    private void injectAppFeelTweaks(WebView view) {
        String js = "(function(){"
                + "try{ if (typeof setTheme === 'function') setTheme('snezhnaya'); }catch(e){}"
                + "var s = document.createElement('style');"
                + "s.textContent = "
                + "'#themePanel > .panel-title, #themePanel .theme-grid { display:none !important; }"
                + "* { -webkit-tap-highlight-color: transparent !important; -webkit-touch-callout: none !important; }"
                + "body, div, span, button { -webkit-user-select: none !important; user-select: none !important; }"
                + "input, textarea { -webkit-user-select: text !important; user-select: text !important; }';"
                + "document.head.appendChild(s);"
                + "})();";
        view.evaluateJavascript(js, null);
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
        if (previewDecoder != null) {
            previewDecoder.stopAudio();
        }
        if (previewVideoWrap != null) {
            previewVideoWrap.setVisibility(View.GONE);
        }
        if (tvPreviewCaption != null) {
            tvPreviewCaption.setVisibility(View.GONE);
        }
    }

    private void onShizukuStateChanged(ShizukuPermissionHelper.State state) {
        currentShizukuState = state;
        switch (state) {
            case BINDER_NOT_AVAILABLE:
                tvShizukuDot.setTextColor(0xFF386080);
                tvShizukuStatus.setText("Chưa sẵn sàng");
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
        // Mọi nút bên dưới (chọn file, cài, gỡ) đều khoá lại cho đến khi có
        // quyền Shizuku — đúng yêu cầu: quyền này là điều kiện tiên quyết
        // trước khi làm bất cứ gì khác, không riêng nút Cài Mod.
        setActionButtonsLocked(state != ShizukuPermissionHelper.State.GRANTED);
        updateInstallButtonEnabled();
    }

    /** Khoá/mở tất cả nút PHÍA DƯỚI khối cấp quyền (chọn file + gỡ mod). Nút Cài Mod có thêm điều kiện riêng (phải chọn file), xử lý ở updateInstallButtonEnabled(). */
    private void setActionButtonsLocked(boolean locked) {
        btnPickZip.setEnabled(!locked);
        btnPickZip.setAlpha(locked ? 0.4f : 1f);
        btnUninstallAll.setEnabled(!locked);
        btnUninstallAll.setAlpha(locked ? 0.4f : 1f);
        updateInstallButtonEnabled();
    }

    private void updateInstallButtonEnabled() {
        boolean permissionOk = isPreScopedStorage || currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        boolean enabled = selectedZipUri != null && permissionOk;
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
        boolean permissionOk = isPreScopedStorage || currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        boolean canInstall = !busy && selectedZipUri != null && permissionOk;
        btnInstall.setEnabled(canInstall);
        btnInstall.setAlpha(canInstall ? 1f : 0.4f);

        // Hết bận -> trả về đúng trạng thái khoá/mở theo quyền, KHÔNG mở
        // khoá vô điều kiện (trước đây thiếu kiểm tra permissionOk ở đây,
        // khiến nút bật lại dù chưa có quyền, sau khi 1 thao tác busy kết thúc).
        boolean actionButtonsEnabled = !busy && permissionOk;
        btnUninstallAll.setEnabled(actionButtonsEnabled);
        btnUninstallAll.setAlpha(actionButtonsEnabled ? 1f : 0.4f);
        btnPickZip.setEnabled(actionButtonsEnabled);
        btnPickZip.setAlpha(actionButtonsEnabled ? 1f : 0.4f);

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
