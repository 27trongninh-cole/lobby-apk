package com.echohall.kgvn.localbuild;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Thay thế server/lib/stripAudio.js (dùng ffmpeg-static "-c copy -an", KHÔNG
 * chạy được trên Android). Ở đây dùng MediaExtractor + MediaMuxer chuẩn
 * Android SDK: chỉ COPY track video (không giải mã lại, không mất chất
 * lượng, rất nhanh), bỏ hẳn track audio ra khỏi container — cùng hiệu ứng
 * với "-c copy -an" của ffmpeg.
 *
 * Nếu file không có audio track, hoặc có lỗi đọc/mux, trả về false và giữ
 * nguyên file gốc — giống hệt cơ chế fallback của bản server.
 */
public final class VideoAudioStripper {

    private VideoAudioStripper() {}

    public interface Logger { void log(String msg); }

    public static class Result {
        public boolean ok;
        public File outputFile; // = input nếu ok=false (fallback)
        public String reason;
    }

    public static Result stripAudio(File inputMp4, File outputDir, Logger log) {
        Result res = new Result();
        File outFile = new File(outputDir, "video_no_audio_" + System.currentTimeMillis() + ".mp4");

        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(inputMp4.getAbsolutePath());

            int videoTrackIndex = -1;
            MediaFormat videoFormat = null;
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i;
                    videoFormat = f;
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) {
                res.ok = false;
                res.reason = "Không tìm thấy video track trong file nguồn";
                res.outputFile = inputMp4;
                return res;
            }

            muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerVideoTrack = muxer.addTrack(videoFormat);
            muxer.start();

            extractor.selectTrack(videoTrackIndex);
            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();

            while (true) {
                buffer.clear();
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;
                info.offset = 0;
                info.size = sampleSize;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(muxerVideoTrack, buffer, info);
                extractor.advance();
            }

            muxer.stop();
            res.ok = true;
            res.outputFile = outFile;
            log.log("Đã tách audio khỏi video (chỉ copy track video, không re-encode): " + outFile.length() + " byte.");
            return res;
        } catch (Exception e) {
            log.log("⚠ Tách audio khỏi video thất bại, dùng video gốc (giữ nguyên audio nếu có): " + e.getMessage());
            if (outFile.exists()) outFile.delete();
            res.ok = false;
            res.reason = e.getMessage();
            res.outputFile = inputMp4;
            return res;
        } finally {
            try { if (muxer != null) muxer.release(); } catch (Exception ignored) {}
            if (extractor != null) extractor.release();
        }
    }

    /** Ghi byte[] video vào 1 file tạm để MediaExtractor đọc (nó cần path/FD, không nhận byte[] trực tiếp). */
    public static File bytesToTempFile(byte[] videoBytes, File dir, String name) throws Exception {
        File f = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(videoBytes);
        }
        return f;
    }
}
