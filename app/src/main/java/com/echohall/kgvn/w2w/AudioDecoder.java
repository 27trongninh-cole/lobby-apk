package com.echohall.kgvn.w2w;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class AudioDecoder {

    public interface Logger { void log(String msg); }

    public static File decodeToWav(Context context, Uri srcUri, File outWavFile, Logger log) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, srcUri, null);

        int trackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                trackIndex = i;
                format = f;
                break;
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release();
            throw new RuntimeException("Khong tim thay audio track trong file nguon");
        }
        extractor.selectTrack(trackIndex);

        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        String mime = format.getString(MediaFormat.KEY_MIME);
        log.log("Nguon: " + channels + " kenh, " + sampleRate + " Hz (mime=" + mime + ")");

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        File pcmTemp = new File(outWavFile.getParentFile(), "decode_temp.pcm");
        FileOutputStream pcmOut = new FileOutputStream(pcmTemp);

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        long totalBytes = 0;

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inIndex = codec.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                        int sampleSize = inBuf != null ? extractor.readSampleData(inBuf, 0) : -1;
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            long presentationTime = extractor.getSampleTime();
                            codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTime, 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        ByteBuffer outBuf = codec.getOutputBuffer(outIndex);
                        byte[] chunk = new byte[info.size];
                        outBuf.get(chunk);
                        outBuf.clear();
                        pcmOut.write(chunk);
                        totalBytes += chunk.length;
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFormat = codec.getOutputFormat();
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    log.log("Dinh dang output codec thay doi: " + channels + " kenh, " + sampleRate + " Hz");
                }
            }
        } finally {
            pcmOut.close();
            codec.stop();
            codec.release();
            extractor.release();
        }

        log.log("Da giai ma xong: " + totalBytes + " byte PCM 16-bit.");

        writeWavFile(pcmTemp, outWavFile, channels, sampleRate, 16);
        pcmTemp.delete();

        log.log("Da tao WAV trung gian: " + outWavFile.length() + " byte, " + channels + " kenh, " + sampleRate + " Hz.");
        return outWavFile;
    }

    private static void writeWavFile(File pcmFile, File wavFile, int channels, int sampleRate, int bitsPerSample) throws Exception {
        long pcmLen = pcmFile.length();
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        try (RandomAccessFile out = new RandomAccessFile(wavFile, "rw")) {
            out.setLength(0);
            writeString(out, "RIFF");
            writeIntLE(out, (int) (36 + pcmLen));
            writeString(out, "WAVE");
            writeString(out, "fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, (short) 1);
            writeShortLE(out, (short) channels);
            writeIntLE(out, sampleRate);
            writeIntLE(out, (int) byteRate);
            writeShortLE(out, (short) blockAlign);
            writeShortLE(out, (short) bitsPerSample);
            writeString(out, "data");
            writeIntLE(out, (int) pcmLen);

            try (FileInputStream fis = new FileInputStream(pcmFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
    }

    private static void writeString(RandomAccessFile out, String s) throws Exception {
        out.write(s.getBytes("US-ASCII"));
    }

    private static void writeIntLE(RandomAccessFile out, int v) throws Exception {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShortLE(RandomAccessFile out, short v) throws Exception {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
