package j5;

/**
 * Bit reader dùng để parse Ogg page header (27 byte đầu mỗi page).
 * Xác nhận quy ước LSB-first (giống v5.a) qua việc đối chiếu hằng số capture pattern
 * 1399285583 == little-endian bytes "OggS".
 */
public final class a {
    private final byte[] buf;
    private int pos = 0;

    public a(byte[] bArr) {
        this.buf = bArr;
    }

    public boolean a() {
        int i = pos;
        boolean bit = (buf[i / 8] & (1 << (i % 8))) != 0;
        pos = i + 1;
        return bit;
    }

    public int b(int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            if (a()) v |= (1 << i);
        }
        return v;
    }
}
