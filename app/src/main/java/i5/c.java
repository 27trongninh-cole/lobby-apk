package i5;

import java.io.RandomAccessFile;
import java.util.ArrayList;

public final class c {
    public final b f11782a;
    public final ArrayList f11783b = new ArrayList();
    public final ArrayList f11784c = new ArrayList();
    public int f11785d = 0;

    public c(b bVar) {
        this.f11782a = bVar;
    }

    public static void a(d dVar) {
        byte[] bArr = dVar.f11793h;
        if (bArr.length >= 7 && bArr[1] == 118 && bArr[2] == 111 && bArr[3] == 114 && bArr[4] == 98 && bArr[5] == 105 && bArr[6] == 115) {
            return;
        }
    }

    public final synchronized d b() {
        RandomAccessFile randomAccessFile;
        b bVar = this.f11782a;
        ArrayList arrayList = this.f11783b;
        int i7 = this.f11785d;
        this.f11785d = i7 + 1;
        long j7 = bVar.f11780b[((Integer) arrayList.get(i7)).intValue()];
        randomAccessFile = bVar.f11779a;
        try {
            randomAccessFile.seek(j7);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return d.a(randomAccessFile, false);
    }
}
