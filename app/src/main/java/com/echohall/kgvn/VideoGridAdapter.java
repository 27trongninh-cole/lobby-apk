package com.echohall.kgvn;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VH> {

    /** MainActivity gán hàm log() của nó vào đây để lỗi tải thumbnail cũng
     * hiện được trong màn LOG.DEBUG của app — không bắt người dùng phải mở
     * logcat qua máy tính mới xem được lý do lỗi. */
    public interface ThumbLogger {
        void log(String message);
    }

    public static ThumbLogger logger;

    private static void logThumb(String message) {
        android.util.Log.w("MeloThumb", message);
        if (logger != null) logger.log("⚠ " + message);
    }

    /** 3 hàng x 2 cột = 6 video/trang — khớp chiều cao cố định 520dp của dialog_picker. */
    public static final int PAGE_SIZE = 6;

    public interface Listener {
        void onSelect(ApiClient.VideoItem item);
    }

    public interface PageListener {
        void onPageInfoChanged(int currentPage, int totalPages);
    }

    // Cache khung hình đầu đã trích xuất trong bộ nhớ (key = video_url) để
    // không phải tải lại video mỗi lần cuộn qua cuộn lại trong lưới, giống
    // trình duyệt tự cache <video> đã load 1 lần trên web.
    private static final LruCache<String, Bitmap> FRAME_CACHE = new LruCache<>(24);
    private static final ExecutorService THUMB_EXECUTOR = Executors.newFixedThreadPool(3);
    // Executor riêng chỉ để CHỜ CÓ GIỚI HẠN THỜI GIAN kết quả trích xuất —
    // không trực tiếp làm việc nặng, nên không lo tắc nghẽn dù nhiều video
    // cùng bị treo (xem ghi chú TIMEOUT bên dưới).
    private static final ExecutorService WATCHDOG_EXECUTOR = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> inFlight = new HashSet<>();

    private final List<ApiClient.VideoItem> all;
    private List<ApiClient.VideoItem> filtered;
    private final Listener listener;
    private PageListener pageListener;
    private String selectedId;
    private int currentPage = 0;

    public VideoGridAdapter(List<ApiClient.VideoItem> items, String selectedId, Listener listener) {
        this.all = new ArrayList<>(items);
        this.filtered = new ArrayList<>(items);
        this.selectedId = selectedId;
        this.listener = listener;
    }

    public void setPageListener(PageListener l) {
        this.pageListener = l;
        notifyPageInfo();
    }

    private void notifyPageInfo() {
        if (pageListener != null) pageListener.onPageInfoChanged(currentPage, getTotalPages());
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
    }

    public void setPage(int page) {
        int clamped = Math.max(0, Math.min(page, getTotalPages() - 1));
        if (clamped == currentPage) return;
        currentPage = clamped;
        notifyDataSetChanged();
        notifyPageInfo();
    }

    public void setSelectedId(String id) {
        this.selectedId = id;
        notifyDataSetChanged();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(n).replaceAll("");
        return n.toLowerCase(Locale.ROOT).replace('đ', 'd').replace('Đ', 'd');
    }

    public void filter(String query) {
        String q = normalize(query == null ? "" : query.trim());
        filtered = new ArrayList<>();
        for (ApiClient.VideoItem v : all) {
            if (q.isEmpty() || normalize(v.name).contains(q)) filtered.add(v);
        }
        currentPage = 0;
        notifyDataSetChanged();
        notifyPageInfo();
    }

    public boolean isEmpty() {
        return filtered.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ApiClient.VideoItem item = filtered.get(currentPage * PAGE_SIZE + position);
        h.name.setText(item.name);
        boolean selected = item.id != null && item.id.equals(selectedId);
        h.badge.setVisibility(selected ? View.VISIBLE : View.GONE);
        h.card.setStrokeColor(ContextCompat.getColor(h.card.getContext(),
                selected ? R.color.hud_accent : R.color.hud_border));
        h.card.setStrokeWidth(selected ? dp(h.card.getContext(), 2) : dp(h.card.getContext(), 1));
        h.itemView.setOnClickListener(v -> listener.onSelect(item));

        h.fallback.setVisibility(View.GONE);
        h.thumb.setVisibility(View.VISIBLE);

        if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
            // Có sẵn ảnh thumbnail admin upload — dùng luôn, không cần trích
            // khung hình từ video (nhanh hơn nhiều). TRƯỚC ĐÂY không có
            // .listener(...) nên nếu Glide tải lỗi (link hỏng, hết hạn...)
            // ảnh chỉ im lặng không hiện gì — khung nhìn trống trơn không rõ
            // lý do. Giờ bắt lỗi rõ ràng và bật icon dự phòng khi fail.
            Glide.with(h.thumb.getContext())
                    .load(new com.bumptech.glide.load.model.GlideUrl(item.thumbnailUrl,
                            new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                                    .build()))
                    .centerCrop()
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model,
                                                     com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                     boolean isFirstResource) {
                            logThumb("Glide lỗi tải ảnh bìa " + item.thumbnailUrl + ": " + (e != null ? e.getMessage() : "null"));
                            h.thumb.setVisibility(View.GONE);
                            h.fallback.setVisibility(View.VISIBLE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                        com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            h.thumb.setVisibility(View.VISIBLE);
                            h.fallback.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(h.thumb);
        } else if (item.videoUrl != null && !item.videoUrl.isEmpty()) {
            // Không có thumbnail_url — trích khung hình ĐẦU của video, đúng
            // như cách web lấy frame đầu từ thẻ <video> khi thiếu ảnh bìa.
            loadFirstFrame(h, item.videoUrl);
        } else {
            h.thumb.setVisibility(View.GONE);
            h.fallback.setVisibility(View.VISIBLE);
        }
    }

    private void loadFirstFrame(VH h, String videoUrl) {
        Bitmap cached = FRAME_CACHE.get(videoUrl);
        if (cached != null) {
            h.thumb.setImageBitmap(cached);
            return;
        }

        // Đặt bitmap rỗng tạm thời (nền placeholder có sẵn từ background),
        // đánh dấu tag để tránh set nhầm ảnh khi ViewHolder bị tái sử dụng
        // trong lúc đang trích xuất (RecyclerView recycle khi cuộn nhanh).
        h.thumb.setImageDrawable(null);
        h.thumb.setTag(videoUrl);

        synchronized (inFlight) {
            if (inFlight.contains(videoUrl)) return;
            inFlight.add(videoUrl);
        }

        THUMB_EXECUTOR.execute(() -> {
            Future<Bitmap> future = WATCHDOG_EXECUTOR.submit((Callable<Bitmap>) () -> {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    // THÊM User-Agent giả lập trình duyệt di động — 1 số CDN/anti-hotlink
                    // chặn hoặc trả lỗi cho request không có User-Agent hợp lệ (client Java
                    // mặc định không gửi UA, khác hẳn <video> trên web luôn có UA trình duyệt).
                    java.util.HashMap<String, String> headers = new java.util.HashMap<>();
                    headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
                    retriever.setDataSource(videoUrl, headers);
                    return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } finally {
                    try {
                        retriever.release();
                    } catch (Exception ignored) {
                    }
                }
            });

            Bitmap frame = null;
            try {
                // GHI CHÚ TIMEOUT: video mp4 không bật "faststart" (moov atom
                // nằm ở CUỐI file thay vì đầu) khiến MediaMetadataRetriever
                // phải tải gần hết file qua mạng chỉ để đọc được 1 khung hình
                // đầu — có thể treo rất lâu hoặc vô thời hạn với video nặng.
                // Giới hạn 6s để không kẹt UI/luồng vô thời hạn; nếu server
                // export video bằng ffmpeg, khuyến khích thêm cờ
                // "-movflags +faststart" để việc này luôn nhanh.
                frame = future.get(6, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                logThumb("Timeout trích khung hình: " + videoUrl);
            } catch (Exception e) {
                // TRƯỚC ĐÂY lỗi này bị nuốt hoàn toàn im lặng — không cách nào
                // biết được lý do thật sự khiến thumbnail luôn trống. Giờ log
                // rõ loại lỗi + URL để lần sau xem LOG.DEBUG là biết ngay.
                logThumb("Lỗi trích khung hình từ " + videoUrl + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
            } finally {
                synchronized (inFlight) {
                    inFlight.remove(videoUrl);
                }
            }

            Bitmap finalFrame = frame;
            mainHandler.post(() -> {
                if (finalFrame != null) {
                    FRAME_CACHE.put(videoUrl, finalFrame);
                }
                // Chỉ set ảnh nếu ViewHolder này vẫn đang hiển thị đúng video
                // đó (chưa bị tái sử dụng cho item khác trong lúc chờ).
                if (videoUrl.equals(h.thumb.getTag())) {
                    if (finalFrame != null) {
                        h.thumb.setVisibility(View.VISIBLE);
                        h.fallback.setVisibility(View.GONE);
                        h.thumb.setImageBitmap(finalFrame);
                    } else {
                        h.thumb.setVisibility(View.GONE);
                        h.fallback.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        int start = currentPage * PAGE_SIZE;
        return Math.max(0, Math.min(PAGE_SIZE, filtered.size() - start));
    }

    private static int dp(android.content.Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView thumb, fallback;
        TextView name, badge;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardVideoThumb);
            thumb = itemView.findViewById(R.id.ivVideoThumb);
            fallback = itemView.findViewById(R.id.ivVideoThumbFallback);
            name = itemView.findViewById(R.id.tvVideoName);
            badge = itemView.findViewById(R.id.tvVideoSelectedBadge);
        }
    }
}

