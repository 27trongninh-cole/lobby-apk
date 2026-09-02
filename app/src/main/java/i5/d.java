package i5;

import java.io.EOFException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public final class d {
    public final boolean f11786a;
    public final long f11787b;
    public final int f11788c;
    public final int[] f11789d;
    public final int[] f11790e;
    public final int f11791f;
    public final byte[] f11792g;
    public final byte[] f11793h;

    public d(boolean z6, long j7, int i7, int[] iArr, int[] iArr2, int i8, byte[] bArr, byte[] bArr2) {
        this.f11786a = z6;
        this.f11787b = j7;
        this.f11788c = i7;
        this.f11789d = iArr;
        this.f11790e = iArr2;
        this.f11791f = i8;
        this.f11792g = bArr;
        this.f11793h = bArr2;
    }

    public static d a(RandomAccessFile randomAccessFile, boolean z6) {
        int i7;
        try {
            byte[] bArr = new byte[27];
            byte[] bArr2 = null;
            int i8 = 0;
            if (randomAccessFile.getFilePointer() == randomAccessFile.length()) {
                return null;
            }
            randomAccessFile.readFully(bArr);

            j5.a aVar = new j5.a(bArr);
            int iB = aVar.b(32);
            if (iB != 1399285583) {
                System.out.println("Ogg packet header sai, khong phai OggS");
            }
            aVar.b(8);
            boolean z7 = (((byte) aVar.b(8)) & 4) != 0;
            long j7 = 0;
            for (int i11 = 0; i11 < 64; i11++) {
                if (aVar.a()) {
                    j7 |= 1L << i11;
                }
            }
            int iB2 = aVar.b(32);
            aVar.b(32);
            aVar.b(32);
            int iB3 = aVar.b(8);
            int[] iArr = new int[iB3];
            int[] iArr2 = new int[iB3];
            byte[] bArr3 = new byte[iB3];
            int i12 = 0;
            int i13 = 27;
            for (int i14 = 0; i14 < iB3; i14++) {
                i7 = randomAccessFile.readByte() & 255;
                bArr3[i14] = (byte) i7;
                iArr2[i14] = i7;
                iArr[i14] = i12;
                i12 += i7;
            }
            if (!z6) {
                bArr2 = new byte[i12];
                randomAccessFile.readFully(bArr2);
            }
            return new d(z7, j7, iB2, iArr, iArr2, i12, bArr3, bArr2);
        } catch (EOFException unused) {
            throw new i5.a();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
