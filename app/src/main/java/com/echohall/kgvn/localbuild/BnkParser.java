package com.echohall.kgvn.localbuild;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port 1:1 từ server/lib/bnkParser.js (repo web build-mod) — chỉ giữ lại phần
 * cần cho việc PATCH (bnkPatcher dùng), bỏ các hàm chỉ phục vụ phân tích/hiển
 * thị trên web (findDurationOccurrences, scanForSourceStructs,
 * findTrackOwnDuration...).
 *
 * Toàn bộ offset trả về là offset TUYỆT ĐỐI vào buffer gốc — giống bản gốc,
 * để BnkPatcher ghi đè trực tiếp không cần tính lại.
 */
public final class BnkParser {

    public static final Map<Integer, String> HIRC_TYPES = new HashMap<>();
    static {
        HIRC_TYPES.put(1, "Settings"); HIRC_TYPES.put(2, "Sound"); HIRC_TYPES.put(3, "EventAction");
        HIRC_TYPES.put(4, "Event"); HIRC_TYPES.put(5, "RandomSequenceContainer"); HIRC_TYPES.put(6, "SwitchContainer");
        HIRC_TYPES.put(7, "ActorMixer"); HIRC_TYPES.put(8, "Bus"); HIRC_TYPES.put(9, "LayerContainer");
        HIRC_TYPES.put(10, "MusicSegment"); HIRC_TYPES.put(11, "MusicTrack"); HIRC_TYPES.put(12, "MusicSwitchContainer");
        HIRC_TYPES.put(13, "MusicRanSeqContainer"); HIRC_TYPES.put(14, "Attenuation"); HIRC_TYPES.put(15, "DialogueEvent");
        HIRC_TYPES.put(16, "FeedbackBus"); HIRC_TYPES.put(17, "FeedbackNode"); HIRC_TYPES.put(18, "Effect");
        HIRC_TYPES.put(19, "Environment"); HIRC_TYPES.put(20, "AudioDevice"); HIRC_TYPES.put(22, "AuxiliaryBus");
        HIRC_TYPES.put(23, "LFO"); HIRC_TYPES.put(24, "Envelope"); HIRC_TYPES.put(25, "AudioDeviceEffect");
        HIRC_TYPES.put(26, "Curve");
    }
    public static final Set<Integer> CONTAINER_TYPES =
            new HashSet<>(Arrays.asList(5, 6, 7, 8, 9, 10, 12, 13));

    private BnkParser() {}

    // ───────────────────────── đọc/ghi little-endian thuần byte[] ─────────────────────────

    public static long u32(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    public static int u8(byte[] b, int off) { return b[off] & 0xFF; }

    public static String fourCC(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }

    public static double readDoubleLE(byte[] b, int off) {
        long bits = 0;
        for (int i = 0; i < 8; i++) bits |= ((long) (b[off + i] & 0xFF)) << (8 * i);
        return Double.longBitsToDouble(bits);
    }

    public static void writeDoubleLE(byte[] b, int off, double value) {
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) b[off + i] = (byte) ((bits >> (8 * i)) & 0xFF);
    }

    public static void writeUInt32LE(byte[] b, int off, long value) {
        b[off] = (byte) (value & 0xFF);
        b[off + 1] = (byte) ((value >> 8) & 0xFF);
        b[off + 2] = (byte) ((value >> 16) & 0xFF);
        b[off + 3] = (byte) ((value >> 24) & 0xFF);
    }

    public static void writeUInt8(byte[] b, int off, int value) { b[off] = (byte) (value & 0xFF); }

    // ───────────────────────── cấu trúc dữ liệu ─────────────────────────

    public static class Chunk {
        public String id;
        public int dataStart;
        public long size;
        public boolean truncated;
    }

    public static class DidxEntry {
        public long mediaId;
        public long size;
    }

    public static class Hirc {
        public long id;
        public int type;
        public String typeName;
        public int payloadStart; // offset tuyệt đối vào buf
        public int payloadLen;
        public Set<Long> refIds = new HashSet<>();
    }

    public static class ParsedBnk {
        public List<Chunk> chunks;
        public List<DidxEntry> didx = new ArrayList<>();
        public List<Hirc> hirc = new ArrayList<>();
    }

    public static class DurationCandidate {
        public double value;
        public int offset;
    }

    // ───────────────────────── parse ─────────────────────────

    public static List<Chunk> parseChunks(byte[] buf) {
        List<Chunk> chunks = new ArrayList<>();
        int total = buf.length;
        int off = 0;
        while (off + 8 <= total) {
            Chunk c = new Chunk();
            c.id = fourCC(buf, off);
            c.size = u32(buf, off + 4);
            c.dataStart = off + 8;
            if (c.dataStart + c.size > total) { c.truncated = true; chunks.add(c); break; }
            c.truncated = false;
            chunks.add(c);
            off = (int) (c.dataStart + c.size);
        }
        return chunks;
    }

    private static List<DidxEntry> parseDIDX(byte[] buf, Chunk c) {
        List<DidxEntry> entries = new ArrayList<>();
        int n = (int) (c.size / 12);
        for (int i = 0; i < n; i++) {
            int o = c.dataStart + i * 12;
            DidxEntry e = new DidxEntry();
            e.mediaId = u32(buf, o);
            e.size = u32(buf, o + 8);
            entries.add(e);
        }
        return entries;
    }

    private static List<Hirc> parseHIRC(byte[] buf, Chunk c) {
        List<Hirc> objs = new ArrayList<>();
        int o = c.dataStart;
        int end = (int) (c.dataStart + c.size);
        long count = u32(buf, o); o += 4;
        for (long i = 0; i < count; i++) {
            if (o + 5 > end) break;
            int type = u8(buf, o);
            long len = u32(buf, o + 1);
            int objStart = o + 5;
            if (objStart + len > end) break;
            long id = u32(buf, objStart);
            int payloadStart = objStart + 4;
            int payloadLen = (int) (len - 4);
            Hirc h = new Hirc();
            h.id = id;
            h.type = type;
            h.typeName = HIRC_TYPES.getOrDefault(type, "Unknown(" + type + ")");
            h.payloadStart = payloadStart;
            h.payloadLen = Math.max(0, payloadLen);
            objs.add(h);
            o = (int) (objStart + len);
        }
        return objs;
    }

    private static Set<Long> parseEventRefs(byte[] buf, int payloadStart, int payloadLen, Set<Long> ids) {
        Set<Long> out = new HashSet<>();
        if (payloadLen < 1) return out;
        int cnt = u8(buf, payloadStart);
        if (1 + cnt * 4 <= payloadLen) {
            for (int k = 0; k < cnt; k++) {
                long v = u32(buf, payloadStart + 1 + 4 * k);
                if (ids.contains(v)) out.add(v);
            }
        }
        return out;
    }

    private static Set<Long> parseContainerRefs(byte[] buf, int payloadStart, int payloadLen, Set<Long> ids) {
        Set<Long> out = new HashSet<>();
        for (int p = 0; p + 8 <= payloadLen; p++) {
            long cnt = u32(buf, payloadStart + p);
            if (cnt < 1 || cnt > 64) continue;
            if (p + 4 + cnt * 4 > payloadLen) continue;
            List<Long> vals = new ArrayList<>();
            boolean allValid = true;
            Set<Long> dup = new HashSet<>();
            for (int k = 0; k < cnt; k++) {
                long v = u32(buf, payloadStart + p + 4 + 4 * k);
                if (!ids.contains(v) || dup.contains(v)) { allValid = false; break; }
                dup.add(v);
                vals.add(v);
            }
            if (allValid) out.addAll(vals);
        }
        return out;
    }

    private static Set<Long> parseActionRefs(byte[] buf, int payloadStart, int payloadLen, Set<Long> ids) {
        Set<Long> out = new HashSet<>();
        for (int p = 0; p + 4 <= payloadLen; p++) {
            long v = u32(buf, payloadStart + p);
            if (ids.contains(v)) out.add(v);
        }
        return out;
    }

    /** Mọi offset TUYỆT ĐỐI trong payload nơi 1 uint32 LE == targetId. */
    public static List<Integer> findAllIdOccurrences(byte[] buf, int payloadStart, int payloadLen, long targetId) {
        List<Integer> offsets = new ArrayList<>();
        for (int p = 0; p + 4 <= payloadLen; p++) {
            if (u32(buf, payloadStart + p) == targetId) offsets.add(payloadStart + p);
        }
        return offsets;
    }

    /** Mọi double hợp lệ trong khoảng 100ms..2h, trừ hằng số 1000.0 và các cặp âm/dương giả. */
    public static List<DurationCandidate> findAllDurationCandidates(byte[] buf, int payloadStart, int payloadLen) {
        List<DurationCandidate> raw = new ArrayList<>();
        for (int p = 0; p + 8 <= payloadLen; p++) {
            double v = readDoubleLE(buf, payloadStart + p);
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            if (v < 100 || v > 7200000) continue;
            if (Math.abs(v - 1000.0) < 0.01) continue;
            DurationCandidate dc = new DurationCandidate();
            dc.value = Math.round(v * 1000.0) / 1000.0;
            dc.offset = payloadStart + p;
            raw.add(dc);
        }

        List<Double> allValues = new ArrayList<>();
        for (int p = 0; p + 8 <= payloadLen; p++) {
            double v = readDoubleLE(buf, payloadStart + p);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) allValues.add(v);
        }

        List<DurationCandidate> result = new ArrayList<>();
        for (DurationCandidate c : raw) {
            boolean hasNegTwin = false;
            for (double v : allValues) {
                if (Math.abs(v + c.value) < 0.01) { hasNegTwin = true; break; }
            }
            if (!hasNegTwin) result.add(c);
        }
        return result;
    }

    public static ParsedBnk parseBnk(byte[] buf) {
        List<Chunk> chunks = parseChunks(buf);
        ParsedBnk result = new ParsedBnk();
        result.chunks = chunks;

        for (Chunk c : chunks) {
            if (c.truncated) continue;
            if (c.id.equals("DIDX")) result.didx = parseDIDX(buf, c);
            else if (c.id.equals("HIRC")) result.hirc = parseHIRC(buf, c);
        }

        Set<Long> hircIds = new HashSet<>();
        for (Hirc h : result.hirc) hircIds.add(h.id);

        for (Hirc h : result.hirc) {
            Set<Long> refs;
            if (h.type == 4) refs = parseEventRefs(buf, h.payloadStart, h.payloadLen, hircIds);
            else if (CONTAINER_TYPES.contains(h.type)) refs = parseContainerRefs(buf, h.payloadStart, h.payloadLen, hircIds);
            else if (h.type == 3) refs = parseActionRefs(buf, h.payloadStart, h.payloadLen, hircIds);
            else refs = new HashSet<>();
            h.refIds = refs;
        }

        return result;
    }
}
