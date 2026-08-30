package com.echohall.kgvn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VH> {

    public interface Listener {
        void onSelect(ApiClient.VideoItem item);
    }

    private final List<ApiClient.VideoItem> all;
    private List<ApiClient.VideoItem> filtered;
    private final Listener listener;
    private String selectedId;

    public VideoGridAdapter(List<ApiClient.VideoItem> items, String selectedId, Listener listener) {
        this.all = new ArrayList<>(items);
        this.filtered = new ArrayList<>(items);
        this.selectedId = selectedId;
        this.listener = listener;
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
        notifyDataSetChanged();
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
        ApiClient.VideoItem item = filtered.get(position);
        h.name.setText(item.name);
        h.itemView.setSelected(item.id != null && item.id.equals(selectedId));
        h.itemView.setOnClickListener(v -> listener.onSelect(item));

        if (item.thumbnailUrl != null && !item.thumbnailUrl.isEmpty()) {
            h.fallback.setVisibility(View.GONE);
            h.thumb.setVisibility(View.VISIBLE);
            Glide.with(h.thumb.getContext())
                    .load(item.thumbnailUrl)
                    .centerCrop()
                    .into(h.thumb);
        } else {
            // Không có thumbnail_url (giống web: fallback dùng chính thẻ
            // <video> để lấy khung hình đầu) — ở đây đơn giản hoá bằng icon
            // 🎬 thay vì tự trích khung hình từ video_url (tốn thời gian +
            // băng thông cho mỗi ô lưới).
            h.thumb.setVisibility(View.GONE);
            h.fallback.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return filtered.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView fallback, name;

        VH(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.ivVideoThumb);
            fallback = itemView.findViewById(R.id.tvVideoThumbFallback);
            name = itemView.findViewById(R.id.tvVideoName);
        }
    }
}
