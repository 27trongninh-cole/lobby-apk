package v5;

import java.util.Arrays;

public final class a {
    public final byte[] f15027a;
    public final int f15028b;
    public final int f15029c;
    public int f15030d = 0;

    public a(byte[] bArr, int i7) {
        this.f15027a = bArr;
        this.f15028b = bArr.length;
        this.f15029c = i7;
    }

    public static a d() {
        byte[] bArr = new byte[32768];
        Arrays.fill(bArr, (byte) 0);
        return new a(bArr, 2);
    }

    public static a e(byte[] bArr) {
        return new a(bArr, 1);
    }

    public final void a() {
        this.f15030d = 0;
        Arrays.fill(this.f15027a, (byte) 0);
    }

    public final int b() {
        int i7 = this.f15030d;
        return i7 % 8 == 0 ? i7 : (8 - (i7 % 8)) + i7;
    }

    public final byte[] c() {
        int i7 = this.f15029c;
        byte[] bArr = this.f15027a;
        return i7 == 1 ? bArr : Arrays.copyOfRange(bArr, 0, b() / 8);
    }

    public final int f(int i7) {
        int i8 = 0;
        for (int i9 = 0; i9 < i7; i9++) {
            if (g()) {
                i8 |= 1 << i9;
            }
        }
        return i8;
    }

    public final boolean g() {
        int i7 = this.f15030d;
        int i8 = this.f15027a[i7 / 8] & (1 << (i7 % 8));
        this.f15030d = i7 + 1;
        return i8 != 0;
    }

    public final void h(int i7, int i8) {
        int i9;
        if (i8 == 0) {
            throw new RuntimeException("mode=0");
        }
        int i10 = i8 - 1;
        int i11 = this.f15028b;
        if (i10 != 0) {
            if (i10 == 1) {
                i9 = this.f15030d + i7;
            } else if (i10 == 2) {
                i9 = (i11 * 8) - i7;
            } else {
                i9 = 0;
            }
            this.f15030d = i9;
        } else {
            this.f15030d = i7;
        }
        if (b() / 8 >= i11 || this.f15030d <= -1) {
            throw new IndexOutOfBoundsException("Error seek; max: " + (i11 * 8) + "; offset: " + this.f15030d);
        }
    }

    public final int i(int i7, int i8, a aVar) {
        if (i8 != i7) {
            if (i8 != i7 && i7 > 24) {
                throw new UnsupportedOperationException("No not");
            }
            int iF = aVar.f(i7);
            j(i8, iF);
            return iF;
        }
        int i9 = 0;
        for (int i10 = 0; i10 < i7; i10++) {
            boolean zG = aVar.g();
            j(1, zG ? 1 : 0);
            if (zG) {
                i9 |= 1 << i10;
            }
        }
        return i9;
    }

    public final void j(int i7, int i8) {
        int i9;
        int i10;
        if (this.f15029c != 2) {
            throw new UnsupportedOperationException("BitSream Write Only");
        }
        if (i7 == 0) {
            return;
        }
        if (i7 > 32 || (i10 = (i9 = this.f15030d) + i7) > this.f15028b * 8) {
            throw new IndexOutOfBoundsException("Max read 32 bits");
        }
        int i11 = i9 / 8;
        int i12 = i9 % 8;
        int i13 = i11 + 0;
        byte[] bArr = this.f15027a;
        bArr[i13] = (byte) ((((1 << i12) - 1) & bArr[i13]) | (i8 << i12));
        int i14 = i7 + i12;
        if (i14 > 8) {
            bArr[i11 + 1] = (byte) (i8 >> (8 - i12));
            if (i14 > 16) {
                bArr[i11 + 2] = (byte) (i8 >> (16 - i12));
                if (i14 > 24) {
                    bArr[i11 + 3] = (byte) (i8 >> (24 - i12));
                    if (i14 > 32) {
                        bArr[i11 + 4] = (byte) (i8 >> (32 - i12));
                    }
                }
            }
        }
        this.f15030d = i10;
    }

    public final void k(a aVar) {
        int i7 = (aVar.f15028b * 8) - aVar.f15030d;
        for (int i8 = 0; i8 < i7 && this.f15030d / 8 < aVar.f15027a.length; i8++) {
            j(1, aVar.g() ? 1 : 0);
        }
    }

    public final void l(byte[] bArr) {
        for (byte b7 : bArr) {
            j(8, b7 & 255);
        }
    }
}
