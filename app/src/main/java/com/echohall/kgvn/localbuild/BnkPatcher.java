package com.echohall.kgvn.localbuild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port 1:1 từ server/lib/bnkPatcher.js — patch sourceId + duration (và tự
 * chuyển embedded→streamed nếu game nhúng sẵn wem vào bank) trực tiếp trên
 * buffer .bnk, không cần server.
 */
public final class BnkPatcher {

    private BnkPatcher() {}

    public static class PatchResult {
        public boolean ok;
        public String reason;
        public byte[] buffer;
        public int idPatchCount;
        public int durationPatchCount;
        public int streamTypeConvertedCount;
    }

    private static class Located {
        boolean ok;
        String reason;
        List<Integer> durationOffsets = new ArrayList<>();
        List<Integer> idFieldOffsets = new ArrayList<>();
        List<int[]> streamTypeFields = new ArrayList<>(); // {streamTypeOffset, mediaSizeOffset}
    }

    private static Located locateFields(byte[] buf, long targetSourceId) {
        BnkParser.ParsedBnk parsed = BnkParser.parseBnk(buf);
        Located loc = new Located();

        List<BnkParser.Hirc> trackMatches = new ArrayList<>();
        Map<BnkParser.Hirc, List<Integer>> idOffsetsByTrack = new HashMap<>();
        for (BnkParser.Hirc h : parsed.hirc) {
            if (h.type != 2 && h.type != 11) continue; // Sound / MusicTrack
            List<Integer> idOffsets = BnkParser.findAllIdOccurrences(buf, h.payloadStart, h.payloadLen, targetSourceId);
            if (!idOffsets.isEmpty()) {
                trackMatches.add(h);
                idOffsetsByTrack.put(h, idOffsets);
            }
        }

        if (trackMatches.isEmpty()) {
            loc.ok = false;
            loc.reason = "Không tìm thấy HIRC object nào tham chiếu sourceId " + targetSourceId;
            return loc;
        }

        Set<Long> trackIds = new LinkedHashSet<>();
        for (BnkParser.Hirc h : trackMatches) trackIds.add(h.id);

        List<BnkParser.Hirc> segments = new ArrayList<>();
        for (BnkParser.Hirc h : parsed.hirc) {
            if (h.type != 10) continue; // MusicSegment
            boolean refsTrack = false;
            for (Long r : h.refIds) if (trackIds.contains(r)) { refsTrack = true; break; }
            if (refsTrack) segments.add(h);
        }

        List<Integer> durationOffsets = new ArrayList<>();
        for (BnkParser.Hirc seg : segments) {
            for (BnkParser.DurationCandidate c : BnkParser.findAllDurationCandidates(buf, seg.payloadStart, seg.payloadLen)) {
                durationOffsets.add(c.offset);
            }
        }
        for (BnkParser.Hirc h : trackMatches) {
            for (BnkParser.DurationCandidate c : BnkParser.findAllDurationCandidates(buf, h.payloadStart, h.payloadLen)) {
                durationOffsets.add(c.offset);
            }
        }

        List<Integer> idFieldOffsets = new ArrayList<>();
        for (BnkParser.Hirc h : trackMatches) idFieldOffsets.addAll(idOffsetsByTrack.get(h));

        // Tự phát hiện embedded->streamed (giống bnkPatcher.js): nếu sourceId
        // gốc đang NHÚNG SẴN trong bank (streamType=0, mediaSize khớp DIDX),
        // tự chuyển streamType về 2 (streamed) để cách patch "trỏ ra file .wem
        // rời" tiếp tục hoạt động dù game vừa đổi cách đóng gói.
        Map<Long, Long> didxSizeById = new HashMap<>();
        for (BnkParser.DidxEntry d : parsed.didx) didxSizeById.put(d.mediaId, d.size);

        List<int[]> streamTypeFields = new ArrayList<>();
        Long embeddedSize = didxSizeById.get(targetSourceId);
        if (embeddedSize != null) {
            for (int off : idFieldOffsets) {
                if (off - 1 < 0 || off + 8 > buf.length) continue;
                int streamTypeOffset = off - 1;
                int mediaSizeOffset = off + 4;
                int streamType = BnkParser.u8(buf, streamTypeOffset);
                long mediaSize = BnkParser.u32(buf, mediaSizeOffset);
                if (streamType == 0 && mediaSize == embeddedSize) {
                    streamTypeFields.add(new int[]{ streamTypeOffset, mediaSizeOffset });
                }
            }
        }

        loc.ok = true;
        loc.durationOffsets = durationOffsets;
        loc.idFieldOffsets = idFieldOffsets;
        loc.streamTypeFields = streamTypeFields;
        return loc;
    }

    /**
     * Trả về BUFFER MỚI (copy) đã patch:
     *  - mọi occurrence của targetSourceId -> replacementSourceId
     *  - mọi field duration liên quan (segment + track) -> newDurationMs
     * Kích thước file giữ nguyên — chỉ ghi đè field cố định độ dài tại chỗ.
     */
    public static PatchResult patchIdAndDuration(byte[] buf, long targetSourceId, long replacementSourceId, double newDurationMs) {
        Located loc = locateFields(buf, targetSourceId);
        PatchResult res = new PatchResult();
        if (!loc.ok) { res.ok = false; res.reason = loc.reason; return res; }

        byte[] patched = Arrays.copyOf(buf, buf.length);

        int streamTypeConvertedCount = 0;
        for (int[] stf : loc.streamTypeFields) {
            BnkParser.writeUInt8(patched, stf[0], 2); // 2 = streamed (phát từ file rời)
            streamTypeConvertedCount++;
        }

        int idPatchCount = 0;
        for (int off : loc.idFieldOffsets) {
            BnkParser.writeUInt32LE(patched, off, replacementSourceId);
            idPatchCount++;
        }

        int durationPatchCount = 0;
        for (int off : loc.durationOffsets) {
            BnkParser.writeDoubleLE(patched, off, newDurationMs);
            durationPatchCount++;
        }

        res.ok = true;
        res.buffer = patched;
        res.idPatchCount = idPatchCount;
        res.durationPatchCount = durationPatchCount;
        res.streamTypeConvertedCount = streamTypeConvertedCount;
        return res;
    }
}
