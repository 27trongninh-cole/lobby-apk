package com.echohall.kgvn.w2w;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Gộp 3 mảnh của w2wtest lại thành 1 bước duy nhất cho "TỰ TẢI NHẠC LÊN":
 * Uri (.wav/.mp3/.ogg) -> (decode nếu cần) -> WAV -> .wem + duration đo được.
 *
 * Không phụ thuộc server — chạy hoàn toàn trên máy bằng native encoder
 * (libmwem.so) đã port từ repo w2wtest-android.
 */
public final class WavToWemPipeline {

    private WavToWemPipeline() {}

    // Mức quality mặc định — tương đương "level 8/10" trong app w2wtest gốc
    // (qualityFromSlider(level=8) = -0.1 + 7/9*1.1 ≈ 0.756). Chất lượng cao,
    // dung lượng vẫn hợp lý cho 1 bản nhạc sảnh.
    private static final float DEFAULT_QUALITY = 0.756f;

    public static class Result {
        public File wemFile;
        public int durationMs;
    }

    public static Result convert(Context ctx, Uri srcUri, String displayName, File workDir,
                                  byte[] codebookBytes, WemConverter.Logger log) throws Exception {
        if (!workDir.exists()) workDir.mkdirs();

        String lower = displayName == null ? "" : displayName.toLowerCase(java.util.Locale.US);
        File wavFile;
        if (lower.endsWith(".wav")) {
            log.log("File đã là .wav, copy trực tiếp không cần decode.");
            wavFile = new File(workDir, "input.wav");
            copyUriToFile(ctx, srcUri, wavFile);
        } else {
            log.log("File .mp3/.ogg — decode qua MediaCodec sang WAV trung gian trước.");
            wavFile = new File(workDir, "decoded.wav");
            AudioDecoder.decodeToWav(ctx, srcUri, wavFile, log::log);
        }

        WavInfo info = WavInfo.read(wavFile.getAbsolutePath());
        int durationMs = (int) Math.round(info.numSamples * 1000.0 / info.sampleRate);
        log.log("Duration đo được: " + durationMs + " ms.");

        File wemFile = WemConverter.convert(wavFile, workDir, DEFAULT_QUALITY, codebookBytes, log);

        Result r = new Result();
        r.wemFile = wemFile;
        r.durationMs = durationMs;
        return r;
    }

    private static void copyUriToFile(Context ctx, Uri uri, File dest) throws Exception {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(dest)) {
            if (is == null) throw new RuntimeException("Không mở được file đã chọn");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        }
    }
}
