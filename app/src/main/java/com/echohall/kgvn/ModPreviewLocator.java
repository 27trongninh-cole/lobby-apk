package com.echohall.kgvn;

import android.content.Context;
import android.net.Uri;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Giải nén zip mod vào 1 thư mục TẠM RIÊNG (khác "mod_extract" mà
 * ModInstaller dùng lúc cài thật) chỉ để xem trước — tìm file .wem đầu
 * tiên và file video đầu tiên trong zip, không đụng gì đến game.
 *
 * Vì sao tách thư mục riêng: nếu dùng chung "mod_extract" với ModInstaller,
 * bước cài mod thật sự (installFromZip) sẽ dọn/ghi đè thư mục đó, có thể
 * đụng vào lúc preview đang phát — tách riêng cho an toàn, dù tốn thêm 1
 * lượt giải nén nhỏ (zip mod thường chỉ vài file, chi phí không đáng kể).
 */
public class ModPreviewLocator {

    public static class PreviewFiles {
        public final File wemFile;   // null nếu zip không có file .wem
        public final File videoFile; // null nếu zip không có file video
        PreviewFiles(File wemFile, File videoFile) {
            this.wemFile = wemFile;
            this.videoFile = videoFile;
        }
    }

    private final Context appContext;

    public ModPreviewLocator(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Chạy trên thread nền do caller tự lo (giải nén không nhanh bằng việc chỉ đọc tên). */
    public PreviewFiles locate(Uri zipUri) throws IOException {
        File externalCache = appContext.getExternalCacheDir();
        if (externalCache == null) {
            throw new IOException("Không truy cập được external cache.");
        }

        File zipCopy = new File(externalCache, "preview_incoming.zip");
        try (InputStream in = appContext.getContentResolver().openInputStream(zipUri);
             FileOutputStream out = new FileOutputStream(zipCopy)) {
            if (in == null) throw new IOException("Không đọc được file zip đã chọn.");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }

        File extractDir = new File(externalCache, "preview_extract");
        deleteRecursive(extractDir);
        extractDir.mkdirs();

        try (ZipFile zipFile = new ZipFile(zipCopy)) {
            zipFile.extractAll(extractDir.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("Giải nén zip thất bại: " + e.getMessage(), e);
        } finally {
            zipCopy.delete();
        }

        List<File> allFiles;
        try (Stream<java.nio.file.Path> walk = Files.walk(extractDir.toPath())) {
            allFiles = walk.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).collect(Collectors.toList());
        }

        File wem = allFiles.stream()
                .filter(f -> f.getName().toLowerCase().endsWith(".wem"))
                .findFirst().orElse(null);

        File video = allFiles.stream()
                .filter(f -> isVideoFile(f.getName()))
                .findFirst().orElse(null);

        return new PreviewFiles(wem, video);
    }

    private static boolean isVideoFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                || lower.endsWith(".webm") || lower.endsWith(".3gp");
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
