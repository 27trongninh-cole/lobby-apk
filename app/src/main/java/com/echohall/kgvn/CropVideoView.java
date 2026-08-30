package com.echohall.kgvn;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * VideoView mặc định luôn fit-và-letterbox (giữ nguyên tỉ lệ, co nhỏ lại để
 * vừa khung, để lộ viền đen nếu tỉ lệ khung khác tỉ lệ video) — không có cách
 * nào bật "crop lấp đầy" như CSS `object-fit:cover` mà web đang dùng cho
 * .preview-video-wrap video.
 *
 * View này giả lập đúng hành vi object-fit:cover: đo kích thước lớn hơn
 * khung chứa (theo đúng tỉ lệ thật của video, lấy từ onPrepared), rồi để
 * FrameLayout cha (mặc định clipChildren=true) cắt phần dư ra ngoài — kết
 * quả là video lấp đầy khung, phần thừa 2 bên hoặc trên dưới bị crop, không
 * còn viền đen.
 *
 * Cách dùng: sau khi có MediaPlayer trong onPreparedListener, gọi
 * setVideoAspectRatio(mp.getVideoWidth(), mp.getVideoHeight()).
 */
public class CropVideoView extends VideoView {

    private int videoWidth = 0;
    private int videoHeight = 0;

    public CropVideoView(Context context) {
        super(context);
    }

    public CropVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CropVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Gọi khi biết được kích thước thật của video (thường trong onPrepared). */
    public void setVideoAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.videoWidth = width;
        this.videoHeight = height;
        requestLayout();
    }

    /** Reset về hành vi mặc định (chưa biết kích thước video) — gọi khi đổi nguồn phát. */
    public void clearVideoAspectRatio() {
        videoWidth = 0;
        videoHeight = 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int containerWidth = MeasureSpec.getSize(widthMeasureSpec);
        int containerHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (videoWidth <= 0 || videoHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        float containerRatio = (float) containerWidth / containerHeight;
        float videoRatio = (float) videoWidth / videoHeight;

        int measuredWidth;
        int measuredHeight;
        if (videoRatio > containerRatio) {
            // Video "rộng" hơn khung (theo tỉ lệ) -> khớp theo chiều cao,
            // tràn ra 2 bên theo chiều ngang -> bị crop trái/phải.
            measuredHeight = containerHeight;
            measuredWidth = Math.round(measuredHeight * videoRatio);
        } else {
            // Video "cao" hơn khung (vd video dọc 9:16 trong khung ngang
            // 16:9) -> khớp theo chiều ngang, tràn ra trên/dưới -> bị crop
            // trên/dưới. Đây đúng là trường hợp video sảnh 9:16 hiện tại.
            measuredWidth = containerWidth;
            measuredHeight = Math.round(measuredWidth / videoRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
