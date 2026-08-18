package com.echohall.kgvn;

import java.io.IOException;

/**
 * "Fix ISPDiff" — thao tác CHỈ LÀM 1 LẦN để biến thư mục ISPDiff (chứa
 * LobbyMovie, chứa file .mp4 bị Android khoá quyền ghi group cho file
 * media) thành 1 bản sao do CHÍNH SHELL tạo ra — từ đó về sau, mọi file
 * bên trong ghi/rename bình thường được, không cần fix lại mỗi lần cài mod.
 *
 * VÌ SAO CÁCH NÀY HOẠT ĐỘNG (đã xác nhận bằng thực nghiệm — rename cả thư
 * mục ISPDiff thành công dù rename từng file .mp4 bên trong thất bại):
 * rename/tạo mới 1 entry trong thư mục chỉ cần quyền GHI trên chính THƯ
 * MỤC CHỨA entry đó (ở đây là "Extra/2022.V3"), không phụ thuộc quyền của
 * các file/thư mục con bên trong entry bị rename. "Extra/2022.V3" đã xác
 * nhận có quyền ghi bình thường cho group ext_data_rw (Music_Login.bnk
 * cài thành công trong Sound_DLC, cùng cấp với ISPDiff).
 *
 * Các bước (chỉ chạy nếu CHƯA fix — kiểm tra qua sự tồn tại của
 * "ISPDiff.nins"):
 *   1. cp -r ISPDiff -> ISPDiff_ninstaller_tmp   (bản sao MỚI, shell sở
 *      hữu -> mọi file/thư mục bên trong ghi được bình thường)
 *   2. mv ISPDiff -> ISPDiff.nins                 (giữ nguyên bản gốc làm
 *      backup TOÀN VẸN — dùng khi cần khôi phục 100% nguyên trạng)
 *   3. mv ISPDiff_ninstaller_tmp -> ISPDiff        (đưa bản ghi-được vào
 *      đúng vị trí)
 *
 * Sau bước này, ModBackupManager.installFile() dùng bình thường cho từng
 * file bên trong ISPDiff/LobbyMovie như mọi thư mục khác — không cần biết
 * gì về class này nữa.
 */
public class IspdiffFixer {

    private static final String FOLDER_NAME = "ISPDiff";
    // Đồng bộ hậu tố với ModBackupManager — cùng ý nghĩa "bản gốc được giữ lại".
    private static final String BACKUP_SUFFIX = ".nins";
    private static final String TEMP_SUFFIX = "_ninstaller_tmp";

    public interface ProgressCallback {
        void onStatus(String message);
    }

    /** @param gameExtraRoot đường dẫn tuyệt đối tới .../files/Extra/2022.V3 */
    public boolean isFixed(String gameExtraRoot) throws IOException {
        String backupPath = gameExtraRoot + "/" + FOLDER_NAME + BACKUP_SUFFIX;
        return dirExists(backupPath);
    }

    /** Idempotent — gọi mỗi lần cài mod cũng an toàn, tự bỏ qua nếu đã fix rồi. */
    public void ensureFixed(String gameExtraRoot, ProgressCallback callback) throws IOException {
        if (isFixed(gameExtraRoot)) {
            notify(callback, "ISPDiff đã được fix từ trước — bỏ qua bước sao chép.");
            return;
        }

        String original = gameExtraRoot + "/" + FOLDER_NAME;
        String backup = original + BACKUP_SUFFIX;
        String tempNew = original + TEMP_SUFFIX;

        if (!dirExists(original)) {
            throw new IOException("Không tìm thấy thư mục " + FOLDER_NAME
                    + " trong dữ liệu game — kiểm tra lại đường dẫn/version game.");
        }

        // Dọn tàn dư nếu 1 lần fix trước đó bị gián đoạn giữa chừng (app bị
        // kill lúc đang copy vài GB) — tránh cp -r báo lỗi vì thư mục đã tồn tại.
        ShizukuShell.execOrThrow(new String[]{"rm", "-rf", tempNew});

        notify(callback, "Đang sao chép " + FOLDER_NAME + " (có thể vài GB, chờ một lát — chỉ cần làm 1 lần)...");
        ShizukuShell.execOrThrow(new String[]{"cp", "-r", original, tempNew});

        notify(callback, "Đổi tên bản gốc thành backup...");
        ShizukuShell.execOrThrow(new String[]{"mv", original, backup});

        notify(callback, "Đưa bản sao ghi-được vào vị trí chính thức...");
        ShizukuShell.execOrThrow(new String[]{"mv", tempNew, original});

        notify(callback, "Fix ISPDiff hoàn tất — các lần cài mod video sau sẽ không cần fix lại.");
    }

    /**
     * Khôi phục nguyên trạng 100%: xoá bản hiện tại (có thể đã bị mod nhiều
     * lần), đổi backup toàn vẹn trở lại đúng tên gốc. Dùng cho "gỡ tất cả".
     * Nếu chưa từng fix (không có backup), không làm gì cả.
     */
    public void restoreWhole(String gameExtraRoot) throws IOException {
        String original = gameExtraRoot + "/" + FOLDER_NAME;
        String backup = original + BACKUP_SUFFIX;

        if (!dirExists(backup)) return; // chưa từng fix -> không có gì để khôi phục

        ShizukuShell.execOrThrow(new String[]{"rm", "-rf", original});
        ShizukuShell.execOrThrow(new String[]{"mv", backup, original});
    }

    private static boolean dirExists(String path) throws IOException {
        return ShizukuShell.execOrThrow(new String[]{"sh", "-c",
                "[ -d " + shellQuote(path) + " ] && echo yes || echo no"})
                .stdout.trim().equals("yes");
    }

    private static void notify(ProgressCallback callback, String message) {
        if (callback != null) callback.onStatus(message);
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
