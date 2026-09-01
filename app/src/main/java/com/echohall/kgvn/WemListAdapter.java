package com.echohall.kgvn;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WemListAdapter extends RecyclerView.Adapter<WemListAdapter.VH> {

    /** Số bài nhạc hiện trên 1 trang — khớp với chiều cao cố định 520dp của dialog_picker. */
    public static final int PAGE_SIZE = 6;

    public interface Listener {
        void onSelect(ApiClient.WemItem item);
        void onPlayPreview(ApiClient.WemItem item, ImageView playButton);
    }

    /** Báo cho MainActivity biết trang hiện tại / tổng số trang đã đổi, để cập nhật UI điều hướng. */
    public interface PageListener {
        void onPageInfoChanged(int currentPage, int totalPages);
    }

    private final List<ApiClient.WemItem> all;
    private List<ApiClient.WemItem> filtered;
    private final Listener listener;
    private PageListener pageListener;
    private String selectedId;
    private int currentPage = 0;

    public WemListAdapter(List<ApiClient.WemItem> items, String selectedId, Listener listener) {
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

    /** Bỏ dấu tiếng Việt để tìm kiếm không phân biệt dấu, giống chuẩn hoá trên web. */
    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(n).replaceAll("");
        return n.toLowerCase(Locale.ROOT).replace('đ', 'd').replace('Đ', 'd');
    }

    public void filter(String query) {
        String q = normalize(query == null ? "" : query.trim());
        filtered = new ArrayList<>();
        for (ApiClient.WemItem w : all) {
            if (q.isEmpty() || normalize(w.name).contains(q)) filtered.add(w);
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wem_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ApiClient.WemItem item = filtered.get(currentPage * PAGE_SIZE + position);
        h.name.setText(item.name);
        h.duration.setText(formatDuration(item.durationMs == null ? 0 : item.durationMs));

        boolean selected = item.id != null && item.id.equals(selectedId);
        h.badge.setVisibility(selected ? View.VISIBLE : View.GONE);
        h.card.setStrokeColor(ContextCompat.getColor(h.card.getContext(),
                selected ? R.color.hud_accent : R.color.hud_border));
        h.card.setCardBackgroundColor(ContextCompat.getColor(h.card.getContext(),
                selected ? R.color.hud_accent_bg_muted : R.color.hud_surface_1));

        h.itemView.setOnClickListener(v -> listener.onSelect(item));
        h.playBtn.setImageResource(R.drawable.ic_play);
        h.playBtn.setAlpha(1f);
        h.playBtn.setOnClickListener(v -> listener.onPlayPreview(item, h.playBtn));
    }

    @Override
    public int getItemCount() {
        int start = currentPage * PAGE_SIZE;
        return Math.max(0, Math.min(PAGE_SIZE, filtered.size() - start));
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "--:--";
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.US, "%d:%02d", min, sec);
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView name, duration, badge;
        ImageView playBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            name = itemView.findViewById(R.id.tvWemName);
            duration = itemView.findViewById(R.id.tvWemDuration);
            badge = itemView.findViewById(R.id.tvWemSelectedBadge);
            playBtn = itemView.findViewById(R.id.btnWemPlay);
        }
    }
}
