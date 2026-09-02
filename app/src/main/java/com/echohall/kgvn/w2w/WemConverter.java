package com.echohall.kgvn.w2w;

import a6.d;
import androidx.lifecycle.z;
import com.google.android.gms.internal.ads.vk;
import io.github.lnii11.bsed.w2w.W2WIO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class WemConverter {

    public interface Logger { void log(String msg); }

    public static File convert(File wavFile, File outDir, float quality, byte[] codebookBinBytes, Logger log) throws Exception {
        log.log("PACKET_DEBUG_MARKER_v9: WemConverter.convert() build v9 dang chay.");
        WavInfo wav = WavInfo.read(wavFile.getAbsolutePath());
        log.log("WAV: " + wav.channels + " kenh, " + wav.sampleRate + " Hz, " + wav.numSamples + " sample.");

        File oggFile = new File(outDir, "out.ogg");
        int wencResult = W2WIO.wenc(wavFile.getAbsolutePath(), oggFile.getAbsolutePath(), wav.channels, wav.sampleRate, quality);
        log.log("wenc() ket qua: " + wencResult);
        if (wencResult != 1) throw new RuntimeException("Native wenc() that bai");
        log.log("Encode xong: " + oggFile.length() + " byte.");

        i5.b bReader = new i5.b(new RandomAccessFile(oggFile, "r"));
        Iterator it = bReader.f11781c.values().iterator();
        if (!it.hasNext()) throw new RuntimeException("Khong tim thay stream nao trong file ogg");
        i5.c cVar = (i5.c) it.next();

        z dictionary = new z(codebookBinBytes);
        vk vkVar = new vk(cVar, dictionary);
        log.log("Da parse ID header + match codebook.");

        a6.a idHeader = (a6.a) vkVar.f8888c;
        d setupData = (d) vkVar.f8889d;

        File finalWemFile = new File(outDir, wavFile.getName().replaceAll("\\.[^.]+$", "") + ".wem");
        File audioTempFile = new File(outDir, "audio_temp.bin");
        FileOutputStream fileOutputStream = new FileOutputStream(audioTempFile);

        v5.a aVarD = v5.a.d();
        aVarD.j(8, setupData.f154c.size() - 1);
        int i19 = 0;
        for (Object entryObj : setupData.f154c.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObj;
            int key = (Integer) entry.getKey();
            aVarD.j(10, ((a6.c) ((ArrayList) entry.getValue()).get(0)).f152a);
            if (i19 != key) throw new IllegalArgumentException("Codebook thu tu sai: " + i19 + " != " + key);
            i19++;
        }

        v5.a src = (v5.a) setupData.f10944a;
        int timeCount = src.f(6) + 1;
        for (int t = 0; t < timeCount; t++) {
            if (src.f(16) != 0) throw new UnsupportedOperationException("Time domain transformation != 0");
        }
        log.log("Time domain OK (count=" + timeCount + ").");

        int floorCount = aVarD.i(6, 6, src) + 1;
        for (int i = 0; i < floorCount; i++) {
            int floorType = src.f(16);
            if (floorType != 1) throw new UnsupportedOperationException("Floor type " + floorType + " khong ho tro");
            int partitions = aVarD.i(5, 5, src);
            int[] partClass = new int[partitions];
            int maxClass = -1;
            for (int j = 0; j < partitions; j++) {
                int c = aVarD.i(4, 4, src);
                partClass[j] = c;
                if (c > maxClass) maxClass = c;
            }
            int[] classDims = new int[maxClass + 1];
            for (int c = 0; c <= maxClass; c++) {
                classDims[c] = aVarD.i(3, 3, src) + 1;
                int subclasses = aVarD.i(2, 2, src);
                if (subclasses != 0) aVarD.i(8, 8, src);
                for (int k = 0; k < (1 << subclasses); k++) aVarD.i(8, 8, src);
            }
            aVarD.i(2, 2, src);
            int rangebits = aVarD.i(4, 4, src);
            for (int j = 0; j < partitions; j++) {
                int cn = partClass[j];
                for (int k = 0; k < classDims[cn]; k++) aVarD.i(rangebits, rangebits, src);
            }
        }

        log.log("Floor OK (count=" + floorCount + ").");

        int residueCount = aVarD.i(6, 6, src) + 1;
        for (int i = 0; i < residueCount; i++) {
            int rtype = src.f(16);
            aVarD.j(2, rtype);
                                
            aVarD.i(24, 24, src);
            aVarD.i(24, 24, src);
            aVarD.i(24, 24, src);
            int classifications = aVarD.i(6, 6, src) + 1;
            aVarD.i(8, 8, src);
            int[] cascade = new int[classifications];
            for (int j = 0; j < classifications; j++) {
                int low = aVarD.i(3, 3, src);
                boolean flag = src.g();
                aVarD.j(1, flag ? 1 : 0);
                int high = 0;
                if (flag) high = aVarD.i(5, 5, src);
                cascade[j] = high * 8 + low;
            }
            for (int j = 0; j < classifications; j++)
                for (int k = 0; k < 8; k++)
                    if ((cascade[j] & (1 << k)) != 0) aVarD.i(8, 8, src);
        }

        log.log("Residue OK (count=" + residueCount + ").");

        int mapCount = aVarD.i(6, 6, src) + 1;
        for (int i = 0; i < mapCount; i++) {
            int mappingType = src.f(16);
            if (mappingType != 0) throw new UnsupportedOperationException("Mapping type != 0: " + mappingType);
            
            boolean submapsFlag = src.g();
            aVarD.j(1, submapsFlag ? 1 : 0);
            int submaps = 1;
            if (submapsFlag) submaps = aVarD.i(4, 4, src) + 1;
            boolean squareFlag = src.g();
            aVarD.j(1, squareFlag ? 1 : 0);
            if (squareFlag) {
                int steps = aVarD.i(8, 8, src) + 1;
                int ib = ilog(idHeader.f144c - 1);
                for (int j = 0; j < steps; j++) { aVarD.i(ib, ib, src); aVarD.i(ib, ib, src); }
            }
            aVarD.i(2, 2, src);
            if (submaps > 1) for (int j = 0; j < idHeader.f144c; j++) aVarD.i(4, 4, src);
            for (int j = 0; j < submaps; j++) { aVarD.i(8, 8, src); aVarD.i(8, 8, src); aVarD.i(8, 8, src); }
        }

        log.log("Mapping OK (count=" + mapCount + ").");

        int modeCount = aVarD.i(6, 6, src) + 1;
        setupData.f157f = new boolean[modeCount];
        for (int i = 0; i < modeCount; i++) {
            boolean blockflag = src.g();
            aVarD.j(1, blockflag ? 1 : 0);
            int windowtype = src.f(16);
            int transformtype = src.f(16);
            aVarD.i(8, 8, src);
            if (windowtype != 0) throw new UnsupportedOperationException("Window type != 0");
            if (transformtype != 0) throw new UnsupportedOperationException("Transform type != 0");
            setupData.f157f[i] = blockflag;
        }
        int modeBits = 0;
        { int mm = modeCount - 1; while (mm > 0) { mm >>= 1; modeBits++; } }
        setupData.f158g = modeBits;
        aVarD.j(1, 1);

        byte[] setupBlob = aVarD.c();
        log.log("Setup blob: " + setupBlob.length + " byte.");

        c6.a packetReader = vkVar.packetReader;
        v5.a packetOut = v5.a.d();
        v5.a tmpD = v5.a.d();
        log.log("PACKET_DEBUG_MARKER_v9: bat dau doc audio packet (dung chung packetReader).");
        int packetCountDebug = 0;
        int skippedHeaderDebug = 0;
        StringBuilder firstSizesDebug = new StringBuilder();
        while (!packetReader.f1917e) {
            byte[] assembled = null;
            while (true) {
                if (packetReader.f1916d >= packetReader.f1918f) {
                    if (packetReader.f1915c != null) { packetReader.a(); packetReader.f1916d = 0; }
                    else { packetReader.f1917e = true; }
                }
                if (packetReader.f1917e) break;
                int[] sizes = packetReader.f1919g;
                int idx = packetReader.f1916d;
                int segSize = sizes[idx];
                int segOff = packetReader.f1920h[idx];
                byte[] chunk = Arrays.copyOfRange(packetReader.f1921i, segOff, segOff + segSize);
                assembled = (assembled == null) ? chunk : concat(assembled, chunk);
                packetReader.f1916d++;
                if (segSize < 255) break;
            }
            if (assembled != null && assembled.length != 0) {
                
                v5.a rd = v5.a.e(assembled);

                boolean packetTypeBit = rd.g();
                if (packetTypeBit) {
                    
                    log.log("Bo qua 1 header packet du thua (khong phai audio) giua stream.");
                    skippedHeaderDebug++;
                    continue;
                }

                int modeNumber = rd.f(setupData.f158g);
                if (modeNumber < 0 || modeNumber >= setupData.f157f.length)
                    throw new RuntimeException("Mode number khong hop le: " + modeNumber);

                boolean blockflag = setupData.f157f[modeNumber];
                if (blockflag) {
                    rd.g();
                    rd.g();
                }

                tmpD.a();
                tmpD.j(setupData.f158g, modeNumber);
                tmpD.k(rd);

                byte[] packetCompact = tmpD.c();
                if (packetCompact.length > setupData.f160i) setupData.f160i = packetCompact.length;
                packetOut.j(16, packetCompact.length);
                packetOut.l(packetCompact);
                setupData.f159h += packetOut.b();
                fileOutputStream.write(packetOut.c());
                packetOut.a();
                packetCountDebug++;
                if (packetCountDebug <= 20 || packetCountDebug % 50 == 0) {
                    firstSizesDebug.append(packetCompact.length).append(",");
                }
            }
        }
        log.log("PACKET_DEBUG_MARKER_v9: TONG SO AUDIO PACKET = " + packetCountDebug
                + " | so header packet bo qua = " + skippedHeaderDebug
                + " | mot so kich thuoc dau/moc: " + firstSizesDebug.toString());
        fileOutputStream.close();
        {
            long crcTemp = 0;
            try {
                java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
                byte[] cbuf = new byte[8192];
                int cn;
                try (java.io.FileInputStream cfis = new java.io.FileInputStream(audioTempFile)) {
                    while ((cn = cfis.read(cbuf)) != -1) crc32.update(cbuf, 0, cn);
                }
                crcTemp = crc32.getValue();
            } catch (Exception ignored) {}
            log.log("PACKET_DEBUG_MARKER_v9: audioTempFile CRC32=" + Long.toHexString(crcTemp)
                    + " size=" + audioTempFile.length());
        }
        log.log("Audio packet stream: " + audioTempFile.length() + " byte, max packet: " + setupData.f160i);

        byte[] setupLenPrefix = new byte[]{
            (byte) (setupBlob.length & 0xFF),
            (byte) ((setupBlob.length >> 8) & 0xFF)
        };
        int setupPacketTotalLen = setupLenPrefix.length + setupBlob.length;

        idHeader.f149h = 16080;
        idHeader.f150i = 16560;
        
        byte[] header = idHeader.o(setupPacketTotalLen,
                (long) (audioTempFile.length() + setupPacketTotalLen));

        FileOutputStream out = new FileOutputStream(finalWemFile);
        out.write(header);
        out.write(setupLenPrefix);
        out.write(setupBlob);
        java.io.FileInputStream fis = new java.io.FileInputStream(audioTempFile);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
        fis.close();
        out.close();
        audioTempFile.delete();

        long crc = 0;
        try {
            java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
            byte[] cbuf = new byte[8192];
            int cn;
            try (java.io.FileInputStream cfis = new java.io.FileInputStream(finalWemFile)) {
                while ((cn = cfis.read(cbuf)) != -1) crc32.update(cbuf, 0, cn);
            }
            crc = crc32.getValue();
        } catch (Exception ignored) {}
        log.log("PACKET_DEBUG_MARKER_v9: finalWemFile CRC32=" + Long.toHexString(crc)
                + " size=" + finalWemFile.length()
                + " lastModified=" + finalWemFile.lastModified());
        
        log.log("XONG! File .wem: " + finalWemFile.length() + " byte -> " + finalWemFile.getAbsolutePath());
        return finalWemFile;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static int ilog(int v) {
        int r = 0;
        while (v > 0) { v >>>= 1; r++; }
        return r;
    }
}
