package com.huluwa.console.service.fanqie;

/**
 * 番茄小说 App 接口用到的密码学原语（纯 Java 实现，无外部依赖）。
 *
 * <p>算法来源：`com.dragon.read` 的 `libfanqie.so` 逆向结论。
 * 已逐字节验证与 SO 行为一致的组件：</p>
 * <ul>
 *   <li>MD5 —— 标准实现，Java 自带</li>
 *   <li>SM3 —— 标准国密实现，SO 内联汇编自实现，已比对确认与标准算法一致</li>
 *   <li>AES-128/CBC/PKCS7 —— 标准实现，Java 自带</li>
 *   <li>Base64 —— 标准实现</li>
 * </ul>
 *
 * <p>注意：本类只放**已完全验证**的原语；签名头的算法在 {@link FanqieSigner}。</p>
 */
public final class FanqieCrypto {

    private FanqieCrypto() {
    }

    // ------------------------------------------------------------------
    // SM3（标准 GM/T 0004-2012）
    // ------------------------------------------------------------------

    private static final int[] SM3_IV = {
            0x7380166F, 0x4914B2B9, 0x172442D7, 0xDA8A0600,
            0xA96F30BC, 0x163138AA, 0xE38DEE4D, 0xB0FB0E4E
    };

    private static int rotl(int x, int n) {
        n &= 31;
        return n == 0 ? x : (x << n) | (x >>> (32 - n));
    }

    private static int p0(int x) {
        return x ^ rotl(x, 9) ^ rotl(x, 17);
    }

    private static int p1(int x) {
        return x ^ rotl(x, 15) ^ rotl(x, 23);
    }

    private static void compress(int[] v, byte[] data, int off) {
        int[] w = new int[68];
        for (int i = 0; i < 16; i++) {
            int p = off + i * 4;
            w[i] = ((data[p] & 0xFF) << 24) | ((data[p + 1] & 0xFF) << 16)
                    | ((data[p + 2] & 0xFF) << 8) | (data[p + 3] & 0xFF);
        }
        for (int j = 16; j < 68; j++) {
            w[j] = p1(w[j - 16] ^ w[j - 9] ^ rotl(w[j - 3], 15))
                    ^ rotl(w[j - 13], 7) ^ w[j - 6];
        }
        int[] w1 = new int[64];
        for (int j = 0; j < 64; j++) {
            w1[j] = w[j] ^ w[j + 4];
        }
        int a = v[0], b = v[1], c = v[2], d = v[3];
        int e = v[4], f = v[5], g = v[6], h = v[7];
        for (int j = 0; j < 64; j++) {
            int t = j < 16 ? 0x79CC4519 : 0x7A879D8A;
            int ff = j < 16 ? (a ^ b ^ c) : ((a & b) | (a & c) | (b & c));
            int gg = j < 16 ? (e ^ f ^ g) : ((e & f) | (~e & g));
            int a12 = rotl(a, 12);
            int ss1 = rotl(a12 + e + rotl(t, j), 7);
            int ss2 = ss1 ^ a12;
            int tt1 = ff + d + ss2 + w1[j];
            int tt2 = gg + h + ss1 + w[j];
            d = c;
            c = rotl(b, 9);
            b = a;
            a = tt1;
            h = g;
            g = rotl(f, 19);
            f = e;
            e = p0(tt2);
        }
        v[0] ^= a; v[1] ^= b; v[2] ^= c; v[3] ^= d;
        v[4] ^= e; v[5] ^= f; v[6] ^= g; v[7] ^= h;
    }

    /** 标准 SM3 摘要，返回 32 字节。 */
    public static byte[] sm3(byte[] input) {
        byte[] data = input == null ? new byte[0] : input;
        int n = data.length;
        int padLen = ((55 - n) % 64 + 64) % 64;
        int total = n + 1 + padLen + 8;
        byte[] buf = new byte[total];
        System.arraycopy(data, 0, buf, 0, n);
        buf[n] = (byte) 0x80;
        long bits = (long) n * 8L;
        for (int i = 0; i < 8; i++) {
            buf[total - 1 - i] = (byte) (bits >>> (8 * i));
        }
        int[] v = SM3_IV.clone();
        for (int off = 0; off < total; off += 64) {
            compress(v, buf, off);
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[i * 4] = (byte) (v[i] >>> 24);
            out[i * 4 + 1] = (byte) (v[i] >>> 16);
            out[i * 4 + 2] = (byte) (v[i] >>> 8);
            out[i * 4 + 3] = (byte) v[i];
        }
        return out;
    }

    /** SM3 摘要的小写十六进制串（64 字符）。 */
    public static String sm3Hex(byte[] input) {
        return hexLower(sm3(input));
    }

    // ------------------------------------------------------------------
    // 十六进制
    // ------------------------------------------------------------------

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String hexLower(byte[] data) {
        if (data == null) {
            return "";
        }
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = HEX[(data[i] >>> 4) & 0xF];
            out[i * 2 + 1] = HEX[data[i] & 0xF];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String s = hex.trim();
        if ((s.length() & 1) != 0) {
            throw new IllegalArgumentException("十六进制串长度必须为偶数");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // PKCS7
    // ------------------------------------------------------------------

    public static byte[] pkcs7Pad(byte[] data, int blockSize) {
        int pad = blockSize - (data.length % blockSize);
        byte[] out = new byte[data.length + pad];
        System.arraycopy(data, 0, out, 0, data.length);
        for (int i = data.length; i < out.length; i++) {
            out[i] = (byte) pad;
        }
        return out;
    }

    public static byte[] pkcs7Unpad(byte[] data, int blockSize) {
        if (data == null || data.length == 0) {
            return data;
        }
        int pad = data[data.length - 1] & 0xFF;
        if (pad == 0 || pad > blockSize || pad > data.length) {
            return data;
        }
        for (int i = data.length - pad; i < data.length; i++) {
            if ((data[i] & 0xFF) != pad) {
                return data;
            }
        }
        byte[] out = new byte[data.length - pad];
        System.arraycopy(data, 0, out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------
    // 小端整数
    // ------------------------------------------------------------------

    public static byte[] le64(long value) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (value >>> (8 * i));
        }
        return out;
    }

    public static byte[] le32(long value) {
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (byte) (value >>> (8 * i));
        }
        return out;
    }
}
