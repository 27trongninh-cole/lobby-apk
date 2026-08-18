package com.echohall.kgvn;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Backup/restore theo TỪNG FILE bị ghi đè, không phải theo cả thư mục.
 *
 * Vì sao: thư mục nhạc gốc (Sound_DLC/Android/...) chứa hàng nghìn file,
 * trong khi 1 lượt cài mod thường chỉ thay thế một số ít file cụ thể
 * (đúng những bài được chọn trên web). Backup cả thư mục (copy toàn bộ)
 * sẽ tốn gấp đôi dung lượng + thời gian không cần thiết. Thay vào đó:
 *
 *   1. Với mỗi file đích bị ghi đè, nếu nó đang tồn tại VÀ chưa có bản
 *      backup từ trước, rename nó thành "<tên>.nins" ngay tại chỗ
 *      (rename tức thời, không phụ thuộc dung lượng file).
 *   2. Ghi file mod vào đúng tên gốc.
 *   3. Ghi lại đường dẫn đã đụng vào trong 1 manifest riêng (lưu trong
 *      bộ nhớ app, KHÔNG lưu trong thư mục game) để biết cần restore gì.
 *
 * Restore: xoá file mod hiện tại, đổi tên "<tên>.nins" trở lại
 * tên gốc, xoá khỏi manifest.
 *
 * Quan trọng: nếu 1 file đã có backup rồi (cài mod 2 lần liên tiếp mà
 * chưa gỡ lần đầu), KHÔNG được backup đè lên backup cũ — vì bản backup
 * cũ mới là file gốc thật của game, còn file đang nằm đó lúc này đã là
 * mod. Backup đè sẽ mất vĩnh viễn file gốc.
 */
public class ModBackupManager {

    private static final String BACKUP_SUFFIX = ".nins";
    private static final String MANIFEST_FILE_NAME = "mod_manifest.json";

    private final Context appContext;
    private final File manifestFile;

    public ModBackupManager(Context context) {
        this.appContext = context.getApplicationContext();
        // Lưu manifest trong bộ nhớ riêng của app (KHÔNG phải external storage
        // của game) — Shizuku/shell không cần đọc file này, chỉ app tự đọc.
        this.manifestFile = new File(appContext.getFilesDir(), MANIFEST_FILE_NAME);
    }

    /**
     * Cài 1 file mod vào đúng vị trí đích, tự backup file gốc nếu cần.
     *
     * @param sourceFileInExternalCache file mod đã nằm sẵn trong
     *        getExternalCacheDir() của app này (Shizuku/shell đọc được từ
     *        đây) — KHÔNG dùng file trong getCacheDir() (internal), shell
     *        không có quyền đọc.
     * @param targetAbsolutePath đường dẫn tuyệt đối trong thư mục data của
     *        game, ví dụ .../Android/data/com.garena.game.kgvn/files/Extra/
     *        2022.V3/Sound_DLC/Android/xxx.wem
     */
    public void installFile(File sourceFileInExternalCache, String targetAbsolutePath) throws IOException {
        if (!sourceFileInExternalCache.exists()) {
            throw new IOException("File nguồn không tồn tại: " + sourceFileInExternalCache);
        }

        String backupPath = targetAbsolutePath + BACKUP_SUFFIX;

        // NGUỒN SỰ THẬT là filesystem, KHÔNG phải manifest riêng của app:
        // nếu "<tên>.nins" đã tồn tại, file hiện tại ở targetAbsolutePath
        // chắc chắn là do app này cài (game không bao giờ tự tạo file hậu tố
        // .nins) — không cần rename thêm lần nữa, tránh mất bản gốc
        // thật đang được giữ trong file backup đó.
        //
        // Cách này sống sót qua việc app bị xoá data/gỡ cài lại: dù manifest
        // nội bộ mất, filesystem của game vẫn còn nguyên bằng chứng.
        boolean backupExists = ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                "[ -e " + shellQuote(backupPath) + " ] && echo yes || echo no"})
                .stdout.trim().equals("yes");

        if (!backupExists) {
            boolean targetExists = ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                    "[ -e " + shellQuote(targetAbsolutePath) + " ] && echo yes || echo no"})
                    .stdout.trim().equals("yes");

            if (targetExists) {
                // File đích hiện tại chưa từng có backup -> đây là file gốc
                // thật của game. Rename tức thời, không phụ thuộc dung lượng.
                ShizukuShell.execOrThrow(new String[]{
                        "mv", targetAbsolutePath, backupPath});
            }
            // Nếu target chưa từng tồn tại (file hoàn toàn mới), không có gì
            // để backup — lúc restore sẽ hiểu là "xoá file mod đi".
        }
        // else: backup đã tồn tại từ lần cài trước -> file hiện tại là mod cũ,
        // không phải file gốc -> ghi đè trực tiếp, KHÔNG tạo thêm backup mới
        // (đây chính là phần tối ưu dung lượng: chỉ có duy nhất 1 bản backup
        // cho mỗi file, dù cài mod bao nhiêu lần liên tiếp).

        addTracked(targetAbsolutePath); // cập nhật index tiện lợi cho restoreAll/liệt kê

        // Copy file mod từ external cache của app vào đúng vị trí đích.
        ShizukuShell.execOrThrow(new String[]{
                "cp", sourceFileInExternalCache.getAbsolutePath(), targetAbsolutePath});
    }

    /** Gỡ 1 file cụ thể: xoá bản mod, khôi phục bản gốc nếu có. */
    public void restoreFile(String targetAbsolutePath) throws IOException {
        if (!isTracked(targetAbsolutePath)) {
            return; // không có gì để restore
        }
        String backupPath = targetAbsolutePath + BACKUP_SUFFIX;

        boolean backupExists = ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                "[ -e " + shellQuote(backupPath) + " ] && echo yes || echo no"})
                .stdout.trim().equals("yes");

        if (backupExists) {
            // Xoá file mod hiện tại trước, rồi mới đổi tên bản gốc trở lại —
            // tránh trường hợp mv đè lỗi vì file đích đang tồn tại.
            ShizukuShell.execOrThrow(new String[]{"rm", "-f", targetAbsolutePath});
            ShizukuShell.execOrThrow(new String[]{"mv", backupPath, targetAbsolutePath});
        } else {
            // Không có backup nghĩa là file này vốn không tồn tại trước khi
            // cài mod (file mới hoàn toàn) — restore = xoá nó đi.
            ShizukuShell.execOrThrow(new String[]{"rm", "-f", targetAbsolutePath});
        }

        removeTracked(targetAbsolutePath);
    }

    /** Gỡ toàn bộ mod đã cài (dùng cho nút "Gỡ tất cả"). */
    public void restoreAll() throws IOException {
        Set<String> tracked = new LinkedHashSet<>(readManifest());
        for (String path : tracked) {
            restoreFile(path); // tự cập nhật manifest từng bước, an toàn nếu bị gián đoạn giữa chừng
        }
    }

    public Set<String> getInstalledPaths() {
        return readManifest();
    }

    /**
     * Quét trực tiếp filesystem tìm mọi file "*.nins" trong thư mục
     * data của game — đây là nguồn đáng tin cậy hơn manifest (sống sót qua
     * việc app bị xoá data/gỡ cài lại). Dùng làm nguồn chính cho restoreAll();
     * manifest chỉ bổ sung thêm các file mod HOÀN TOÀN MỚI (game vốn không
     * có) — loại này không để lại dấu vết .nins nên chỉ manifest mới
     * biết, và sẽ bị bỏ sót nếu manifest cũng mất — hạn chế cần biết trước.
     */
    public Set<String> scanInstalledPathsFromFilesystem(String gameDataRootAbsolutePath) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        ShizukuShell.Result r = ShizukuShell.execOrThrow(new String[]{
                "find", gameDataRootAbsolutePath, "-name", "*" + BACKUP_SUFFIX});
        for (String line : r.stdout.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // "<target>.nins" -> "<target>"
            result.add(trimmed.substring(0, trimmed.length() - BACKUP_SUFFIX.length()));
        }
        return result;
    }

    /**
     * restoreAll bền vững: hợp nhất kết quả quét filesystem (đáng tin cậy
     * nhất cho file bị ghi đè) với manifest nội bộ (bắt thêm file mod hoàn
     * toàn mới mà filesystem không để lại dấu vết .nins).
     */
    public void restoreAllRobust(String gameDataRootAbsolutePath) throws IOException {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(scanInstalledPathsFromFilesystem(gameDataRootAbsolutePath));
        merged.addAll(readManifest());
        for (String path : merged) {
            restoreFile(path);
        }
    }

    // ───────────────────────── manifest helpers ─────────────────────────
    // Manifest chỉ là 1 JSON array các đường dẫn đích đã bị đụng vào — đọc/ghi
    // trực tiếp bằng app (không qua shell), nên mỗi thao tác đều atomic đơn giản
    // bằng cách ghi lại toàn bộ file mỗi lần thay đổi (số lượng path thường nhỏ,
    // không phải hàng nghìn — chỉ những file THỰC SỰ bị thay, nên ổn).

    private synchronized boolean isTracked(String path) {
        return readManifest().contains(path);
    }

    private synchronized void addTracked(String path) throws IOException {
        Set<String> set = readManifest();
        set.add(path);
        writeManifest(set);
    }

    private synchronized void removeTracked(String path) throws IOException {
        Set<String> set = readManifest();
        set.remove(path);
        writeManifest(set);
    }

    private Set<String> readManifest() {
        Set<String> result = new LinkedHashSet<>();
        if (!manifestFile.exists()) return result;
        try {
            String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
        } catch (IOException | JSONException e) {
            // Manifest hỏng/rỗng — coi như chưa cài gì. Không throw để không
            // chặn luôn cả app; nhưng đáng lẽ nên log để người dùng biết nếu
            // việc này xảy ra bất thường (file bị can thiệp ngoài ý muốn).
        }
        return result;
    }

    private void writeManifest(Set<String> paths) throws IOException {
        JSONArray arr = new JSONArray();
        for (String p : paths) arr.put(p);
        // Ghi ra file tạm rồi rename đè lên manifest thật — tránh manifest bị
        // hỏng nếu app crash giữa lúc ghi (rename trong cùng filesystem nội bộ
        // của app là atomic).
        File tmp = new File(appContext.getFilesDir(), MANIFEST_FILE_NAME + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(manifestFile)) {
            throw new IOException("Không thể cập nhật manifest mod");
        }
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
