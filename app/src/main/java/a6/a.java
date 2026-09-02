package a6;

import com.google.android.gms.internal.ads.vk;
import e.j0;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class a extends j0 {
    public final int f144c;
    public final int f145d;
    public final int f146e;
    public final int f147f;
    public final int f148g;
    public int f149h;
    public int f150i;

    public a(vk vkVar, v5.a aVar) {
        super(vkVar, aVar);
        this.f149h = -1;
        this.f150i = -1;
        aVar.f(32);
        this.f144c = aVar.f(8);
        this.f145d = aVar.f(32);
        aVar.f(32);
        this.f146e = aVar.f(32);
        aVar.f(32);
        int iF = aVar.f(8);
        this.f147f = iF & 15;
        this.f148g = iF >> 4;
        aVar.f(8);
    }

    public final byte[] o(int i7, long j7) {
        d dVar = (d) ((vk) this.f10945b).f8889d;
        v5.a aVarD = v5.a.d();
        aVarD.l("RIFF".getBytes(StandardCharsets.US_ASCII));
        aVarD.j(32, (int) (86 + j7));
        aVarD.l("WAVE".getBytes(StandardCharsets.US_ASCII));
        aVarD.l("fmt ".getBytes(StandardCharsets.US_ASCII));
        aVarD.j(16, 66);
        aVarD.j(16, 0);
        aVarD.j(16, 65535);
        int i8 = this.f144c;
        aVarD.j(16, i8);
        aVarD.j(32, this.f145d);
        aVarD.j(32, this.f146e);
        aVarD.j(16, 0);
        aVarD.j(16, 0);
        aVarD.j(32, 48);
        if (i8 < 1 || i8 > 2) {
            throw new UnsupportedOperationException("Mono or Stereo only");
        }
        byte[] bArr = new byte[4];
        Arrays.fill(bArr, (byte) 0);
        v5.a aVar = new v5.a(bArr, 2);
        aVar.j(8, i8);
        aVar.j(4, 1);
        aVar.j(19, i8 != 1 ? 3 : 4);
        aVarD.l(aVar.c());
        aVarD.j(32, (int) ((vk) this.f10945b).f8886a);
        aVarD.j(32, i7);
        int i9 = (int) j7;
        aVarD.j(32, i9);
        aVarD.j(16, 0);
        aVarD.j(16, dVar.f161j);
        aVarD.j(32, 0);
        aVarD.j(32, i7);
        aVarD.j(16, dVar.f160i);
        aVarD.j(16, 0);
        aVarD.j(32, this.f149h);
        aVarD.j(32, this.f150i);
        aVarD.j(32, 0);
        aVarD.j(8, this.f147f);
        aVarD.j(8, this.f148g);
        aVarD.l("data".getBytes(StandardCharsets.US_ASCII));
        aVarD.j(32, i9);
        return aVarD.c();
    }
}
