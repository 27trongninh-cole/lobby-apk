package com.echohall.kgvn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Chạy lệnh shell với quyền của tiến trình Shizuku (thường là "shell" uid,
 * đủ quyền đọc/ghi Android/data của app khác mà không cần root máy).
 *
 * Shizuku.newProcess không nằm trong API public chính thức của thư viện
 * shizuku-api (nó dùng nội bộ cho Shizuku Manager), nên phải gọi qua
 * reflection — đây là kỹ thuật cộng đồng dùng phổ biến, không phải hack
 * riêng của app này. Nếu sau này Shizuku expose public API tương đương,
 * nên chuyển sang dùng thẳng, bỏ reflection.
 */
public class ShizukuShell {

    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
        public boolean isSuccess() { return exitCode == 0; }
    }

    /** Chạy 1 lệnh shell đơn (không qua "sh -c" ghép chuỗi) để tránh escaping sai. */
    public static Result exec(String[] cmd) throws Exception {
        Method newProcess = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
        newProcess.setAccessible(true);
        Object remoteProcess = newProcess.invoke(null, cmd, null, null);

        Method getInputStream = remoteProcess.getClass().getMethod("getInputStream");
        Method getErrorStream = remoteProcess.getClass().getMethod("getErrorStream");
        Method waitFor = remoteProcess.getClass().getMethod("waitFor");

        InputStream stdout = (InputStream) getInputStream.invoke(remoteProcess);
        InputStream stderr = (InputStream) getErrorStream.invoke(remoteProcess);

        String outText = readAll(stdout);
        String errText = readAll(stderr);
        int exitCode = (int) waitFor.invoke(remoteProcess);

        return new Result(exitCode, outText, errText);
    }

    /** Tiện ích: chạy và ném exception rõ ràng nếu exit code != 0. */
    public static Result execOrThrow(String[] cmd) throws IOException {
        try {
            Result r = exec(cmd);
            if (!r.isSuccess()) {
                throw new IOException("Lệnh thất bại (" + r.exitCode + "): " + String.join(" ", cmd)
                        + (r.stderr.isEmpty() ? "" : " — " + r.stderr));
            }
            return r;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Không thể chạy lệnh qua Shizuku: " + e.getMessage(), e);
        }
    }

    private static String readAll(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // stream đã đóng hoặc rỗng — không sao, trả về những gì đọc được
        }
        return sb.toString();
    }
}
