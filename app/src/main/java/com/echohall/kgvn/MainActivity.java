package com.echohall.kgvn;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.SeekBar;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.echohall.kgvn.localbuild.LocalModBuilder;

/**
 * Màn hình chính — bản redesign v2 (1 màn hình cố định, không cuộn).
 *
 * Header gọn (logo + trạng thái Shizuku + nút góc phải), khung preview 16:9
 * crop lấp đầy (giống object-fit:cover trên web, dùng CropVideoView), 2 thẻ
 * chọn nhạc/video mở dialog picker riêng (list có tìm kiếm cho nhạc, lưới
 * thumbnail có tìm kiếm cho video), khu "Cài gần đây" (lưu SharedPreferences,
 * bấm để chọn lại nhanh tổ hợp cũ — chỉ áp dụng cho video lấy từ thư viện,
 * video tự upload không lưu lại được vì Uri có thể mất quyền truy cập sau khi
 * app khởi động lại), và cụm nút hành động dưới cùng.
 *
 * Nút góc phải trên header hiện là icon Terminal (log debug) cho bản test.
 * Khi build release chính thức, đổi hành vi tại đúng 1 chỗ — hàm
 * {@link #onTopRightActionClicked()} — dựa vào BuildConfig.DEBUG, không cần
 * đụng tới layout hay phần còn lại của Activity.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "melo_ninstaller_prefs";
    private static final String KEY_RECENT_LIST = "recent_installs_json";
    private static final int MAX_RECENT = 3;

    private View tvShizukuDotView;
    private TextView tvShizukuStatus;
    private View btnRequestPermission;
    private TextView tvRequestPermissionLabel;
    private ImageView btnTopRightAction;

    private TextView tvSelectedWem;
    private TextView tvSelectedVideo;
    private View cardChooseWem;
    private View cardChooseVideo;

    private CropVideoView previewVideo;    private ImageView previewOverlayImg;
    private View overlayToggleChip;
    private TextView tvOverlayToggleState;
    private View previewEmptyOverlay;
    private TextView tvPreviewCaptionOverlay;
    private boolean overlayOn = true;
    private PreviewDecoder previewDecoder;

    // Thanh playback nhạc dưới khung preview.
    private View layoutPlaybackBar;
    private ImageView btnPlaybackToggle;
    private SeekBar seekPlayback;
    private TextView tvPlaybackTime;
    private final Handler playbackPollHandler = new Handler(Looper.getMainLooper());
    private boolean isDraggingSeek = false;
    private final Runnable playbackPollRunnable = new Runnable() {
        @Override
        public void run() {
            previewDecoder.queryPlaybackState(state -> {
                if (!state.hasSrc) {
                    layoutPlaybackBar.setVisibility(View.GONE);
                } else {
                    layoutPlaybackBar.setVisibility(View.VISIBLE);
                    btnPlaybackToggle.setImageResource(state.playing ? R.drawable.ic_pause : R.drawable.ic_play);
                    tvPlaybackTime.setText(formatTime(state.currentTimeSec) + " / " + formatTime(state.durationSec));
                    if (!isDraggingSeek && state.durationSec > 0) {
                        int progress = (int) (1000 * state.currentTimeSec / state.durationSec);
                        seekPlayback.setProgress(Math.max(0, Math.min(1000, progress)));
                    }
                }
            });
            playbackPollHandler.postDelayed(this, 300);
        }
    };

    private void startPlaybackPolling() {
        playbackPollHandler.removeCallbacks(playbackPollRunnable);
        playbackPollHandler.post(playbackPollRunnable);
    }

    private void stopPlaybackPolling() {
        playbackPollHandler.removeCallbacks(playbackPollRunnable);
        if (layoutPlaybackBar != null) layoutPlaybackBar.setVisibility(View.GONE);
    }

    private static String formatTime(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) seconds = 0;
        int total = (int) seconds;
        return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60);
    }


    private TextView tvRecentLabel;
    private LinearLayout layoutRecentList;

    private ProgressBar progressBar;
    private TextView tvProgressLabel;
    private View btnInstall;
    private View btnReinstallLast;
    private View btnUninstallAll;

    private View loadingOverlay;
    private ProgressBar loadingSpinner;
    private ImageView loadingErrorIcon;
    private TextView tvLoadingLabel;

    private View installProgressOverlay;
    private TextView tvInstallProgressLabel;
    private ProgressBar installOverlayProgressBar;
    private TextView tvInstallProgressDetail;

    private StringBuilder logBuffer = new StringBuilder();

    private ShizukuPermissionHelper shizukuHelper;
    private ModInstaller modInstaller;
    private final ApiClient apiClient = new ApiClient();

    private List<ApiClient.WemItem> wemLibrary;
    private List<ApiClient.VideoItem> videoLibrary;
    private ApiClient.WemItem selectedWem;
    private ApiClient.VideoItem selectedVideoFromLibrary;
    private Uri selectedVideoUploadUri;
    private String selectedVideoUploadName;

    // Nhạc TỰ TẢI LÊN — CHỈ lưu Uri file gốc lúc chọn, KHÔNG convert ngay.
    // Convert wav/mp3/ogg -> wem (com.echohall.kgvn.w2w.WavToWemPipeline)
    // chỉ chạy khi người dùng bấm "Cài mod" — tránh convert xong rồi người
    // dùng đổi ý chọn file khác, phí thời gian máy yếu convert (vài phút).
    // selectedLocalWemFile/DurationMs chỉ được điền TRONG buildLocalAndInstall().
    private Uri selectedAudioUploadUri;
    private String selectedAudioUploadName;
    private File selectedLocalWemFile;
    private int selectedLocalWemDurationMs;

    // Phát ngay file gốc (wav/mp3/ogg) vừa chọn để nghe thử LIỀN, không đợi
    // convert wem xong (convert có thể mất vài phút trên máy yếu — xem log
    // thực tế: 240907ms cho 1 file). Dùng MediaPlayer thường vì mp3/wav/ogg
    // phát trực tiếp được, khác với .wem cần previewDecoder (WebView) riêng.
    private android.media.MediaPlayer rawAudioPreviewPlayer;

    private ShizukuPermissionHelper.State currentShizukuState = ShizukuPermissionHelper.State.BINDER_NOT_AVAILABLE;
    private boolean busy = false;

    private File lastBuildZipFile;
    private SharedPreferences prefs;

    private final boolean isPreScopedStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;

    private final ActivityResultLauncher<String[]> pickVideoUploadLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedVideoUploadUri = uri;
                selectedVideoFromLibrary = null;
                selectedVideoUploadName = queryDisplayName(uri);
                tvSelectedVideo.setText(selectedVideoUploadName);
                updateInstallButtonEnabled();
                refreshPreview();
            });

    private final ActivityResultLauncher<String[]> pickAudioUploadLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                onAudioUploadSelected(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        VideoGridAdapter.logger = this::log;
        lockActivityWindowHeight();

        tvShizukuDotView = findViewById(R.id.tvShizukuDotView);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        btnRequestPermission = findViewById(R.id.btnRequestPermission);
        tvRequestPermissionLabel = findViewById(R.id.tvRequestPermissionLabel);
        btnTopRightAction = findViewById(R.id.btnTopRightAction);

        tvSelectedWem = findViewById(R.id.tvSelectedWem);
        tvSelectedVideo = findViewById(R.id.tvSelectedVideo);
        cardChooseWem = findViewById(R.id.cardChooseWem);
        cardChooseVideo = findViewById(R.id.cardChooseVideo);

        previewVideo = findViewById(R.id.previewVideo);
        previewOverlayImg = findViewById(R.id.previewOverlayImg);
        overlayToggleChip = findViewById(R.id.overlayToggleChip);
        tvOverlayToggleState = findViewById(R.id.tvOverlayToggleState);
        previewEmptyOverlay = findViewById(R.id.previewEmptyOverlay);
        tvPreviewCaptionOverlay = findViewById(R.id.tvPreviewCaptionOverlay);

        tvRecentLabel = findViewById(R.id.tvRecentLabel);
        layoutRecentList = findViewById(R.id.layoutRecentList);

        progressBar = findViewById(R.id.progressBar);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        btnInstall = findViewById(R.id.btnInstall);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loadingErrorIcon = findViewById(R.id.loadingErrorIcon);
        tvLoadingLabel = findViewById(R.id.tvLoadingLabel);

        installProgressOverlay = findViewById(R.id.installProgressOverlay);
        tvInstallProgressLabel = findViewById(R.id.tvInstallProgressLabel);
        installOverlayProgressBar = findViewById(R.id.installOverlayProgressBar);
        tvInstallProgressDetail = findViewById(R.id.tvInstallProgressDetail);
        btnReinstallLast = findViewById(R.id.btnReinstallLast);
        btnUninstallAll = findViewById(R.id.btnUninstallAll);

        modInstaller = new ModInstaller(this);
        previewDecoder = new PreviewDecoder(this, (ViewGroup) findViewById(android.R.id.content));

        layoutPlaybackBar = findViewById(R.id.layoutPlaybackBar);
        btnPlaybackToggle = findViewById(R.id.btnPlaybackToggle);
        seekPlayback = findViewById(R.id.seekPlayback);
        tvPlaybackTime = findViewById(R.id.tvPlaybackTime);

        btnPlaybackToggle.setOnClickListener(v -> previewDecoder.togglePlayPause());
        seekPlayback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isDraggingSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isDraggingSeek = false;
                previewDecoder.queryPlaybackState(state -> {
                    if (state.durationSec > 0) {
                        double seekSeconds = state.durationSec * seekBar.getProgress() / 1000.0;
                        previewDecoder.seekTo(seekSeconds);
                    }
                });
            }
        });

        cardChooseWem.setOnClickListener(v -> openWemPickerDialog());
        cardChooseVideo.setOnClickListener(v -> openVideoPickerDialog());
        btnTopRightAction.setOnClickListener(v -> onTopRightActionClicked());
        btnInstall.setOnClickListener(v -> onInstallClicked());
        btnReinstallLast.setOnClickListener(v -> onReinstallLastClicked());
        btnUninstallAll.setOnClickListener(v -> onUninstallAllClicked());
        overlayToggleChip.setOnClickListener(v -> toggleOverlay());

        if (isPreScopedStorage) {
            setupPreScopedStorageUi();
        } else {
            shizukuHelper = new ShizukuPermissionHelper(this, this::onShizukuStateChanged);
            btnRequestPermission.setOnClickListener(v -> shizukuHelper.requestPermission());
            maybeShowShizukuIntro();
            shizukuHelper.checkAndNotify();
        }

        lastBuildZipFile = new File(getCacheDir(), "last_build.zip");
        updateReinstallButtonVisibility();
        renderRecentList();

        loadLibraries();
    }

    // ─────────────────────────── Nút góc phải (log debug / sau này Settings) ───────────────────────────

    private static final String KEY_SHIZUKU_INTRO_SHOWN = "shizuku_intro_shown";

    /**
     * Hiện đúng 1 LẦN duy nhất (cờ SharedPreferences) ở lần mở app đầu tiên,
     * TRƯỚC khi Shizuku hỏi quyền thật — để người dùng biết đây là bước bắt
     * buộc, không phải quyền tuỳ chọn có thể lờ đi. Không hiện lại nếu
     * người dùng đã từng thấy, kể cả khi họ chưa cấp quyền.
     */
    private void maybeShowShizukuIntro() {
        if (prefs.getBoolean(KEY_SHIZUKU_INTRO_SHOWN, false)) return;
        prefs.edit().putBoolean(KEY_SHIZUKU_INTRO_SHOWN, true).apply();

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_shizuku_intro, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Echohall).setView(view).create();
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnIntroContinue).setOnClickListener(v -> {
            dialog.dismiss();
            if (shizukuHelper != null) shizukuHelper.requestPermission();
        });
        dialog.show();
    }

    private void onTopRightActionClicked() {
        // TODO(bản release): if (!BuildConfig.DEBUG) { mở SettingsActivity (sáng/tối,
        // ngôn ngữ, tài khoản) thay vì log; return; }
        showLogDialog();
    }

    private void showLogDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_log, null);
        TextView tvLog = view.findViewById(R.id.tvLog);
        tvLog.setText(logBuffer.toString());
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Echohall)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnCloseLog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void log(String message) {
        String time = DateFormat.format("HH:mm:ss", new Date()).toString();
        logBuffer.append("[").append(time).append("] ").append(message).append("\n");
    }

    // ─────────────────────────── Thư viện + picker dialog ───────────────────────────

    /**
     * Trạng thái tải thư viện — dùng để phân biệt "đang tải" (bình thường,
     * chỉ cần đợi) với "tải lỗi thật" (network fail), tránh hiện Toast lỗi
     * sai lúc người dùng bấm mở picker ngay vài giây sau khi mở app trong
     * lúc /api/wem-list vẫn đang gọi (không phải bug, chỉ là chưa xong).
     */
    private boolean wemLoading = false;
    private boolean videoLoading = false;

    private static final int MAX_AUTO_RETRIES = 3;
    // Cách nhau 5s — đủ để phủ qua thời gian "cold start" của server Render
    // free tier (sleep sau ~15 phút không traffic, có thể mất 20-30s để
    // tỉnh lại ở request đầu tiên).
    private static final long AUTO_RETRY_DELAY_MS = 5000;
    private int wemRetryCount = 0;
    private int videoRetryCount = 0;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    /**
     * Sắp xếp alphabet — dùng Collator tiếng Việt thay vì so sánh chuỗi
     * thường, để thứ tự đúng theo mặt chữ có dấu (VD: "Đêm" xếp đúng chỗ
     * chứ không bị đẩy xuống cuối như so sánh Unicode thô).
     */
    private static final Collator VI_COLLATOR = Collator.getInstance(new Locale("vi", "VN"));

    private void sortWemLibraryAlphabetically() {
        if (wemLibrary == null) return;
        Collections.sort(wemLibrary, (a, b) -> VI_COLLATOR.compare(
                a.name == null ? "" : a.name, b.name == null ? "" : b.name));
    }

    private void sortVideoLibraryAlphabetically() {
        if (videoLibrary == null) return;
        Collections.sort(videoLibrary, (a, b) -> VI_COLLATOR.compare(
                a.name == null ? "" : a.name, b.name == null ? "" : b.name));
    }

    private void loadLibraries() {
        wemLoading = true;
        videoLoading = true;
        tvSelectedWem.setText("Đang tải...");
        tvSelectedVideo.setText("Đang tải...");
        log("Đang tải thư viện nhạc/video từ server...");
        showLoadingOverlay(true, "ĐANG TẢI THƯ VIỆN...");

        apiClient.fetchWemList(new ApiClient.Callback<List<ApiClient.WemItem>>() {
            @Override
            public void onSuccess(List<ApiClient.WemItem> result) {
                wemLibrary = result;
                sortWemLibraryAlphabetically();
                wemLoading = false;
                wemRetryCount = 0;
                if (selectedWem == null) tvSelectedWem.setText("Chưa chọn");
                log("✓ Tải xong " + result.size() + " bài nhạc.");
                checkLibrariesLoadedDone();
            }

            @Override
            public void onError(String message) {
                wemLoading = false;
                log("✗ Lỗi tải danh sách nhạc: " + message);
                if (wemRetryCount < MAX_AUTO_RETRIES) {
                    wemRetryCount++;
                    log("… tự thử lại tải nhạc (lần " + wemRetryCount + "/" + MAX_AUTO_RETRIES + ")");
                    retryHandler.postDelayed(() -> {
                        wemLoading = true;
                        apiClient.fetchWemList(this);
                    }, AUTO_RETRY_DELAY_MS);
                } else {
                    if (selectedWem == null) tvSelectedWem.setText("Lỗi tải — bấm để thử lại");
                    checkLibrariesLoadedDone();
                }
            }
        });

        apiClient.fetchVideoList(new ApiClient.Callback<List<ApiClient.VideoItem>>() {
            @Override
            public void onSuccess(List<ApiClient.VideoItem> result) {
                videoLibrary = result;
                sortVideoLibraryAlphabetically();
                videoLoading = false;
                videoRetryCount = 0;
                if (selectedVideoFromLibrary == null && selectedVideoUploadUri == null) tvSelectedVideo.setText("Chưa chọn");
                log("✓ Tải xong " + result.size() + " video.");
                checkLibrariesLoadedDone();
            }

            @Override
            public void onError(String message) {
                videoLoading = false;
                log("✗ Lỗi tải danh sách video: " + message);
                if (videoRetryCount < MAX_AUTO_RETRIES) {
                    videoRetryCount++;
                    log("… tự thử lại tải video (lần " + videoRetryCount + "/" + MAX_AUTO_RETRIES + ")");
                    retryHandler.postDelayed(() -> {
                        videoLoading = true;
                        apiClient.fetchVideoList(this);
                    }, AUTO_RETRY_DELAY_MS);
                } else {
                    if (selectedVideoFromLibrary == null && selectedVideoUploadUri == null) tvSelectedVideo.setText("Lỗi tải — bấm để thử lại");
                    checkLibrariesLoadedDone();
                }
            }
        });
    }

    /**
     * Chỉ ẩn overlay loading khi CẢ HAI đã xong (thành công hoặc đã hết
     * lượt tự thử lại) — tránh trường hợp 1 cái xong trước làm overlay tắt
     * sớm trong khi cái còn lại vẫn đang lỗi.
     */
    private void checkLibrariesLoadedDone() {
        boolean wemDone = wemLibrary != null || wemRetryCount >= MAX_AUTO_RETRIES;
        boolean videoDone = videoLibrary != null || videoRetryCount >= MAX_AUTO_RETRIES;
        if (!wemDone || !videoDone) return;

        if (wemLibrary != null && videoLibrary != null) {
            showLoadingOverlay(false, null);
        } else {
            // Hết lượt tự thử lại mà vẫn lỗi — chuyển overlay sang trạng
            // thái "bấm để thử lại" thay vì cứ quay vòng vô tận.
            showLoadingRetryState();
        }
    }

    private void showLoadingOverlay(boolean show, String label) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingOverlay.setOnClickListener(null);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingErrorIcon.setVisibility(View.GONE);
        if (label != null) tvLoadingLabel.setText(label);
    }

    private void showLoadingRetryState() {
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingSpinner.setVisibility(View.GONE);
        loadingErrorIcon.setVisibility(View.VISIBLE);
        tvLoadingLabel.setText("KHÔNG TẢI ĐƯỢC THƯ VIỆN — CHẠM ĐỂ THỬ LẠI");
        loadingOverlay.setOnClickListener(v -> {
            wemRetryCount = 0;
            videoRetryCount = 0;
            loadLibraries();
        });
    }

    private void openWemPickerDialog() {
        if (wemLoading) {
            Toast.makeText(this, "Thư viện nhạc đang tải, đợi vài giây rồi bấm lại nhé.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wemLibrary == null || wemLibrary.isEmpty()) {
            // Lỗi thật (không phải đang tải) — cho phép bấm lại để thử tải lại.
            loadLibraries();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_picker, null);
        ((TextView) view.findViewById(R.id.tvPickerTitle)).setText("KHO VẬT PHẨM // NHẠC");
        EditText search = view.findViewById(R.id.etPickerSearch);
        search.setHint("Tìm bài nhạc...");
        RecyclerView rv = view.findViewById(R.id.rvPickerList);
        View emptyView = view.findViewById(R.id.tvPickerEmpty);
        rv.setLayoutManager(new LinearLayoutManager(this));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Echohall).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Tự tải nhạc lên: convert wav/mp3/ogg -> wem NGAY TRÊN MÁY (xem
        // com.echohall.kgvn.w2w.WavToWemPipeline), không qua server. Tính
        // năng này bị KHOÁ cho tới khi máy được admin duyệt (Device-based
        // Manual License Activation — xem com.echohall.kgvn.license.DeviceLicense).
        com.google.android.material.card.MaterialCardView uploadRow = view.findViewById(R.id.btnPickerUpload);
        TextView tvUploadBadge = view.findViewById(R.id.tvPickerUploadBadge);
        ((TextView) view.findViewById(R.id.tvPickerUploadTitle)).setText("TỰ TẢI NHẠC LÊN");
        boolean licenseActivated = com.echohall.kgvn.license.DeviceLicense.isCachedActivated(this);
        if (licenseActivated) {
            ((TextView) view.findViewById(R.id.tvPickerUploadSubtitle)).setText("Chọn file .wav / .mp3 / .ogg từ máy của bạn");
            tvUploadBadge.setVisibility(View.GONE);
        } else {
            ((TextView) view.findViewById(R.id.tvPickerUploadSubtitle)).setText("Cần kích hoạt theo máy trước khi dùng");
            tvUploadBadge.setText("CẦN KÍCH HOẠT");
            tvUploadBadge.setVisibility(View.VISIBLE);
        }
        uploadRow.setAlpha(1f);
        uploadRow.setClickable(true);
        uploadRow.setOnClickListener(v -> {
            if (com.echohall.kgvn.license.DeviceLicense.isCachedActivated(this)) {
                dialog.dismiss();
                pickAudioUploadLauncher.launch(new String[]{"audio/*"});
            } else {
                openLicenseActivationDialog();
            }
        });

        WemListAdapter adapter = new WemListAdapter(wemLibrary, selectedWem == null ? null : selectedWem.id,
                new WemListAdapter.Listener() {
                    @Override
                    public void onSelect(ApiClient.WemItem item) {
                        selectedWem = item;
                        selectedAudioUploadUri = null;
                        selectedLocalWemFile = null;
                        tvSelectedWem.setText(item.name);
                        updateInstallButtonEnabled();
                        refreshPreview();
                        dialog.dismiss();
                    }

                    @Override
                    public void onPlayPreview(ApiClient.WemItem item, ImageView playButton) {
                        playButton.setAlpha(0.35f);
                        apiClient.fetchWemPreviewBytes(item.id, new ApiClient.Callback<byte[]>() {
                            @Override
                            public void onSuccess(byte[] result) {
                                runOnUiThread(() -> playButton.setAlpha(1f));
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
                                runOnUiThread(() -> {
                                    playButton.setAlpha(1f);
                                    Toast.makeText(MainActivity.this, "Lỗi tải nhạc thử: " + message, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }
                });
        rv.setAdapter(adapter);

        ImageView btnPagePrev = view.findViewById(R.id.btnPagePrev);
        ImageView btnPageNext = view.findViewById(R.id.btnPageNext);
        TextView tvPageIndicator = view.findViewById(R.id.tvPageIndicator);
        adapter.setPageListener((page, totalPages) -> {
            tvPageIndicator.setText("TRANG " + (page + 1) + "/" + totalPages);
            btnPagePrev.setEnabled(page > 0);
            btnPagePrev.setAlpha(page > 0 ? 1f : 0.3f);
            btnPageNext.setEnabled(page < totalPages - 1);
            btnPageNext.setAlpha(page < totalPages - 1 ? 1f : 0.3f);
            // Tái khoá kích thước MỖI LẦN đổi trang — trang cuối có ít item
            // hơn khiến RecyclerView "muốn" báo lại kích thước tự nhiên nhỏ
            // hơn cho window; đặt lại ngay sau notifyDataSetChanged() đảm bảo
            // cửa sổ không bao giờ co theo, dù chỉ 1 khung hình.
            lockDialogHeight(dialog, 560);
        });
        btnPagePrev.setOnClickListener(v -> adapter.setPage(adapter.getCurrentPage() - 1));
        btnPageNext.setOnClickListener(v -> adapter.setPage(adapter.getCurrentPage() + 1));

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filter(s.toString());
                emptyView.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                rv.setVisibility(adapter.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        view.findViewById(R.id.btnPickerCloseIcon).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        lockDialogHeight(dialog, 560);
    }

    private void onAudioUploadSelected(Uri uri) {
        String displayName = queryDisplayName(uri);

        // CHỈ lưu lại Uri + nghe thử ngay — KHÔNG convert ở đây. Convert
        // thật sự (tốn thời gian, có thể vài phút trên máy yếu) chỉ chạy khi
        // bấm "Cài mod" (xem buildLocalAndInstall), để đổi ý chọn file khác
        // không lãng phí công convert file cũ.
        selectedWem = null;
        selectedLocalWemFile = null;
        selectedAudioUploadUri = uri;
        selectedAudioUploadName = displayName;
        tvSelectedWem.setText("(Máy) " + displayName);
        log("Đã chọn nhạc tự tải lên: " + displayName + " (sẽ convert khi bấm Cài mod).");
        startRawAudioPreview(uri);
        updateInstallButtonEnabled();
    }

    private byte[] readAssetBytes(String name) throws Exception {
        try (java.io.InputStream is = getAssets().open(name);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    /**
     * Device-based Manual License Activation: hiện Device ID (ANDROID_ID) +
     * nút "Kiểm tra kích hoạt" đọc bảng device_licenses trên Supabase. Admin
     * duyệt bằng cách tự tay thêm dòng device_id vào bảng đó qua dashboard
     * Supabase — app KHÔNG có đường tự ghi/tự kích hoạt.
     */
    private void openLicenseActivationDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_license, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Echohall).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        String deviceId = com.echohall.kgvn.license.DeviceLicense.getDeviceId(this);
        TextView tvDeviceId = view.findViewById(R.id.tvLicenseDeviceId);
        TextView tvStatus = view.findViewById(R.id.tvLicenseStatus);
        View btnCopy = view.findViewById(R.id.btnLicenseCopyId);
        View btnCheck = view.findViewById(R.id.btnLicenseCheck);
        View btnClose = view.findViewById(R.id.btnLicenseClose);

        tvDeviceId.setText(deviceId);

        if (com.echohall.kgvn.license.DeviceLicense.isCachedActivated(this)) {
            tvStatus.setText("✓ Máy này đã được kích hoạt.");
        }

        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", deviceId));
            Toast.makeText(this, "Đã sao chép Device ID", Toast.LENGTH_SHORT).show();
        });

        btnCheck.setOnClickListener(v -> {
            tvStatus.setText("Đang kiểm tra...");
            btnCheck.setEnabled(false);
            new Thread(() -> {
                com.echohall.kgvn.license.DeviceLicense.CheckResult result =
                        com.echohall.kgvn.license.DeviceLicense.checkActivationOnline(this);
                runOnUiThread(() -> {
                    btnCheck.setEnabled(true);
                    tvStatus.setText(result.message);
                    if (result.activated) {
                        Toast.makeText(this, "Đã kích hoạt thành công!", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openVideoPickerDialog() {
        if (videoLoading) {
            Toast.makeText(this, "Thư viện video đang tải, đợi vài giây rồi bấm lại nhé.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (videoLibrary == null || videoLibrary.isEmpty()) {
            loadLibraries();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_picker, null);
        ((TextView) view.findViewById(R.id.tvPickerTitle)).setText("KHO VẬT PHẨM // VIDEO");
        EditText search = view.findViewById(R.id.etPickerSearch);
        search.setHint("Tìm video...");
        RecyclerView rv = view.findViewById(R.id.rvPickerList);
        View emptyView = view.findViewById(R.id.tvPickerEmpty);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Echohall).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Tự tải video từ máy — nút phụ đặt Ở ĐẦU danh sách (dễ thấy nhất),
        // dùng thẳng ô btnPickerUpload có sẵn trong dialog_picker.xml thay
        // vì tự tạo View bằng code như bản trước.
        view.findViewById(R.id.btnPickerUpload).setOnClickListener(v -> {
            dialog.dismiss();
            pickVideoUploadLauncher.launch(new String[]{"video/*"});
        });

        VideoGridAdapter adapter = new VideoGridAdapter(videoLibrary,
                selectedVideoFromLibrary == null ? null : selectedVideoFromLibrary.id,
                item -> {
                    selectedVideoFromLibrary = item;
                    selectedVideoUploadUri = null;
                    tvSelectedVideo.setText(item.name);
                    updateInstallButtonEnabled();
                    refreshPreview();
                    dialog.dismiss();
                });
        rv.setAdapter(adapter);

        ImageView btnPagePrevV = view.findViewById(R.id.btnPagePrev);
        ImageView btnPageNextV = view.findViewById(R.id.btnPageNext);
        TextView tvPageIndicatorV = view.findViewById(R.id.tvPageIndicator);
        adapter.setPageListener((page, totalPages) -> {
            tvPageIndicatorV.setText("TRANG " + (page + 1) + "/" + totalPages);
            btnPagePrevV.setEnabled(page > 0);
            btnPagePrevV.setAlpha(page > 0 ? 1f : 0.3f);
            btnPageNextV.setEnabled(page < totalPages - 1);
            btnPageNextV.setAlpha(page < totalPages - 1 ? 1f : 0.3f);
            lockDialogHeight(dialog, 640);
        });
        btnPagePrevV.setOnClickListener(v -> adapter.setPage(adapter.getCurrentPage() - 1));
        btnPageNextV.setOnClickListener(v -> adapter.setPage(adapter.getCurrentPage() + 1));

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filter(s.toString());
                emptyView.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                rv.setVisibility(adapter.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        view.findViewById(R.id.btnPickerCloseIcon).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        lockDialogHeight(dialog, 640);
    }

    /**
     * Ép chiều cao cửa sổ dialog cố định bằng code — dù panel bên trong đã
     * khai layout_height="520dp", cửa sổ AlertDialog mặc định vẫn đo theo
     * kiểu wrap_content ở cấp window, nên nội dung có ít item hơn (trang
     * cuối) có thể khiến cả cửa sổ co lại theo. Set trực tiếp
     * WindowManager.LayoutParams để cửa sổ luôn giữ đúng 1 kích thước bất
     * kể trang nào đang hiển thị.
     */
    /**
     * Khoá chiều cao cửa sổ MÀN HÌNH CHÍNH — tương tự lockDialogHeight bên
     * dưới, nhưng áp cho chính cửa sổ Activity (Theme.Echohall cũng là
     * windowIsFloating=true nên hành xử y hệt 1 dialog nổi). Không khoá thì
     * mỗi lần thanh playback nhạc (layoutPlaybackBar) hiện/ẩn theo trạng thái
     * chọn nhạc, panel chính bị đo lại theo wrap_content và co giãn theo,
     * nhìn giật cục. 640dp đủ chỗ cho MỌI trạng thái (kể cả khi mọi khối tuỳ
     * chọn — nút cấp quyền, thanh playback, danh sách cài gần đây, progress
     * — đều đang hiện cùng lúc); trạng thái ít nội dung hơn chỉ để trống
     * khoảng dưới thay vì co cửa sổ lại.
     */
    private void lockActivityWindowHeight() {
        int px = Math.round(640 * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.height = px;
        getWindow().setAttributes(lp);
    }

    private void lockDialogHeight(AlertDialog dialog, int heightDp) {
        if (dialog.getWindow() == null) return;
        int px = Math.round(heightDp * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.height = px;
        dialog.getWindow().setAttributes(lp);
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

    private void refreshPreview() {
        boolean hasVideo = selectedVideoFromLibrary != null || selectedVideoUploadUri != null;
        boolean hasWem = selectedWem != null || selectedAudioUploadUri != null;
        if (!hasVideo && !hasWem) return;

        stopPreview();
        previewEmptyOverlay.setVisibility(View.VISIBLE);

        if (hasVideo) {
            Uri videoUri = selectedVideoUploadUri != null
                    ? selectedVideoUploadUri
                    : Uri.parse(selectedVideoFromLibrary.videoUrl == null ? "" : selectedVideoFromLibrary.videoUrl);
            try {
                previewVideo.clearVideoAspectRatio();
                previewVideo.setVideoURI(videoUri);
                previewVideo.setOnPreparedListener(mp -> {
                    mp.setVolume(0f, 0f);
                    mp.setLooping(true);
                    previewVideo.setVideoAspectRatio(mp.getVideoWidth(), mp.getVideoHeight());
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
            if (selectedAudioUploadUri != null) {
                // Nhạc tự tải lên CHƯA convert (convert chỉ chạy lúc bấm Cài
                // mod) — nghe thử bằng cách phát thẳng file gốc, y hệt lúc
                // vừa chọn (startRawAudioPreview), vì stopPreview() ở trên
                // vừa dừng nó rồi (do đổi video/mở lại dialog...).
                startRawAudioPreview(selectedAudioUploadUri);
            } else {
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
                    }
                });
            }
        }
    }

    private byte[] readFileBytes(File f) throws Exception {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    // ─────────────────────────── Cài mod (build + install) ───────────────────────────

    private void updateInstallButtonEnabled() {
        boolean permissionOk = isPreScopedStorage || currentShizukuState == ShizukuPermissionHelper.State.GRANTED;
        boolean hasVideo = selectedVideoFromLibrary != null || selectedVideoUploadUri != null;
        boolean hasWem = selectedWem != null || selectedAudioUploadUri != null;
        boolean enabled = !busy && permissionOk && hasWem && hasVideo;
        btnInstall.setEnabled(enabled);
        btnInstall.setAlpha(enabled ? 1f : 0.4f);
    }

    private void updateReinstallButtonVisibility() {
        boolean exists = lastBuildZipFile != null && lastBuildZipFile.exists();
        btnReinstallLast.setVisibility(exists ? View.VISIBLE : View.GONE);
    }

    private void onInstallClicked() {
        if (selectedWem == null && selectedAudioUploadUri == null) {
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

        showInstallOverlay();
        setInstallStage("Đang chuẩn bị...");
        setBusyUi(true, "Đang cài mod...");

        if (selectedAudioUploadUri != null) {
            // Nhạc tự tải lên -> build HOÀN TOÀN trên máy (không gọi
            // /api/build của server) — xem LocalModBuilder. Convert
            // wav/mp3/ogg -> wem CHỈ diễn ra ở đây, lúc đã chắc chắn bấm cài.
            log("Bắt đầu Cài mod OFFLINE: nhạc=" + selectedAudioUploadName);
            buildLocalAndInstall();
            return;
        }

        log("Bắt đầu build mod: nhạc=" + selectedWem.name);
        setInstallStage("Đang build trên server");

        if (selectedVideoUploadUri != null) {
            new Thread(() -> {
                try {
                    byte[] videoBytes = readAllBytesFromUri(selectedVideoUploadUri);
                    runOnUiThread(() -> callBuildApi(selectedWem.id, null, videoBytes, selectedVideoUploadName));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setBusyUi(false, null);
                        hideInstallOverlay();
                        log("✗ Lỗi đọc file video: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Lỗi đọc file video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        } else {
            callBuildApi(selectedWem.id, selectedVideoFromLibrary.id, null, null);
        }
    }

    /**
     * Nhánh build offline cho nhạc TỰ TẢI LÊN: convert wav/mp3/ogg -> wem
     * (chỉ chạy TỚI ĐÂY, lúc đã bấm Cài mod), đọc video (từ upload hoặc tải
     * thẳng videoUrl public của item trong thư viện), tách audio khỏi video
     * bằng MediaExtractor/MediaMuxer (thay ffmpeg của server), rồi giao cho
     * LocalModBuilder tự lấy config Supabase + patch bnk + đóng gói zip.
     */
    private void buildLocalAndInstall() {
        new Thread(() -> {
            try {
                runOnUiThread(() -> setInstallStage("Đang chuyển đổi tệp âm thanh"));
                byte[] codebookBytes = readAssetBytes("packed_codebooks_aoTuV_603.bin");
                File w2wWorkDir = new File(getCacheDir(), "w2w_work");
                com.echohall.kgvn.w2w.WavToWemPipeline.Result convertResult =
                        com.echohall.kgvn.w2w.WavToWemPipeline.convert(
                                this, selectedAudioUploadUri, selectedAudioUploadName, w2wWorkDir,
                                codebookBytes, msg -> runOnUiThread(() -> log(msg)));
                selectedLocalWemFile = convertResult.wemFile;
                selectedLocalWemDurationMs = convertResult.durationMs;
                runOnUiThread(() -> log("✓ Convert xong: " + convertResult.wemFile.getName()
                        + " — " + convertResult.durationMs + " ms."));

                byte[] videoBytesRaw;
                if (selectedVideoUploadUri != null) {
                    runOnUiThread(() -> setInstallStage("Đang đọc video từ máy"));
                    videoBytesRaw = readAllBytesFromUri(selectedVideoUploadUri);
                } else {
                    runOnUiThread(() -> {
                        setInstallStage("Đang tải video từ server");
                        log("Đang tải video từ thư viện (mạng chậm có thể mất vài phút, app sẽ tự thử lại tối đa 3 lần nếu rớt mạng)...");
                    });
                    videoBytesRaw = LocalModBuilder.httpGetBytes(selectedVideoFromLibrary.videoUrl);
                }

                File workDir = new File(getCacheDir(), "local_build_work");
                if (!workDir.exists()) workDir.mkdirs();
                File rawVideoFile = com.echohall.kgvn.localbuild.VideoAudioStripper.bytesToTempFile(
                        videoBytesRaw, workDir, "raw_video_" + System.currentTimeMillis() + ".mp4");

                runOnUiThread(() -> setInstallStage("Đang xử lý video"));
                com.echohall.kgvn.localbuild.VideoAudioStripper.Result stripResult =
                        com.echohall.kgvn.localbuild.VideoAudioStripper.stripAudio(
                                rawVideoFile, workDir, msg -> runOnUiThread(() -> log(msg)));
                byte[] finalVideoBytes = readFileBytes(stripResult.outputFile);

                LocalModBuilder.BuildInput input = new LocalModBuilder.BuildInput();
                input.wemBytes = readFileBytes(selectedLocalWemFile);
                input.wemDurationMs = selectedLocalWemDurationMs;
                input.videoBytes = finalVideoBytes;

                runOnUiThread(() -> setInstallStage("Đang ghép file mod"));
                LocalModBuilder.BuildOutput output = LocalModBuilder.build(input,
                        msg -> runOnUiThread(() -> log(msg)));

                try (FileOutputStream fos = new FileOutputStream(lastBuildZipFile)) {
                    fos.write(output.zipBytes);
                }

                runOnUiThread(() -> {
                    log("✓ Build OFFLINE xong (" + output.zipBytes.length + " bytes). " + output.reportText);
                    updateReinstallButtonVisibility();
                    installFromCachedZip("Đang cài vào game...", true);
                });
            } catch (Throwable e) {
                // Throwable: UnsatisfiedLinkError (thiếu libmwem.so / máy
                // không phải arm64) là Error chứ không phải Exception.
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    hideInstallOverlay();
                    log("✗ Cài mod offline thất bại: " + msg);
                    Toast.makeText(MainActivity.this, "Cài mod thất bại: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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
                    hideInstallOverlay();
                    log("✗ Lỗi lưu file build: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Lỗi lưu file build: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                updateReinstallButtonVisibility();
                installFromCachedZip("Đang cài vào game...", true);
            }

            @Override
            public void onError(String message) {
                setBusyUi(false, null);
                hideInstallOverlay();
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
        showInstallOverlay();
        setInstallStage("Đang cài lại bản gần nhất");
        setBusyUi(true, "Đang cài lại bản gần nhất...");
        log("Cài lại từ cache (không gọi server): " + lastBuildZipFile.getName());
        installFromCachedZip("Đang cài lại bản gần nhất...", false);
    }

    private void installFromCachedZip(String initialStatusLabel, boolean saveToRecent) {
        tvProgressLabel.setText(initialStatusLabel);
        modInstaller.installFromZip(Uri.fromFile(lastBuildZipFile), new ModInstaller.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String currentFileName) {
                runOnUiThread(() -> {
                    int percent = total == 0 ? 0 : (int) (100.0 * current / total);
                    progressBar.setProgress(percent);
                    tvProgressLabel.setText("Đang cài (" + current + "/" + total + ")");
                    setInstallStage("Đang cài vào game", percent, current + "/" + total + " file");
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
                    // Toàn bộ onStatus() ở bước fix ISPDiff diễn ra TRƯỚC vòng
                    // lặp copy từng file (xem ModInstaller.installFromZip) —
                    // nên map chung về 1 nhãn đơn giản, chi tiết thật vẫn ghi
                    // vào log() cho ai cần xem kỹ.
                    setInstallStage("Đang làm sạch đường dẫn", 0, message);
                    log("… " + message);
                });
            }

            @Override
            public void onDone(int installedCount) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    hideInstallOverlay();
                    log("✓ Hoàn tất — đã cài " + installedCount + " file.");
                    Toast.makeText(MainActivity.this, "Cài mod thành công (" + installedCount + " file)", Toast.LENGTH_LONG).show();
                    if (saveToRecent) saveCurrentSelectionToRecent();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusyUi(false, null);
                    hideInstallOverlay();
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

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        AlertDialog confirmDialog = new AlertDialog.Builder(this, R.style.Theme_Echohall).setView(view).create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnConfirmCancel).setOnClickListener(v -> confirmDialog.dismiss());
        view.findViewById(R.id.btnConfirmOk).setOnClickListener(v -> {
            confirmDialog.dismiss();
            performUninstallAll();
        });
        confirmDialog.show();
    }

    private void performUninstallAll() {
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

    // ─────────────────────────── "Cài gần đây" (SharedPreferences) ───────────────────────────
    // Chỉ lưu được tổ hợp dùng video TỪ THƯ VIỆN (có id ổn định). Video tự
    // upload không lưu lại vào lịch sử vì Uri của SAF picker không đảm bảo
    // còn quyền đọc được sau khi app khởi động lại.

    private void saveCurrentSelectionToRecent() {
        if (selectedWem == null || selectedVideoFromLibrary == null) return;
        try {
            JSONArray arr = readRecentArray();
            JSONObject entry = new JSONObject();
            entry.put("wemId", selectedWem.id);
            entry.put("wemName", selectedWem.name);
            entry.put("videoId", selectedVideoFromLibrary.id);
            entry.put("videoName", selectedVideoFromLibrary.name);
            entry.put("displayName", selectedVideoFromLibrary.name + " x " + selectedWem.name);
            entry.put("timestamp", System.currentTimeMillis());

            JSONArray newArr = new JSONArray();
            newArr.put(entry);
            for (int i = 0; i < arr.length() && newArr.length() < MAX_RECENT; i++) {
                JSONObject old = arr.getJSONObject(i);
                if (old.optString("wemId").equals(selectedWem.id)
                        && old.optString("videoId").equals(selectedVideoFromLibrary.id)) continue;
                newArr.put(old);
            }
            prefs.edit().putString(KEY_RECENT_LIST, newArr.toString()).apply();
            renderRecentList();
        } catch (Exception e) {
            log("⚠ Không lưu được lịch sử cài gần đây: " + e.getMessage());
        }
    }

    private JSONArray readRecentArray() {
        try {
            String raw = prefs.getString(KEY_RECENT_LIST, "[]");
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void renderRecentList() {
        layoutRecentList.removeAllViews();
        JSONArray arr = readRecentArray();
        if (arr.length() == 0) {
            tvRecentLabel.setVisibility(View.GONE);
            return;
        }
        tvRecentLabel.setVisibility(View.VISIBLE);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject entry = arr.optJSONObject(i);
            if (entry == null) continue;
            View row = LayoutInflater.from(this).inflate(R.layout.item_recent_row, layoutRecentList, false);
            ((TextView) row.findViewById(R.id.tvRecentName)).setText(entry.optString("displayName"));
            long ts = entry.optLong("timestamp", 0);
            ((TextView) row.findViewById(R.id.tvRecentTime)).setText(
                    ts > 0 ? DateUtils.getRelativeTimeSpanString(ts).toString() : "");
            row.setOnClickListener(v -> applyRecentEntry(entry));
            layoutRecentList.addView(row);
        }
    }

    private void applyRecentEntry(JSONObject entry) {
        if (wemLibrary == null || videoLibrary == null) {
            Toast.makeText(this, "Thư viện chưa tải xong, thử lại sau.", Toast.LENGTH_SHORT).show();
            return;
        }
        String wemId = entry.optString("wemId");
        String videoId = entry.optString("videoId");
        ApiClient.WemItem foundWem = null;
        for (ApiClient.WemItem w : wemLibrary) if (w.id != null && w.id.equals(wemId)) foundWem = w;
        ApiClient.VideoItem foundVideo = null;
        for (ApiClient.VideoItem v : videoLibrary) if (v.id != null && v.id.equals(videoId)) foundVideo = v;

        if (foundWem == null || foundVideo == null) {
            Toast.makeText(this, "Nhạc/video này không còn trong thư viện (đã bị admin xoá hoặc đổi).", Toast.LENGTH_LONG).show();
            return;
        }
        selectedWem = foundWem;
        selectedVideoFromLibrary = foundVideo;
        selectedVideoUploadUri = null;
        tvSelectedWem.setText(selectedWem.name);
        tvSelectedVideo.setText(selectedVideoFromLibrary.name);
        updateInstallButtonEnabled();
        refreshPreview();
    }

    // ─────────────────────────── Overlay preview toggle ───────────────────────────

    private void toggleOverlay() {
        overlayOn = !overlayOn;
        previewOverlayImg.setVisibility(overlayOn ? View.VISIBLE : View.GONE);
        tvOverlayToggleState.setTextColor(overlayOn ? 0xFF80c8f8 : 0xFF386080);
    }

    // ─────────────────── Overlay tiến trình "Cài mod" ───────────────────
    // Hiện từ lúc bấm KÍCH HOẠT TRANG BỊ tới lúc xong hẳn (hoặc lỗi), che
    // toàn bộ nội dung. Nhãn CHỈ dùng từ ngữ đơn giản, không mô tả kỹ thuật
    // chi tiết bên trong (bnk, HIRC, sourceId...) — log chi tiết vẫn ghi
    // riêng qua log() cho ai cần xem.

    private void showInstallOverlay() {
        installProgressOverlay.setVisibility(View.VISIBLE);
        installOverlayProgressBar.setProgress(0);
        tvInstallProgressDetail.setText("");
    }

    private void hideInstallOverlay() {
        installProgressOverlay.setVisibility(View.GONE);
    }

    private void setInstallStage(String simpleLabel) {
        tvInstallProgressLabel.setText(simpleLabel);
    }

    private void setInstallStage(String simpleLabel, int percent, String detail) {
        tvInstallProgressLabel.setText(simpleLabel);
        installOverlayProgressBar.setProgress(Math.max(0, Math.min(100, percent)));
        tvInstallProgressDetail.setText(detail == null ? "" : detail);
    }

    private void stopPreview() {
        try {
            if (previewVideo != null && previewVideo.isPlaying()) previewVideo.stopPlayback();
        } catch (Exception ignored) {
        }
        previewDecoder.stopAudio();
        stopRawAudioPreview();
    }

    private void stopRawAudioPreview() {
        if (rawAudioPreviewPlayer != null) {
            try {
                rawAudioPreviewPlayer.stop();
            } catch (Exception ignored) {
            }
            rawAudioPreviewPlayer.release();
            rawAudioPreviewPlayer = null;
        }
    }

    /**
     * Phát NGAY file audio gốc (wav/mp3/ogg) vừa chọn, không đợi convert wem.
     * Người dùng bấm "TỰ TẢI NHẠC LÊN" là nghe được liền, dù convert phía sau
     * có mất vài phút.
     */
    private void startRawAudioPreview(Uri uri) {
        stopRawAudioPreview();
        try {
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            mp.setOnPreparedListener(p -> p.start());
            mp.setOnErrorListener((p, what, extra) -> {
                log("⚠ Nghe thử (file gốc) lỗi (what=" + what + ")");
                return true;
            });
            mp.prepareAsync();
            rawAudioPreviewPlayer = mp;
        } catch (Exception e) {
            log("⚠ Không nghe thử được file gốc: " + e.getMessage());
        }
    }

    // ─────────────────────────── Shizuku ───────────────────────────

    private void setupPreScopedStorageUi() {
        setDotColor(0xFF80c8f8);
        tvShizukuStatus.setText("SYSTEM // API " + Build.VERSION.SDK_INT);
        btnRequestPermission.setVisibility(View.GONE);
        setActionButtonsLocked(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isPreScopedStorage && shizukuHelper != null) {
            shizukuHelper.checkAndNotify();
        }
        startPlaybackPolling();
    }

    // Trước đây chỉ dừng nhạc ở onDestroy() — nhưng bấm nút Home (hoặc chuyển
    // app khác) chỉ gọi onPause()/onStop(), Activity vẫn sống trong bộ nhớ,
    // nên nhạc trong WebView ẩn tiếp tục phát nền dù người dùng tưởng đã
    // thoát app. Dừng ngay tại onPause() để khớp đúng cảm giác "thoát là tắt".
    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        stopPlaybackPolling();
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

    private void setDotColor(int color) {
        if (tvShizukuDotView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) tvShizukuDotView.getBackground().mutate()).setColor(color);
        }
    }

    private void onShizukuStateChanged(ShizukuPermissionHelper.State state) {
        currentShizukuState = state;
        switch (state) {
            case BINDER_NOT_AVAILABLE:
                setDotColor(0xFFe05a5a);
                tvShizukuStatus.setText("SHIZUKU // CHƯA CHẠY");
                tvRequestPermissionLabel.setText("KIỂM TRA SHIZUKU");
                btnRequestPermission.setVisibility(View.VISIBLE);
                break;
            case PERMISSION_DENIED:
                setDotColor(0xFFe9c846);
                tvShizukuStatus.setText("SHIZUKU // CHƯA CẤP QUYỀN");
                tvRequestPermissionLabel.setText("CẤP QUYỀN SHIZUKU");
                btnRequestPermission.setVisibility(View.VISIBLE);
                break;
            case GRANTED:
                setDotColor(0xFF6fcf6f);
                tvShizukuStatus.setText("SHIZUKU // SẴN SÀNG");
                btnRequestPermission.setVisibility(View.GONE);
                break;
        }
        setActionButtonsLocked(state != ShizukuPermissionHelper.State.GRANTED);
        updateInstallButtonEnabled();
    }

    private void setActionButtonsLocked(boolean locked) {
        btnUninstallAll.setEnabled(!locked);
        btnUninstallAll.setAlpha(locked ? 0.4f : 1f);
        cardChooseWem.setEnabled(!locked);
        cardChooseVideo.setEnabled(!locked);
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
        }
    }
}
