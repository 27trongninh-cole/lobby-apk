package com.echohall.kgvn;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

/**
 * TRƯỚC ĐÂY view này extends android.widget.VideoView (dùng SurfaceView bên
 * trong) — vẫn tính đúng kích thước "tràn khung để crop" (xem onMeasure bên
 * dưới), NHƯNG SurfaceView hiển thị bằng 1 lớp hợp thành phần cứng RIÊNG,
 * xuyên thẳng qua toàn bộ view hierarchy — nó KHÔNG tuân theo bất kỳ clipping
 * nào từ View cha (clipChildren, clipToOutline, bo góc MaterialCardView...).
 * Kết quả: phần video "tràn ra" để tạo hiệu ứng crop không hề bị cắt, mà lộ
 * thẳng ra ngoài toàn bộ khung app.
 *
 * TextureView thì khác — nó là 1 View bình thường, render vào 1 texture
 * OpenGL rồi vẽ như ảnh bitmap qua đúng pipeline vẽ thông thường của
 * Android, nên tuân theo mọi clipping/bo góc/animation như bất kỳ View nào
 * khác. Đổi sang TextureView + tự quản lý MediaPlayer là cách DUY NHẤT vừa
 * giữ được hiệu ứng crop-fill (object-fit:cover) vừa bị cắt đúng theo khung.
 */
public class CropVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    private int videoWidth = 0;
    private int videoHeight = 0;

    private MediaPlayer mediaPlayer;
    private Uri pendingUri;
    private Surface surface;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnErrorListener errorListener;

    public CropVideoView(Context context) {
        super(context);
        init();
    }

    public CropVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    // ─────────────────────── API tương thích với VideoView cũ ───────────────────────
    // Giữ nguyên tên hàm/kiểu tham số như VideoView để MainActivity không cần sửa gì.

    public void setVideoURI(Uri uri) {
        this.pendingUri = uri;
        if (surface != null) openVideo();
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        this.preparedListener = listener;
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        this.errorListener = listener;
    }

    public void start() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public void stopPlayback() {
        releasePlayer();
    }

    private void openVideo() {
        if (pendingUri == null || surface == null) return;
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setSurface(surface);
        if (errorListener != null) mediaPlayer.setOnErrorListener(errorListener);
        if (preparedListener != null) mediaPlayer.setOnPreparedListener(preparedListener);
        try {
            mediaPlayer.setDataSource(getContext(), pendingUri);
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            if (errorListener != null) errorListener.onError(mediaPlayer, 0, 0);
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ─────────────────────── Crop-fill (object-fit: cover) ───────────────────────

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

    // ─────────────────────── SurfaceTextureListener ───────────────────────

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        if (pendingUri != null) openVideo();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }
}
