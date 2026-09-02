package i5;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public final class b {
    public final RandomAccessFile f11779a;
    public final long[] f11780b;
    public final HashMap f11781c = new HashMap();

    public b(RandomAccessFile randomAccessFile) throws IOException {
        this.f11779a = randomAccessFile;
        ArrayList arrayList = new ArrayList();
        int i7 = 0;
        int i8 = 0;
        while (true) {
            try {
                arrayList.add(Long.valueOf(this.f11779a.getFilePointer()));
                d dVarA = d.a(this.f11779a, i8 > 0);
                if (dVarA == null) {
                    break;
                }
                int i9 = dVarA.f11788c;
                c cVar = (c) this.f11781c.get(Integer.valueOf(i9));
                if (cVar == null) {
                    cVar = new c(this);
                    this.f11781c.put(Integer.valueOf(i9), cVar);
                }
                if (i8 == 0) {
                    c.a(dVarA);
                }
                cVar.f11783b.add(Integer.valueOf(i8));
                cVar.f11784c.add(Long.valueOf(dVarA.f11787b));
                if (i8 > 0) {
                    RandomAccessFile randomAccessFile2 = this.f11779a;
                    long filePointer = randomAccessFile2.getFilePointer();
                    byte[] bArr = dVarA.f11793h;
                    randomAccessFile2.seek(filePointer + ((long) (bArr != null ? dVarA.f11792g.length + 27 + bArr.length : dVarA.f11791f)));
                }
                i8++;
            } catch (i5.a unused) {
                break;
            } catch (IOException e5) {
                throw e5;
            }
        }
        this.f11779a.seek(0L);
        this.f11780b = new long[arrayList.size()];
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            this.f11780b[i7] = ((Long) it.next()).longValue();
            i7++;
        }
    }
}
