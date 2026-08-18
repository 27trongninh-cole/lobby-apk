package com.echohall.kgvn;

import android.content.Context;
import android.net.Uri;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nhận file zip mod (đã tải sẵn từ web), giải nén, rồi cài từng file vào
 * đúng vị trí — KHÔNG cần biết trước danh sách file, tự suy ra từ chính
 * cấu trúc thư mục có trong zip.
 *
 * QUY ƯỚC (phải khớp với cách web đóng gói zip):
 *   gốc của zip  ==  Extra/2022.V3/  trong thư mục data của game.
 *   Ví dụ: zip có "Sound_DLC/Android/xxx.wem"
 *       -> cài vào ".../files/Extra/2022.V3/Sound_DLC/Android/xxx.wem"
 *
 * Nếu sau này web đổi mốc gốc (vd. version game đổi từ 2022.V3 sang khác),
 * CHỈ cần đổi hằng số GAME_DATA_SUBPATH bên dưới — không cần đổi gì khác
 * trong luồng cài đặt.
 */
public class ModInstaller {

    private static final String GAME_PACKAGE = "com.garena.game.kgvn";
    // Phần cố định giữa "files" và gốc zip. Đổi ở đây nếu game update path.
    private static final String GAME_DATA_SUBPATH = "Extra/2022.V3";

    public interface ProgressListener {
        /** current/total tính theo số file, để hiển thị progress bar. */
        void onProgress(int current, int total, String currentFileName);
        /**
         * 1 file cụ thể cài thất bại nhưng KHÔNG dừng cả tiến trình — vd. file
         * .mp4 bị Android chặn ghi vì lý do quyền media (cần root), trong khi
         * các file audio khác vẫn cài bình thường. reason nên đủ rõ để hiển
         * thị thẳng cho người dùng biết cần làm gì (không chỉ "lỗi không rõ").
         */
        void onFileSkipped(String relativePath, String reason);
        /** Thông báo trạng thái chung không gắn với 1 file cụ thể (vd. đang fix ISPDiff). */
        void onStatus(String message);
        void onDone(int installedCount);
        void onError(String message);
    }

    private final Context appContext;
    private final ModBackupManager backupManager;
    private final IspdiffFixer ispdiffFixer;

    public ModInstaller(Context context) {
        this.appContext = context.getApplicationContext();
        this.backupManager = new ModBackupManager(appContext);
        this.ispdiffFixer = new IspdiffFixer();
    }

    /**
     * @param zipUri Uri của file zip mod, lấy từ Storage Access Framework
     *               (người dùng chọn file đã tải từ web) hoặc từ Intent nhận file.
     */
    // File "hoàn toàn mới" (game vốn không có) mà web LUÔN tạo với đúng tên cố
    // định này — xác nhận từ cấu trúc zip mod thật. Dùng CHUNG cho cả bước
    // CÀI (bỏ qua backup, ghi đè thẳng — kể cả khi đã tồn tại từ lần cài
    // trước) và bước GỠ (xoá thẳng, không cần tìm backup) — 1 nguồn duy nhất
    // để 2 nơi không lệch nhau.
    private static final String[] KNOWN_APP_CREATED_RELATIVE_PATHS = {
            "Sound_DLC/Android/30082005.wem"
    };

    private static boolean isKnownAppCreatedFile(String relativePathInZip) {
        for (String known : KNOWN_APP_CREATED_RELATIVE_PATHS) {
            if (relativePathInZip.endsWith(known)) return true;
        }
        return false;
    }

    public void installFromZip(Uri zipUri, ProgressListener listener) {
        new Thread(() -> {
            try {
                File extractDir = extractZipToExternalCache(zipUri);
                List<File> files = listAllFiles(extractDir);

                if (files.isEmpty()) {
                    listener.onError("Zip không chứa file nào — kiểm tra lại file mod đã tải.");
                    return;
                }

                // Nếu zip có đụng đến ISPDiff (video), fix 1 lần trước khi cài —
                // idempotent, tự bỏ qua nếu đã fix từ lượt cài trước đó.
                boolean touchesIspdiff = files.stream()
                        .anyMatch(f -> extractDir.toPath().relativize(f.toPath()).toString().contains("/ISPDiff/"));
                if (touchesIspdiff) {
                    ispdiffFixer.ensureFixed(gameExtraRootPath(), listener::onStatus);
                }

                int total = files.size();
                int done = 0;
                for (File f : files) {
                    String relativePath = extractDir.toPath().relativize(f.toPath()).toString();
                    String targetPath = buildTargetPath(relativePath);
                    boolean noBackupNeeded = isKnownAppCreatedFile(relativePath);

                    listener.onProgress(done, total, relativePath);
                    try {
                        backupManager.installFile(f, targetPath, noBackupNeeded);
                        done++;
                    } catch (IOException fileError) {
                        // KHÔNG dừng cả lượt cài vì 1 file thất bại — ví dụ file
                        // media (.mp4/.jpg/...) bị Android chặn ghi khi chưa có
                        // root, trong khi các file khác (âm thanh) vẫn hợp lệ.
                        String reason = fileError.getMessage() != null ? fileError.getMessage() : fileError.toString();
                        if (looksLikePermissionDenied(reason) && isLikelyMediaFile(relativePath)) {
                            reason = "Bị Android chặn ghi vì đây là file media (ảnh/video) — cần quyền ROOT "
                                    + "(Shizuku ở chế độ Root) mới ghi được, quyền Shizuku thường (ADB) không đủ. Chi tiết: " + reason;
                        }
                        listener.onFileSkipped(relativePath, reason);
                    }
                }

                // Dọn thư mục giải nén tạm trong external cache — file mod thật
                // giờ đã nằm trong thư mục game, không cần giữ bản sao ở đây nữa.
                deleteRecursive(extractDir);

                listener.onDone(done);
            } catch (Exception e) {
                listener.onError(describeError(e));
            }
        }).start();
    }

    /** Gỡ toàn bộ mod đã cài — chạy nền, báo kết quả qua listener đơn giản. */
    public void uninstallAll(ProgressListener listener) {
        new Thread(() -> {
            try {
                File gamePackageDataDir = new File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "Android/data/" + GAME_PACKAGE);
                File extraRoot = new File(gamePackageDataDir, "files/" + GAME_DATA_SUBPATH);

                // Khôi phục ISPDiff (nếu đã từng fix) TRƯỚC — xoá sạch bản hiện
                // tại (dù đã mod bao nhiêu lần), đổi backup toàn vẹn trở lại.
                // Rename tức thời, không phụ thuộc dung lượng vài GB bên trong.
                // Làm bước này trước để sau khi quét *.nins ở dưới, thư mục
                // ISPDiff đã sạch, không còn gì để đụng vào nữa (tự nhiên đúng,
                // không cần loại trừ đường dẫn thủ công).
                listener.onStatus("Đang khôi phục ISPDiff (nếu có)...");
                ispdiffFixer.restoreWhole(extraRoot.getAbsolutePath());

                Set<String> before = backupManager.scanInstalledPathsFromFilesystem(extraRoot.getAbsolutePath());
                backupManager.restoreAllRobust(extraRoot.getAbsolutePath());

                // Dọn thêm các file "biết trước là do app tạo, tên cố định" —
                // xoá thẳng, không cần kiểm tra backup vì loại này chưa từng có
                // bản gốc để khôi phục.
                int extraRemoved = 0;
                for (String knownRelative : KNOWN_APP_CREATED_RELATIVE_PATHS) {
                    File f = new File(extraRoot, knownRelative);
                    String path = f.getAbsolutePath();
                    boolean exists = ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                            "[ -e '" + path.replace("'", "'\\''") + "' ] && echo yes || echo no"})
                            .stdout.trim().equals("yes");
                    if (exists) {
                        ShizukuShell.execOrThrow(new String[]{"rm", "-f", path});
                        extraRemoved++;
                    }
                    // Dọn luôn ".nins lạc" nếu máy đã dính bug ở bản cũ (trước khi
                    // installFile có tham số noBackupNeeded) — an toàn để xoá vì
                    // file này chưa từng có bản gốc thật đứng sau nó.
                    String strayBackup = path + ".nins";
                    boolean strayExists = ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                            "[ -e '" + strayBackup.replace("'", "'\\''") + "' ] && echo yes || echo no"})
                            .stdout.trim().equals("yes");
                    if (strayExists) {
                        ShizukuShell.execOrThrow(new String[]{"rm", "-f", strayBackup});
                    }
                }

                listener.onDone(before.size() + extraRemoved);
            } catch (Exception e) {
                listener.onError(describeError(e));
            }
        }).start();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private File extractZipToExternalCache(Uri zipUri) throws IOException {
        File externalCache = appContext.getExternalCacheDir();
        if (externalCache == null) {
            throw new IOException("Không truy cập được external cache — kiểm tra thẻ nhớ/quyền lưu trữ.");
        }

        File zipCopy = new File(externalCache, "incoming_mod.zip");
        try (InputStream in = appContext.getContentResolver().openInputStream(zipUri);
             FileOutputStream out = new FileOutputStream(zipCopy)) {
            if (in == null) throw new IOException("Không đọc được file zip đã chọn.");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }

        File extractDir = new File(externalCache, "mod_extract");
        deleteRecursive(extractDir); // dọn sạch lần cài trước nếu còn sót
        extractDir.mkdirs();

        try (ZipFile zipFile = new ZipFile(zipCopy)) {
            zipFile.extractAll(extractDir.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("Giải nén zip thất bại: " + e.getMessage(), e);
        } finally {
            zipCopy.delete();
        }

        return extractDir;
    }

    private static List<File> listAllFiles(File dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private String gameExtraRootPath() {
        File androidData = new File(
                android.os.Environment.getExternalStorageDirectory(),
                "Android/data/" + GAME_PACKAGE + "/files/" + GAME_DATA_SUBPATH);
        return androidData.getAbsolutePath();
    }

    private String buildTargetPath(String relativePathInZip) {
        // Xác nhận từ zip mod thật: gốc zip = "com.garena.game.kgvn/files/Extra/2022.V3/..."
        // tức đã bao gồm sẵn "<package>/files/..." — chỉ cần ghép thẳng vào
        // sau "Android/data/", không cần tự chèn thêm GAME_PACKAGE hay
        // GAME_DATA_SUBPATH nữa (hằng số cũ giữ lại chỉ để tham chiếu/log).
        File androidDataRoot = new File(
                android.os.Environment.getExternalStorageDirectory(), "Android/data");
        return new File(androidDataRoot, relativePathInZip).getAbsolutePath();
    }

    private static boolean looksLikePermissionDenied(String reason) {
        return reason != null && reason.toLowerCase().contains("permission denied");
    }

    private static boolean isLikelyMediaFile(String relativePath) {
        String lower = relativePath.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webm") || lower.endsWith(".3gp");
        // Cố ý KHÔNG liệt .wem/.bnk vào đây — đó là định dạng riêng của Wwise,
        // Android không nhận diện là media nên không bị chặn theo cơ chế này.
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    private static String describeError(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
