package com.echohall.kgvn.localbuild;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Port 1:1 nhánh chính của server/index.js POST /api/build, chạy hoàn toàn
 * trên máy — dùng khi nhạc là file TỰ TẢI LÊN + convert ngay trên máy (xem
 * com.echohall.kgvn.w2w.WemConverter), nên không có wemId trong thư viện để
 * gọi /api/build của server như bình thường.
 *
 * Không đụng gì tới server Node — chỉ đọc Supabase (anon key, xem
 * SupabaseConfigClient) + tải thẳng bnk_url/video_url (đều là URL public,
 * GitHub raw / Supabase Storage) rồi tự patch + zip.
 */
public final class LocalModBuilder {

    private LocalModBuilder() {}

    // Khớp CHÍNH XÁC với BASE_DIR/WEM_DIR/VIDEO_DIR trong server/index.js —
    // đây là đường dẫn trong app game, không được lệch dù chỉ 1 ký tự.
    private static final String BASE_DIR = "com.garena.game.kgvn/files/Extra/2022.V3/";
    private static final String WEM_DIR = BASE_DIR + "Sound_DLC/Android/";
    private static final String VIDEO_DIR = BASE_DIR + "ISPDiff/LobbyMovie/";

    public interface Logger { void log(String msg); }

    public static class BuildInput {
        public byte[] wemBytes;
        public int wemDurationMs;
        public byte[] videoBytes; // đã tách audio (hoặc gốc nếu tách lỗi) — xem VideoAudioStripper
    }

    public static class BuildOutput {
        public byte[] zipBytes;
        public String reportText; // tương đương X-Build-Report của server, để log cho người dùng xem
    }

    public static BuildOutput build(BuildInput input, Logger log) throws Exception {
        log.log("Đang lấy cấu hình bnk + danh sách sảnh từ Supabase...");
        SupabaseConfigClient.Config cfg = SupabaseConfigClient.fetchConfig();
        log.log("OK — replacementId=" + cfg.bnkSettings.replacementId + ", " + cfg.lobbyProfiles.size() + " sảnh đang bật.");

        log.log("Đang tải Music_Login.bnk gốc...");
        byte[] bnkBuffer = httpGetBytes(cfg.bnkSettings.bnkUrl);
        log.log("OK — " + bnkBuffer.length + " byte.");

        byte[] patchedBuffer = bnkBuffer;
        int totalStreamTypeConverted = 0;
        for (SupabaseConfigClient.LobbyProfile profile : cfg.lobbyProfiles) {
            BnkPatcher.PatchResult patchResult = BnkPatcher.patchIdAndDuration(
                    patchedBuffer, profile.sourceId, cfg.bnkSettings.replacementId, input.wemDurationMs);
            if (!patchResult.ok) {
                throw new RuntimeException("Patch bnk thất bại ở sảnh \"" + profile.name
                        + "\" (Source ID " + profile.sourceId + "): " + patchResult.reason);
            }
            patchedBuffer = patchResult.buffer;
            totalStreamTypeConverted += patchResult.streamTypeConvertedCount;
            log.log("Đã patch sảnh \"" + profile.name + "\": " + patchResult.idPatchCount + " chỗ ID, "
                    + patchResult.durationPatchCount + " chỗ duration.");
        }
        if (totalStreamTypeConverted > 0) {
            log.log("Đã tự chuyển " + totalStreamTypeConverted + " track từ embedded sang streamed.");
        }

        long replacementId = cfg.bnkSettings.replacementId;
        String zipWemName = replacementId + ".wem";

        ByteArrayOutputStream zipBuf = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBuf)) {
            putEntry(zos, WEM_DIR + zipWemName, input.wemBytes);
            putEntry(zos, WEM_DIR + "Music_Login.bnk", patchedBuffer);
            for (SupabaseConfigClient.LobbyProfile profile : cfg.lobbyProfiles) {
                putEntry(zos, VIDEO_DIR + profile.videoFilename, input.videoBytes);
            }
        }

        BuildOutput out = new BuildOutput();
        out.zipBytes = zipBuf.toByteArray();

        StringBuilder report = new StringBuilder();
        report.append("replacementId=").append(replacementId)
                .append(", durationMs=").append(input.wemDurationMs)
                .append(", sảnh: ");
        for (int i = 0; i < cfg.lobbyProfiles.size(); i++) {
            SupabaseConfigClient.LobbyProfile p = cfg.lobbyProfiles.get(i);
            if (i > 0) report.append(", ");
            report.append(p.name).append(" (sourceId=").append(p.sourceId).append(")");
        }
        out.reportText = report.toString();
        log.log("Đã đóng gói xong zip: " + out.zipBytes.length + " byte.");
        return out;
    }

    private static void putEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    public static byte[] httpGetBytes(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = readAll(is);
            if (code != 200) throw new IOException("HTTP " + code + " khi tải " + urlStr);
            if (bytes.length == 0) throw new IOException("File tải về rỗng (0 byte): " + urlStr);
            return bytes;
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }
}
