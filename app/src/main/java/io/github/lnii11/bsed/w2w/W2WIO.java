package io.github.lnii11.bsed.w2w;

public abstract class W2WIO {

    // Nạp libmwem.so (jniLibs/arm64-v8a/libmwem.so) MỘT LẦN duy nhất khi
    // class này lần đầu được dùng — làm ở đây thay vì trong Activity để
    // đảm bảo LUÔN được nạp trước khi wenc() được gọi, dù gọi từ đâu, không
    // phụ thuộc code gọi phải nhớ System.loadLibrary() trước.
    static {
        System.loadLibrary("mwem");
    }

    public static native int wenc(String str, String str2, int i7, int i8, float f7);
}
