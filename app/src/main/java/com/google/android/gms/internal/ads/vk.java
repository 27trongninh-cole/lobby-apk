package com.google.android.gms.internal.ads;

import java.util.ArrayList;
import java.util.Arrays;

public final class vk {
    public final long f8886a;
    public final Object f8887b;
    public Object f8888c;
    public Object f8889d;
    public final Object f8890e;

    // QUAN TRONG: day la instance packetReader DUY NHAT tren cVar nay. c6.a dung co che
    // "prefetch 1 trang" (f1915c) noi mot con tro dung chung ben trong i5.c (f11785d).
    // Neu tao THEM mot c6.a khac tren cung cVar (nhu code cu tung lam trong WemConverter),
    // instance moi se tu y keo them trang tu con tro dung chung do trong luc constructor
    // cua no chay - lam mat/nhay trang so voi cho packetReader nay dang dung do, khien
    // ranh gioi packet bi lech ngay tu audio packet thu 2 tro di (gom nhieu packet that
    // thanh 1 "packet" khong lo). Moi noi can doc packet (id/comment/setup lan audio) PHAI
    // dung chung field packetReader nay, KHONG duoc new c6.a(cVar) them lan nao nua.
    public final c6.a packetReader;

    public vk(i5.c cVar, androidx.lifecycle.z zVar) {
        this.f8887b = cVar;
        this.f8890e = zVar;
        ArrayList arrayList = cVar.f11784c;
        this.f8886a = ((Long) arrayList.get(arrayList.size() - 1)).longValue();

        // Doc 3 header packet (ID, comment, setup) theo dung PACKET (khong phai theo trang) -
        // an toan voi moi cach chia trang cua encoder. packetReader duoc GIU LAI (field cua
        // class) de WemConverter doc tiep audio packet ngay sau, KHONG tao instance moi.
        this.packetReader = new c6.a(cVar);
        byte[] idPacket = readOnePacket(packetReader);
        byte[] commentPacket = readOnePacket(packetReader);
        byte[] setupPacket = readOnePacket(packetReader);

        if (idPacket == null) throw new UnsupportedOperationException("The file has no identification header.");
        if (setupPacket == null) throw new UnsupportedOperationException("The file has no setup header.");

        v5.a idReader = v5.a.e(idPacket);
        idReader.f(8);
        idReader.f(24); idReader.f(24);
        this.f8888c = new a6.a(this, idReader);

        v5.a setupReader = v5.a.e(setupPacket);
        setupReader.f(8);
        setupReader.f(24); setupReader.f(24);
        try {
            this.f8889d = new a6.d(this, setupReader);
        } catch (RuntimeException e) {
            throw new RuntimeException(
                "idPacket.len=" + idPacket.length + " hex=" + hex(idPacket, 16) +
                " | commentPacket.len=" + (commentPacket == null ? -1 : commentPacket.length) +
                " | setupPacket.len=" + setupPacket.length +
                " || goc: " + e.getMessage(), e);
        }
    }

    private static String hex(byte[] b, int n) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) sb.append(String.format("%02X ", b[i]));
        return sb.toString();
    }

    private static byte[] readOnePacket(c6.a pr) {
        byte[] assembled = null;
        while (true) {
            if (pr.f1916d >= pr.f1918f) {
                if (pr.f1915c != null) { pr.a(); pr.f1916d = 0; }
                else { pr.f1917e = true; }
            }
            if (pr.f1917e) break;
            int[] sizes = pr.f1919g;
            int idx = pr.f1916d;
            int segSize = sizes[idx];
            int segOff = pr.f1920h[idx];
            byte[] chunk = Arrays.copyOfRange(pr.f1921i, segOff, segOff + segSize);
            assembled = (assembled == null) ? chunk : concat(assembled, chunk);
            pr.f1916d++;
            if (segSize < 255) break;
        }
        return assembled;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
