package com.echohall.kgvn.w2w;

import java.io.RandomAccessFile;

public class WavInfo {
    public int channels;
    public int sampleRate;
    public int bitsPerSample;
    public int numSamples;

    public static WavInfo read(String path) throws Exception {
        RandomAccessFile f = new RandomAccessFile(path, "r");
        try {
            byte[] riff = new byte[4]; f.readFully(riff);
            f.skipBytes(4);
            byte[] wave = new byte[4]; f.readFully(wave);
            if (!new String(riff, "ASCII").equals("RIFF") || !new String(wave, "ASCII").equals("WAVE"))
                throw new RuntimeException("Khong phai WAV hop le");
            WavInfo info = new WavInfo();
            int dataSize = 0;
            while (f.getFilePointer() < f.length() - 8) {
                byte[] id = new byte[4]; f.readFully(id);
                int size = readU32LE(f);
                long chunkStart = f.getFilePointer();
                String idStr = new String(id, "ASCII");
                if (idStr.equals("fmt ")) {
                    f.skipBytes(2);
                    info.channels = readU16LE(f);
                    info.sampleRate = readU32LE(f);
                    f.skipBytes(6);
                    info.bitsPerSample = readU16LE(f);
                } else if (idStr.equals("data")) {
                    dataSize = size;
                }
                f.seek(chunkStart + size + (size % 2));
            }
            info.numSamples = dataSize / (info.bitsPerSample / 8) / info.channels;
            return info;
        } finally {
            f.close();
        }
    }

    private static int readU32LE(RandomAccessFile f) throws Exception {
        byte[] b = new byte[4]; f.readFully(b);
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24);
    }
    private static int readU16LE(RandomAccessFile f) throws Exception {
        byte[] b = new byte[2]; f.readFully(b);
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
    }
}
