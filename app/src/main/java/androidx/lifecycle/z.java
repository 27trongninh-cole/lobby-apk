package androidx.lifecycle;

import java.util.Arrays;
import java.util.HashMap;

public final class z {
    public final HashMap f1304a;

    public z(byte[] bArr) {
        this.f1304a = new HashMap();
        v5.a aVarE = v5.a.e(bArr);
        for (int i7 = 0; i7 <= 597; i7++) {
            aVarE.h(32, 3);
            aVarE.h(((i7 * 4) + aVarE.f(32)) * 8, 1);
            int iF = aVarE.f(32);
            int iF2 = aVarE.f(32);
            aVarE.h(0, 1);
            this.f1304a.put(Integer.valueOf(i7), Arrays.copyOfRange(aVarE.f15027a, iF, iF2));
        }
    }
}
