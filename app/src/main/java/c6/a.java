package c6;

/**
 * Đọc lần lượt từng "segment" (mảnh) trong các trang Ogg, ghép lại thành packet hoàn chỉnh.
 * Dùng cơ chế đọc trước 1 trang (double-buffering): f1914b = trang hiện tại đang xử lý,
 * f1915c = trang kế tiếp đã đọc trước sẵn (null nếu trang hiện tại là trang cuối stream).
 *
 * Ghi chú: đoạn gốc decompile qua JADX bị lỗi hiển thị dạng "rơi xuyên qua" (fall-through)
 * rất khó đọc/vô nghĩa (các phép gán trong nhánh if bị ghi đè ngay sau đó) - viết lại đây theo
 * đúng ý nghĩa thật suy ra được từ cách class này được gọi dùng (kiểm tra f1915c null để quyết
 * định gọi a() tiếp hay dừng ở packet cuối).
 */
public final class a {
    public final i5.c f1913a;
    public i5.d f1914b;
    public i5.d f1915c;
    public int f1916d = 0;
    public boolean f1917e = false;
    public int f1918f;
    public int[] f1919g;
    public int[] f1920h;
    public byte[] f1921i;

    public a(i5.c cVar) {
        this.f1913a = cVar;
        a();
    }

    public final void a() {
        i5.d cur;
        if (this.f1914b != null && this.f1915c != null) {
            cur = this.f1915c;
        } else {
            cur = this.f1913a.b();
        }
        this.f1914b = cur;
        this.f1919g = cur.f11790e;
        this.f1920h = cur.f11789d;
        this.f1921i = cur.f11793h;
        this.f1918f = cur.f11790e.length;
        // f11786a = trang nay co phai trang cuoi cung cua stream (EOS) khong.
        // Neu KHONG phai trang cuoi, doc truoc 1 trang nua de sau dung.
        this.f1915c = cur.f11786a ? null : this.f1913a.b();
    }
}
