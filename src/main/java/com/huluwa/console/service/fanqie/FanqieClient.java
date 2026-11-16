package com.huluwa.console.service.fanqie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.util.HtmlUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 番茄小说 App 协议客户端（纯 Java 实现，对应 fanqie/fanqie_api.py）。
 *
 * <p>算法来源见 {@link FanqieCrypto} 与 fanqie/ 目录下的逆向报告。本类补齐签名、
 * 设备注册、业务接口与整本下载，密码学原语复用 {@link FanqieCrypto}。</p>
 *
 * <p>接口链路与关键约束（与 Python 版一致）：</p>
 * <ol>
 *   <li>设备注册：POST {@code i.snssdk.com/service/2/device_register/?tt_data=a}，
 *       请求体为 tt_encrypt(JSON)，<b>不得携带</b> x-argus / x-ladon / x-ss-stub。</li>
 *   <li>密钥交换：POST {@code /reading/crypt/registerkey}，<b>不得携带</b> x-ss-stub，
 *       否则返回 200 空 body。</li>
 *   <li>正文：{@code /reading/reader/(batch_)full/v}，必须携带有效 keyver。</li>
 * </ol>
 */
public final class FanqieClient {

    // ==================================================================
    // 常量
    // ==================================================================

    /** AES-128 密钥固定长度。 */
    private static final int BLOCK = 16;

    /** .so RVA 0x1B873D 处的 64 字节常量。 */
    private static final byte[] TT_CONST = FanqieCrypto.fromHex(
            "4dd4c2e6b83162090e52b3c7a6733ba41cb2462b829ab58a196b39db5717752"
                    + "4f49baf7f08e8d68d26a72e37c1a95a2f1f05a51892aef2949732b62a38aadd58");

    /** 固定头：b"tc" + 0x05 0x10 0x00 0x00，其后紧跟 32 字节随机数 R。 */
    private static final byte[] TT_HDR_PREFIX = {'t', 'c', 0x05, 0x10, 0x00, 0x00};

    /** SHA-512 IV（小端 u64）。 */
    private static final byte[] SHA512_IV = {
            (byte) 0x08, (byte) 0xc9, (byte) 0xbc, (byte) 0xf3, (byte) 0x67, (byte) 0xe6, (byte) 0x09, (byte) 0x6a,
            (byte) 0x3b, (byte) 0xa7, (byte) 0xca, (byte) 0x84, (byte) 0x85, (byte) 0xae, (byte) 0x67, (byte) 0xbb,
            (byte) 0x2b, (byte) 0xf8, (byte) 0x94, (byte) 0xfe, (byte) 0x72, (byte) 0xf3, (byte) 0x6e, (byte) 0x3c,
            (byte) 0xf1, (byte) 0x36, (byte) 0x1d, (byte) 0x5f, (byte) 0x3a, (byte) 0xf5, (byte) 0x4f, (byte) 0xa5,
            (byte) 0xd1, (byte) 0x82, (byte) 0xe6, (byte) 0xad, (byte) 0x7f, (byte) 0x52, (byte) 0x0e, (byte) 0x51,
            (byte) 0x1f, (byte) 0x6c, (byte) 0x3e, (byte) 0x2b, (byte) 0x8c, (byte) 0x68, (byte) 0x05, (byte) 0x9b,
            (byte) 0x6b, (byte) 0xbd, (byte) 0x41, (byte) 0xfb, (byte) 0xab, (byte) 0xd9, (byte) 0x83, (byte) 0x1f,
            (byte) 0x79, (byte) 0x21, (byte) 0x7e, (byte) 0x13, (byte) 0x19, (byte) 0xcd, (byte) 0xe0, (byte) 0x5b
    };

    /** x-argus 相关常量。 */
    private static final byte[] ARGUS_KEY8 = FanqieCrypto.fromHex("f2f7fcfff2f7fcff");
    private static final byte[] ARGUS_C9 = FanqieCrypto.fromHex("a66ead9f7701d00c18");
    private static final byte[] ARGUS_MAGIC = {(byte) 0xf2, (byte) 0x81};
    private static final byte[] ARGUS_AES_KEY = FanqieCrypto.fromHex("d31e3718288a1027baab59f146a09a9c");
    private static final byte[] ARGUS_AES_IV = FanqieCrypto.fromHex("ea180a0336ed352fcd24e4d50018ae54");
    private static final long ARGUS_CONST9 = 0x04531c328ae84313L;
    private static final long ARGUS_X27 = 0x3dc94c3a046d678bL;
    private static final long[] ARGUS_BUF_INIT = {
            0x740c7a65a9e078fcL, 0x03cf3c905915e58cL,
            0xd732f2cfd3510e51L, ARGUS_CONST9
    };

    /** x-argus 的 72 轮消息扩展工作区（纯常量，类加载时算一次）。 */
    private static final long[] ARGUS_W = buildArgusW();

    /** x-ladon 常量。 */
    private static final String LADON_MAGIC = "-1611921764-";
    private static final String[] LADON_B = {"3019", "1967"};
    private static final String LADON_A_DEFAULT = "0";
    private static final int LADON_PAD = 0x06;

    /** 固定密钥（.so 0x17d200），用于 registerkey 的 content 加解密。 */
    private static final byte[] K0 = FanqieCrypto.fromHex("ac25c67ddd8f38c1b37a2348828e222e");

    public static final String API_HOST = "https://api5-normal.fqnovel.com";
    public static final String REG_HOST = "https://i.snssdk.com";
    public static final String REG_PATH = "/service/2/device_register/?tt_data=a";

    private static final String UA = "com.dragon.read/70132 (Linux; U; Android 11; zh_CN; "
            + "SM-G9750; Build/RP1A.200720.011)";

    /** App 下载节奏（逆向自 libfanqie.so）：每组 8 章、100ms 间隔、串行。 */
    public static final int APP_BATCH_SIZE = 8;
    public static final long APP_INTERVAL_MS = 100;
    public static final int QUOTA_CHAPTERS = 800;
    public static final int COOLDOWN_SECONDS = 35;

    /** U+FFFD 替换符，用于 UTF-8 容错解码。 */
    private static final Pattern P_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern P_BR = Pattern.compile("<br\\s*/?>");
    private static final Pattern P_ENTITY =
            Pattern.compile("&(?:#([0-9]+)|#[xX]([0-9a-fA-F]+)|[A-Za-z][A-Za-z0-9]+);");
    private static final Pattern P_P = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern P_CONTENT_DIV =
            Pattern.compile("<div[^>]*class=\"[^\"]*content[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Random random = new Random();

    private String host = API_HOST;
    private Duration timeout = Duration.ofSeconds(45);
    private int requestRetries = 3;

    public FanqieClient() { }

    /** 交互阅读使用较短超时；命令行整本下载仍使用默认退避。 */
    public FanqieClient(Duration timeout, int requestRetries) {
        this.timeout = timeout;
        this.requestRetries = Math.max(0, requestRetries);
    }
    private long intervalMs = APP_INTERVAL_MS;

    private String deviceId;
    private String installId;
    private String cdid;
    private byte[] contentKey;
    private Integer keyver;
    private Map<String, String> params;

    /** 本设备已取章节数（配额跟踪）。 */
    private int fetched;

    // ==================================================================
    // 公开数据结构
    // ==================================================================

    /** 搜索结果里的书目。 */
    public record Book(String bookId, String bookName, String author, String abstractText,
                       long wordNumber, Integer creationStatus, boolean finished,
                       double score, long readCount, String category, String thumbUrl,
                       String lastChapterTitle) {
    }

    /** 目录章节。 */
    public record ChapterEntry(String itemId, String title, long order, String volumeName, long wordNumber) {
        public ChapterEntry {
            // 同时覆盖上游新目录和磁盘旧目录的反序列化。
            title = FanqieChapterParser.normalizeTitle(title);
        }
    }

    /** 单章正文。 */
    public record ChapterText(String title, List<String> paragraphs, String text) {
    }

    /** 批量下载结果。 */
    public record Fetched(String itemId, String title, String text, long chars, String error) {
    }

    /** 一页搜索结果：书目列表 + 下一页游标（passback，null 表示没有更多）。 */
    public record SearchPage(List<Book> books, String cursor) {
    }

    // ==================================================================
    // 一、AES 与压缩
    // ==================================================================

    /**
     * AES-128-CBC 加解密，<b>不做自动填充</b>。
     *
     * <p>填充由调用方按 Python 版的 pkcs7_pad / pkcs7_unpad 手工处理，
     * 保证与 fanqie_api.py 的输出逐字节一致（内容密文尾部可能不遵循标准 PKCS7，
     * 用自动填充会抛 BadPaddingException）。</p>
     */
    private static byte[] aes(byte[] key, byte[] iv, byte[] data, int mode) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return c.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("AES 运算失败: " + ex.getMessage(), ex);
        }
    }

    static byte[] aesEncrypt(byte[] key, byte[] iv, byte[] data) {
        if (data.length % BLOCK != 0) {
            throw new IllegalArgumentException("AES 密文长度必须是 16 的倍数");
        }
        return aes(key, iv, data, Cipher.ENCRYPT_MODE);
    }

    static byte[] aesDecrypt(byte[] key, byte[] iv, byte[] data) {
        if (data.length % BLOCK != 0) {
            throw new IllegalArgumentException("AES 密文长度必须是 16 的倍数");
        }
        return aes(key, iv, data, Cipher.DECRYPT_MODE);
    }

    /**
     * 生成与 zlib-rs（Rust flate2）默认参数一致的 gzip 流。
     *
     * <p>与 CPython zlib 只差 gzip 头 2 字节：XFL=0x00、OS=0xff，这里手工拼头保证一致。</p>
     */
    static byte[] gzipRs(byte[] data, int level) {
        Deflater def = new Deflater(level, true);
        def.setInput(data);
        def.finish();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        while (!def.finished()) {
            int n = def.deflate(buf);
            body.write(buf, 0, n);
        }
        def.end();

        CRC32 crc = new CRC32();
        crc.update(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1f);
        out.write(0x8b);
        out.write(0x08);           // CM = deflate
        out.write(0x00);           // FLG
        out.write(new byte[4], 0, 4);  // MTIME
        out.write(0x00);           // XFL
        out.write(0xff);           // OS = unknown
        byte[] compressed = body.toByteArray();
        out.write(compressed, 0, compressed.length);
        out.write(FanqieCrypto.le32(crc.getValue()), 0, 4);
        out.write(FanqieCrypto.le32(data.length & 0xFFFFFFFFL), 0, 4);
        return out.toByteArray();
    }

    // ==================================================================
    // 二、SHA-512 压缩函数 / rev64（tt_encrypt 需要中间态）
    // ==================================================================

    private static final long[] K512 = {
            0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fL, 0xe9b5dba58189dbbcL,
            0x3956c25bf348b538L, 0x59f111f1b605d019L, 0x923f82a4af194f9bL, 0xab1c5ed5da6d8118L,
            0xd807aa98a3030242L, 0x12835b0145706fbeL, 0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L,
            0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L, 0x9bdc06a725c71235L, 0xc19bf174cf692694L,
            0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L, 0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
            0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L,
            0x983e5152ee66dfabL, 0xa831c66d2db43210L, 0xb00327c898fb213fL, 0xbf597fc7beef0ee4L,
            0xc6e00bf33da88fc2L, 0xd5a79147930aa725L, 0x06ca6351e003826fL, 0x142929670a0e6e70L,
            0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL, 0x53380d139d95b3dfL,
            0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47edaee6L, 0x92722c851482353bL,
            0xa2bfe8a14cf10364L, 0xa81a664bbc423001L, 0xc24b8b70d0f89791L, 0xc76c51a30654be30L,
            0xd192e819d6ef5218L, 0xd69906245565a910L, 0xf40e35855771202aL, 0x106aa07032bbd1b8L,
            0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L, 0x2748774cdf8eeb99L, 0x34b0bcb5e19b48a8L,
            0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L, 0x682e6ff3d6b2b8a3L,
            0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72L, 0x8cc702081a6439ecL,
            0x90befffa23631e28L, 0xa4506cebde82bde9L, 0xbef9a3f7b2c67915L, 0xc67178f2e372532bL,
            0xca273eceea26619cL, 0xd186b8c721c0c207L, 0xeada7dd6cde0eb1eL, 0xf57d4f7fee6ed178L,
            0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L, 0x113f9804bef90daeL, 0x1b710b35131c471bL,
            0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL, 0x431d67c49c100d4cL,
            0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L
    };

    static long ror(long x, int n) {
        n &= 63;
        return n == 0 ? x : (x >>> n) | (x << (64 - n));
    }

    private static long leLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xFFL) << (8 * i);
        }
        return v;
    }

    private static byte[] leLongBytes(long v) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (v >>> (8 * i));
        }
        return out;
    }

    /** 单块 SHA-512 压缩：state 64 字节（小端 u64），block 128 字节原始块。 */
    static byte[] compress512(byte[] state, byte[] block) {
        if (state.length != 64 || block.length != 128) {
            throw new IllegalArgumentException("compress512: state 需 64B、block 需 128B");
        }
        long[] v = new long[8];
        for (int i = 0; i < 8; i++) {
            v[i] = leLong(state, i * 8);
        }
        long[] w = new long[80];
        for (int i = 0; i < 16; i++) {
            w[i] = beLong(block, i * 8);
        }
        for (int i = 16; i < 80; i++) {
            long s0 = ror(w[i - 15], 1) ^ ror(w[i - 15], 8) ^ (w[i - 15] >>> 7);
            long s1 = ror(w[i - 2], 19) ^ ror(w[i - 2], 61) ^ (w[i - 2] >>> 6);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        long a = v[0], b = v[1], c = v[2], d = v[3];
        long e = v[4], f = v[5], g = v[6], h = v[7];
        for (int i = 0; i < 80; i++) {
            long s1 = ror(e, 14) ^ ror(e, 18) ^ ror(e, 41);
            long ch = (e & f) ^ ((~e) & g);
            long t1 = h + s1 + ch + K512[i] + w[i];
            long s0 = ror(a, 28) ^ ror(a, 34) ^ ror(a, 39);
            long maj = (a & b) ^ (a & c) ^ (b & c);
            long t2 = s0 + maj;
            h = g;
            g = f;
            f = e;
            e = d + t1;
            d = c;
            c = b;
            b = a;
            a = t1 + t2;
        }
        byte[] out = new byte[64];
        long[] nv = {v[0] + a, v[1] + b, v[2] + c, v[3] + d, v[4] + e, v[5] + f, v[6] + g, v[7] + h};
        for (int i = 0; i < 8; i++) {
            System.arraycopy(leLongBytes(nv[i]), 0, out, i * 8, 8);
        }
        return out;
    }

    private static long beLong(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[off + i] & 0xFFL);
        }
        return v;
    }

    /** arm64 {@code rev64 v.16b} 等价：每 8 字节一组内部字节反转。 */
    static byte[] rev64(byte[] data) {
        byte[] out = new byte[data.length - data.length % 8];
        for (int i = 0; i + 8 <= out.length; i += 8) {
            for (int j = 0; j < 8; j++) {
                out[i + j] = data[i + 7 - j];
            }
        }
        return out;
    }

    // ==================================================================
    // 三、tt_encrypt（设备注册请求体加密）
    // ==================================================================

    /** 由 32 字节随机数 R 推导 64 字节密钥材料 D。 */
    static byte[] deriveD(byte[] r) {
        if (r.length != 32) {
            throw new IllegalArgumentException("deriveD: R 必须 32 字节");
        }
        byte[] blk0 = new byte[128];
        System.arraycopy(r, 0, blk0, 0, 32);
        blk0[32] = (byte) 0x80;
        // 长度 256 位，放在最后一个 16 字节大端
        blk0[126] = (byte) 0x01;
        blk0[127] = (byte) 0x00;
        byte[] a = compress512(SHA512_IV, blk0);

        byte[] blk1 = new byte[128];
        byte[] ra = rev64(a);
        System.arraycopy(ra, 0, blk1, 0, 64);
        System.arraycopy(TT_CONST, 0, blk1, 64, 64);
        byte[] b = compress512(SHA512_IV, blk1);

        byte[] blk2 = new byte[128];
        blk2[0] = (byte) 0x80;
        // 长度 1024 位
        blk2[126] = (byte) 0x04;
        blk2[127] = (byte) 0x00;
        byte[] c = compress512(b, blk2);
        return rev64(c);
    }

    /** 加密设备指纹/请求体，返回 38 字节头 + AES-128-CBC/PKCS7 密文。 */
    public static byte[] ttEncrypt(byte[] data, byte[] r) {
        if (r == null) {
            r = new byte[32];
            new Random().nextBytes(r);
        }
        if (r.length != 32) {
            throw new IllegalArgumentException("ttEncrypt: R 必须 32 字节");
        }
        byte[] d = deriveD(r);
        byte[] gz = gzipRs(data, 6);
        byte[] sha = sha512(gz);
        byte[] pt = new byte[64 + gz.length];
        System.arraycopy(sha, 0, pt, 0, 64);
        System.arraycopy(gz, 0, pt, 64, gz.length);

        byte[] key = slice(d, 0, 16);
        byte[] iv = slice(d, 16, 16);
        byte[] ct = aesEncrypt(key, iv, FanqieCrypto.pkcs7Pad(pt, BLOCK));
        byte[] out = new byte[38 + ct.length];
        System.arraycopy(TT_HDR_PREFIX, 0, out, 0, 6);
        System.arraycopy(r, 0, out, 6, 32);
        System.arraycopy(ct, 0, out, 38, ct.length);
        return out;
    }

    /** 已知该次调用的 R 时，把 tt_encrypt 输出解密回原文。 */
    public static byte[] ttDecryptWithR(byte[] blob, byte[] r) {
        byte[] d = deriveD(r);
        byte[] pt = FanqieCrypto.pkcs7Unpad(
                aesDecrypt(slice(d, 0, 16), slice(d, 16, 16), slice(blob, 38, blob.length - 38)),
                BLOCK);
        byte[] innerSha = slice(pt, 0, 64);
        byte[] gz = slice(pt, 64, pt.length - 64);
        if (!MessageDigest.isEqual(sha512(gz), innerSha)) {
            throw new IllegalArgumentException("tt_encrypt 内层 sha512 校验失败");
        }
        return gunzip(gz);
    }

    static byte[] sha512(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] gzip(byte[] data) {
        return gzipRs(data, 6);
    }

    static byte[] gunzip(byte[] data) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        } catch (IOException gzError) {
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
                return in.readAllBytes();
            } catch (IOException zlibError) {
                throw new IllegalArgumentException("gzip 解压失败: " + zlibError.getMessage(), zlibError);
            }
        }
    }

    private static byte[] slice(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    // ==================================================================
    // 四、x-ladon
    // ==================================================================

    /** md5(le32(ts) || aid) 的 32 个 ASCII hex 字符 -> 4 个 LE-u64。 */
    private static long[] ladonSeed(long ts, String aid) {
        byte[] tsBytes = FanqieCrypto.le32(ts);
        byte[] aidBytes = aid.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[4 + aidBytes.length];
        System.arraycopy(tsBytes, 0, msg, 0, 4);
        System.arraycopy(aidBytes, 0, msg, 4, aidBytes.length);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("MD5").digest(msg);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        StringBuilder hex = new StringBuilder(32);
        for (byte b : digest) {
            int v = b & 0xFF;
            hex.append(Character.forDigit(v >>> 4, 16)).append(Character.forDigit(v & 0xF, 16));
        }
        long[] out = new long[4];
        for (int i = 0; i < 4; i++) {
            // 取 8 个 ASCII 字符，按小端解释（等价 Python int.from_bytes(..., 'little')）
            byte[] chunk = hex.substring(i * 8, i * 8 + 8).getBytes(StandardCharsets.US_ASCII);
            byte[] rev = new byte[8];
            for (int j = 0; j < 8; j++) {
                rev[j] = chunk[7 - j];
            }
            out[i] = new BigInteger(1, rev).longValue();
        }
        return out;
    }

    /** 34 轮滚动混合 -> 35 个 u64 缓冲区。 */
    private static long[] ladonMix(long[] seed) {
        long[] q = new long[4 + 34];
        System.arraycopy(seed, 0, q, 0, 4);
        long acc = q[0];
        long[] buf = new long[35];
        buf[0] = acc;
        for (int i = 0; i < 34; i++) {
            long t = (ror(q[i + 1], 8) + acc) ^ i;
            acc = t ^ ror(acc, 61);
            buf[i + 1] = acc;
            q[4 + i] = t;
        }
        return buf;
    }

    /** 34 字折叠 -> 两个 u64。返回 {x19 ^ ror(x9,61), x19}。 */
    private static long[] ladonFold(long[] seq, long x22, long x25) {
        long x8 = (ror(x25, 8) + x22) ^ seq[0];
        long x9 = x8 ^ ror(x22, 61);
        for (int i = 1; i < seq.length - 1; i++) {
            x8 = (ror(x8, 8) + x9) ^ seq[i];
            x9 = x8 ^ ror(x9, 61);
        }
        long x19 = (ror(x8, 8) + x9) ^ seq[seq.length - 1];
        return new long[]{x19 ^ ror(x9, 61), x19};
    }

    /** 生成 x-ladon。flag=0 用于请求头；A 默认 "0"（服务端不校验）。 */
    public static String xLadon(long ts, int flag, String aid, String a) {
        long[] seed = ladonSeed(ts, aid);
        long[] buf = ladonMix(seed);
        StringBuilder sb = new StringBuilder();
        sb.append(a == null ? LADON_A_DEFAULT : a).append(LADON_MAGIC).append(LADON_B[flag]);
        byte[] seedBytes = new byte[32];
        java.util.Arrays.fill(seedBytes, (byte) LADON_PAD);
        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, seedBytes, 0, Math.min(raw.length, 32));
        long[] seq = new long[34];
        System.arraycopy(buf, 0, seq, 0, 34);
        long[] r1 = ladonFold(seq, leLong(seedBytes, 0), leLong(seedBytes, 8));
        long[] r2 = ladonFold(seq, leLong(seedBytes, 16), leLong(seedBytes, 24));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FanqieCrypto.le32(ts), 0, 4);
        out.write(leLongBytes(r1[0]), 0, 8);
        out.write(leLongBytes(r1[1]), 0, 8);
        out.write(leLongBytes(r2[0]), 0, 8);
        out.write(leLongBytes(r2[1]), 0, 8);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    // ==================================================================
    // 五、x-argus
    // ==================================================================

    /** 消息扩展 68 轮，产出 72 个 u64 工作区。 */
    private static long[] buildArgusW() {
        long[] w = new long[72];
        System.arraycopy(ARGUS_BUF_INIT, 0, w, 0, 4);
        long x9 = ARGUS_CONST9;
        for (int j = 0; j < 68; j++) {
            int shift = (j + 4 < 0x42) ? j : (j - 0x3e);
            long x13 = ARGUS_X27 >>> (shift & 63);
            x9 = w[j + 1] ^ ror(x9, 3);
            long t = (x13 & 1L) | 0xfffffffffffffffcL;
            long r = w[j] ^ ror(x9, 1);
            x9 = t ^ x9;
            x9 = r ^ x9;
            w[(j + 4) % 72] = x9;
        }
        return w;
    }

    /** 按 16 字节分块压缩 72 轮；每块输出 (x26, x23) 共 16 字节。 */
    static byte[] argusHash160(byte[] proto) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < proto.length; i += 16) {
            long lo = leLong(proto, i);
            long hi = leLong(proto, i + 8);
            long x23 = hi;
            long x26 = lo;
            for (int k = 0; k < 72; k++) {
                long x9 = (ror(x23, 63) & ror(x23, 56));
                long x11 = x26 ^ ror(x23, 62);
                long next23 = (x11 ^ ARGUS_W[k]) ^ x9;
                x26 = x23;
                x23 = next23;
            }
            out.write(leLongBytes(x26), 0, 8);
            out.write(leLongBytes(x23), 0, 8);
        }
        return out.toByteArray();
    }

    /** 每 8 字节单元内反转 + 单元顺序颠倒。 */
    static byte[] mirror8(byte[] b) {
        int n = b.length - b.length % 8;
        byte[] out = new byte[n];
        for (int i = 0, u = n / 8 - 1; i < n; i += 8, u--) {
            for (int j = 0; j < 8; j++) {
                out[i + j] = b[u * 8 + 7 - j];
            }
        }
        return out;
    }

    private static byte[] varint(long v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int x = (int) (v & 0x7F);
            v >>>= 7;
            out.write(x | (v != 0 ? 0x80 : 0));
            if (v == 0) {
                return out.toByteArray();
            }
        }
    }

    private static byte[] protoBytes(int n, byte[] v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tag = varint((long) n << 3 | 2);
        out.write(tag, 0, tag.length);
        byte[] len = varint(v.length);
        out.write(len, 0, len.length);
        out.write(v, 0, v.length);
        return out.toByteArray();
    }

    private static byte[] protoBytes(int n, String v) {
        return protoBytes(n, v.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] protoVarint(int n, long v) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tag = varint((long) n << 3);
        out.write(tag, 0, tag.length);
        byte[] val = varint(v);
        out.write(val, 0, val.length);
        return out.toByteArray();
    }

    /** 构造 x-argus 的 protobuf 输入（长度按 16 字节对齐）。 */
    public static byte[] buildProto(String query, String aid, String deviceId, String iid,
                                    long millis, String version) {
        byte[] sm3q = FanqieCrypto.sm3(query.getBytes(StandardCharsets.UTF_8));
        long secs = millis / 1000;
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        try {
            p.write(protoVarint(1, 0x40401252L));
            p.write(protoVarint(2, 2));
            p.write(protoVarint(3, 0x7a55300bL));
            p.write(protoBytes(4, aid));
            p.write(protoBytes(5, deviceId));
            p.write(protoBytes(6, "1611921764"));
            p.write(protoBytes(7, version));
            p.write(protoBytes(8, "v04.04.05-ov-android"));
            p.write(protoVarint(9, 134744640L));
            p.write(protoBytes(10, new byte[8]));
            p.write(protoVarint(11, 0));
            p.write(protoVarint(12, secs * 2));
            p.write(protoBytes(13, FanqieCrypto.fromHex("106e34a2b8c7")));
            p.write(protoBytes(14, slice(sm3q, 0, 6)));
            p.write(protoBytes(15, FanqieCrypto.fromHex("08011001180138ccd9cbbc0c")));
            p.write(protoBytes(16, new byte[0]));
            p.write(protoBytes(20, "none"));
            p.write(protoVarint(21, 738));
            p.write(protoBytes(23, FanqieCrypto.fromHex("0a064e583535314a1084402080b0838708")));
            p.write(FanqieCrypto.fromHex("c80102"));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        byte[] body = p.toByteArray();
        int rem = body.length % 16;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(body, 0, body.length);
        if (rem != 0) {
            int n = 16 - rem;
            for (int i = 0; i < n; i++) {
                out.write(n);
            }
        } else {
            out.write(1);
        }
        return out.toByteArray();
    }

    /** 由 proto 生成 x-argus 签名串（proto 长度须为 16 的倍数）。 */
    public static String xArgus(byte[] proto) {
        if (proto.length % 16 != 0) {
            throw new IllegalArgumentException("proto 长度须为 16 的倍数，实际 " + proto.length);
        }
        byte[] h = argusHash160(proto);
        byte[] s = new byte[h.length];
        for (int i = 0; i < h.length; i++) {
            s[i] = (byte) (h[i] ^ ARGUS_KEY8[i % 8]);
        }
        byte[] keySrc = new byte[ARGUS_KEY8.length + s.length];
        System.arraycopy(ARGUS_KEY8, 0, keySrc, 0, ARGUS_KEY8.length);
        System.arraycopy(s, 0, keySrc, ARGUS_KEY8.length, s.length);
        byte[] m = mirror8(keySrc);
        byte[] msg = new byte[ARGUS_C9.length + m.length + 2];
        System.arraycopy(ARGUS_C9, 0, msg, 0, ARGUS_C9.length);
        System.arraycopy(m, 0, msg, ARGUS_C9.length, m.length);
        msg[msg.length - 2] = 0x61;
        msg[msg.length - 1] = 0x6f;
        byte[] ct = aesEncrypt(ARGUS_AES_KEY, ARGUS_AES_IV, FanqieCrypto.pkcs7Pad(msg, BLOCK));
        byte[] out = new byte[ARGUS_MAGIC.length + ct.length];
        System.arraycopy(ARGUS_MAGIC, 0, out, 0, ARGUS_MAGIC.length);
        System.arraycopy(ct, 0, out, ARGUS_MAGIC.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    // ==================================================================
    // 六、请求参数与设备注册体
    // ==================================================================

    /** 30 项请求参数（与 .so default_params @0x698D40 等价）。 */
    private static final String[][] DEFAULT_PARAMS_TEMPLATE = {
            {"ac", "wifi"}, {"aid", "1967"}, {"app_name", "novelapp"},
            {"channel", "43536163a"}, {"device_platform", "android"}, {"os", "android"},
            {"ssmix", "a"}, {"device_type", "SM-G9750"}, {"device_brand", "Xiaomi"},
            {"language", "zh"}, {"os_api", "32"}, {"os_version", "11"},
            {"version_code", "70132"}, {"version_name", "7.0.1.32"},
            {"manifest_version_code", "70132"}, {"resolution", "720*1280"}, {"dpi", "240"},
            {"update_version_code", "70132"}, {"_rticket", "0"}, {"host_abi", "arm64-v8a"},
            {"dragon_device_type", "0"}, {"pv_player", "70132"}, {"compliance_status", "0"},
            {"need_personal_recommend", "1"}, {"player_so_load", "1"},
            {"is_android_pad_screen", "0"},
            {"rom_version", "coloros__B05A.81282.76653161 release-keys"},
    };

    /** 构造一套完整请求参数。 */
    public static Map<String, String> defaultParams(String deviceId, String installId, String cdid) {
        Map<String, String> p = new LinkedHashMap<>();
        for (String[] e : DEFAULT_PARAMS_TEMPLATE) {
            p.put(e[0], e[1]);
        }
        p.put("device_id", deviceId);
        p.put("iid", installId);
        p.put("cdid", cdid == null ? uuid4() : cdid);
        return p;
    }

    static String uuid4() {
        UUID u = UUID.randomUUID();     // 版本位与变体位符合 v4 规范
        return u.toString();
    }

    /** 构造 device_register 的 JSON 请求体（随机字段每次重新生成）。 */
    private byte[] registrationBody() {
        long nowMs = System.currentTimeMillis();
        String uuid = uuid4();
        byte[] openBytes = new byte[10];
        random.nextBytes(openBytes);
        String openudid = FanqieCrypto.hexLower(openBytes);

        ObjectNode root = json.createObjectNode();
        root.put("_gen_time", nowMs);
        root.put("magic_tag", "ss_app_log");
        ObjectNode h = root.putObject("header");
        h.put("access", "wifi");
        h.put("aid", 1967);
        h.put("apk_first_install_time", nowMs - 86400000L);
        h.put("carrier", "CHINA MOBILE");
        h.put("cdid", uuid);
        h.put("channel", "43536163a");
        h.put("clientudid", uuid);
        h.put("cpu_abi", "arm64-v8a");
        ObjectNode custom = h.putObject("custom");
        custom.put("dragon_device_type", 0);
        custom.put("host_bit", 32);
        h.put("density_dpi", 240);
        h.put("device_brand", "Xiaomi");
        h.put("device_manufacturer", "Xiaomi");
        h.put("device_model", "SM-G9750");
        h.put("device_platform", "android");
        h.put("display_density", "hdpi");
        h.put("display_name", "番茄免费小说");
        h.put("git_hash", "711d1a7");
        h.put("guest_mode", 0);
        ArrayNode ipv6 = h.putArray("ipv6_list");
        ObjectNode ipv6Item = ipv6.addObject();
        ipv6Item.put("type", "client_anpi");
        ipv6Item.put("value", "::1");
        h.put("is_system_app", 0);
        h.put("language", "zh");
        h.put("mcc_mnc", "46000");
        h.put("not_request_sender", 0);
        h.put("oaid_may_support", false);
        h.put("openudid", openudid);
        h.put("os", "Android");
        h.put("os_api", 32);
        h.put("os_version", "11");
        h.put("package", "com.dragon.read");
        h.put("pre_installed_channel", "");
        h.put("region", "CN");
        h.put("release_build", "b05af49");
        h.put("req_id", uuid);
        h.put("resolution", "720x1280");
        h.put("rom", "B05AF49E38D27C");
        h.put("rom_version", "coloros__B05A.81282.76653161 release-keys");
        h.put("sdk_flavor", "china");
        h.put("sdk_target_version", 29);
        h.put("sdk_version", "3.7.0-rc.25-fanqie-xiaoshuo");
        h.put("sig_hash", "a4a27c2633195374c15651ffc3c4a497");
        h.put("sim_region", "cn");
        h.putArray("sim_serial_number");
        h.put("timezone", 8);
        h.put("tz_name", "Asia/Shanghai");
        h.put("tz_offset", 28800);
        try {
            return json.writeValueAsBytes(root);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ==================================================================
    // 七、业务层：网络请求与正文处理
    // ==================================================================

    /** 构造 registerkey 的 content 字段。 */
    static String makeRegisterContent(String deviceId) {
        long id = Long.parseUnsignedLong(deviceId);
        byte[] pt = FanqieCrypto.pkcs7Pad(leLongBytes(id), BLOCK);
        byte[] iv = new byte[16];
        new Random().nextBytes(iv);
        byte[] ct = aesEncrypt(K0, iv, pt);
        byte[] out = new byte[16 + ct.length];
        System.arraycopy(iv, 0, out, 0, 16);
        System.arraycopy(ct, 0, out, 16, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /** 解密 registerkey 响应里的 key，得到 16 字节 content_key。 */
    static byte[] decryptRegisterKey(String keyB64) {
        byte[] raw = Base64.getDecoder().decode(keyB64);
        if (raw.length < 32) {
            throw new IllegalArgumentException("registerkey key too short: " + raw.length);
        }
        byte[] iv = slice(raw, 0, 16);
        byte[] ct = slice(raw, 16, raw.length - 16);
        return FanqieCrypto.pkcs7Unpad(aesDecrypt(K0, iv, ct), BLOCK);
    }

    /** 解密正文密文，返回 XHTML 字符串。 */
    public static String decryptContent(String contentB64, byte[] contentKey) {
        byte[] raw = Base64.getDecoder().decode(contentB64);
        if (raw.length < 32 || raw.length % 16 != 0) {
            throw new IllegalArgumentException("bad content ciphertext length " + raw.length);
        }
        byte[] iv = slice(raw, 0, 16);
        byte[] ct = slice(raw, 16, raw.length - 16);
        // 正文密文并不总是标准 PKCS7（gzip 尾部自带对齐），只做容错去填充
        byte[] pt = FanqieCrypto.pkcs7Unpad(aesDecrypt(contentKey, iv, ct), BLOCK);
        return new String(gunzip(pt), StandardCharsets.UTF_8);
    }

    /** 从章节 XHTML 中提取纯文本段落。 */
    public static List<String> xhtmlToParagraphs(String xhtml) {
        int i = xhtml.indexOf("<body");
        String body = i > 0 ? xhtml.substring(i) : xhtml;
        List<String> out = new ArrayList<>();
        Matcher m = P_P.matcher(body);
        if (!m.find()) {
            m = P_CONTENT_DIV.matcher(body);
        } else {
            m.reset();
        }
        while (m.find()) {
            String s = P_BR.matcher(m.group(1)).replaceAll("\n");
            s = P_TAG.matcher(s).replaceAll("");
            s = decodeHtmlEntities(s).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 只解码实体，保留纯文本里的换行和尖括号；非法数字实体原样保留。 */
    public static String decodeHtmlEntities(String text) {
        if (text == null) return "";
        if (text.indexOf('&') < 0) return text;
        return P_ENTITY.matcher(text).replaceAll(match -> {
            String entity = match.group();
            String decimal = match.group(1);
            String hex = match.group(2);
            String decoded;
            if (decimal != null || hex != null) {
                try {
                    int codePoint = Integer.parseInt(decimal != null ? decimal : hex, decimal != null ? 10 : 16);
                    if (codePoint == 0 || !Character.isValidCodePoint(codePoint) ||
                            (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                        return entity;
                    }
                    decoded = new String(Character.toChars(codePoint));
                } catch (NumberFormatException error) {
                    return entity;
                }
            } else {
                decoded = "&apos;".equals(entity) ? "'" : HtmlUtils.htmlUnescape(entity);
            }
            // replaceAll 的替换值可能包含 $ 或反斜杠，必须按字面量处理。
            return Matcher.quoteReplacement(decoded.replace('\u00a0', ' '));
        });
    }

    // ------------------------------------------------------------------
    // 客户端方法
    // ------------------------------------------------------------------

    public String deviceId() {
        return deviceId;
    }

    public String installId() {
        return installId;
    }

    public byte[] contentKey() {
        return contentKey;
    }

    public Integer keyver() {
        return keyver;
    }

    /** 签名头（x-argus + x-ladon）。 */
    private Map<String, String> sign(long millis, String qs) {
        byte[] proto = buildProto(qs, "1967", deviceId == null ? "" : deviceId,
                installId == null ? "" : installId, millis, "6.8.1.32");
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-argus", xArgus(proto));
        h.put("x-ladon", xLadon(millis / 1000, 0, "1967", null));
        h.put("x-khronos", String.valueOf(millis / 1000));
        h.put("x-ss-req-ticket", String.valueOf(millis));
        return h;
    }

    /** 把参数按插入顺序做 URL 编码（与 urllib.parse.urlencode 的语义一致）。 */
    static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(),
                            StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** GET 请求；服务端限流时返回空 body，按 backoff 退避重试。 */
    private JsonNode get(String path, Map<String, String> params, int retries, double backoffSeconds)
            throws IOException, InterruptedException {
        String qs = formEncode(params);
        String url = host + path + "?" + qs;
        int attempt = 0;
        String last = null;
        while (true) {
            Map<String, String> headers = sign(System.currentTimeMillis(), qs);
            headers.put("User-Agent", UA);
            headers.put("Accept", "application/json");
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .GET();
            headers.forEach(rb::header);
            String body = null;
            try {
                HttpResponse<byte[]> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 400) {
                    last = "HTTP " + resp.statusCode();
                } else {
                    body = new String(resp.body(), StandardCharsets.UTF_8);
                    if (!body.isBlank()) {
                        JsonNode parsed = json.readTree(body);
                        if (parsed != null && parsed.isObject()) return parsed;
                        throw new IOException("接口返回无效数据");
                    }
                    last = "空响应体（服务端限流）";
                }
            } catch (IOException ex) {
                last = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            if (attempt >= Math.min(retries, requestRetries)) {
                throw new IOException(path + " 失败（已重试 " + attempt + " 次）: " + last);
            }
            attempt++;
            Thread.sleep((long) (backoffSeconds * 1000));
            backoffSeconds = Math.min(backoffSeconds * 1.6, 45.0);
        }
    }

    /** POST 请求（json body）。 */
    private JsonNode post(String path, Map<String, String> params, byte[] body,
                          Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        String qs = formEncode(params);
        Map<String, String> headers = sign(System.currentTimeMillis(), qs);
        headers.put("User-Agent", UA);
        headers.put("Content-Type", "application/json; charset=utf-8");
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(host + path + "?" + qs))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(rb::header);
        HttpResponse<byte[]> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new IOException("POST " + path + " HTTP " + resp.statusCode());
        }
        JsonNode parsed = json.readTree(resp.body());
        if (parsed == null || !parsed.isObject()) throw new IOException("接口返回空响应");
        return parsed;
    }

    /** 注册新设备，得到 device_id / install_id。此请求不得带 x-argus / x-ladon。 */
    public JsonNode registerDevice() throws IOException, InterruptedException {
        byte[] payload = ttEncrypt(registrationBody(), null);
        HttpRequest req = HttpRequest.newBuilder(URI.create(REG_HOST + REG_PATH))
                .timeout(timeout)
                .header("User-Agent", "com.dragon.read")
                .header("Content-Type", "application/octet-stream; tt-data=a")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) throw new IOException("设备注册 HTTP " + resp.statusCode());
        JsonNode reg = json.readTree(resp.body());
        if (reg == null) throw new IOException("设备注册返回空响应");
        String did = reg.path("device_id").asText("");
        if (did.isEmpty() || "0".equals(did)) {
            throw new IllegalStateException("设备注册失败: " + reg.toString());
        }
        this.deviceId = did;
        this.installId = reg.path("install_id").asText(did);
        this.cdid = uuid4();
        return reg;
    }

    /** 生成完整请求参数。 */
    public Map<String, String> loadParams() throws IOException, InterruptedException {
        if (deviceId == null) {
            registerDevice();
        }
        this.params = defaultParams(deviceId, installId, cdid);
        return params;
    }

    /** POST /reading/crypt/registerkey 换取 content_key。此请求不得带 x-ss-stub。 */
    public void registerKey() throws IOException, InterruptedException {
        if (params == null) {
            loadParams();
        }
        ObjectNode body = json.createObjectNode();
        body.put("content", makeRegisterContent(deviceId));
        JsonNode res = post("/reading/crypt/registerkey", params,
                json.writeValueAsBytes(body), null);
        JsonNode data = res.path("data");
        String key = data.path("key").asText("");
        if (key.isEmpty()) {
            throw new IllegalStateException("registerkey 失败: " + res);
        }
        this.contentKey = decryptRegisterKey(key);
        this.keyver = data.hasNonNull("keyver") ? data.get("keyver").asInt() : null;
    }

    public FanqieClient ensureSession() throws IOException, InterruptedException {
        if (contentKey == null) {
            registerKey();
        }
        return this;
    }

    /** 原始搜索响应（未解析）。 */
    public JsonNode searchRaw(String keyword, int offset, int count, int tabType, String passback)
            throws IOException, InterruptedException {
        if (params == null) {
            loadParams();
        }
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("query", keyword);
        p.put("offset", String.valueOf(offset));
        p.put("count", String.valueOf(count));
        p.put("tab_type", String.valueOf(tabType));
        if (passback != null) {
            p.put("passback", passback);
        }
        return get("/reading/bookapi/search/tab/v", p, 3, 8.0);
    }

    /** 从 search_tabs 里挑出书籍 tab；取不到就回退到第一个有数据的 tab。 */
    private static JsonNode pickBookTab(JsonNode res) {
        JsonNode tabs = res.path("search_tabs");
        if (tabs.isArray()) {
            for (JsonNode t : tabs) {
                if (t.path("tab_type").asInt(Integer.MIN_VALUE) == 3 && t.path("data").isArray()) {
                    return t;
                }
            }
            for (JsonNode t : tabs) {
                if (t.path("data").isArray() && t.path("data").size() > 0) {
                    return t;
                }
            }
        }
        return null;
    }

    private static long numLong(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return 0;
        }
        if (n.isNumber()) {
            return n.asLong();
        }
        try {
            return Long.parseLong(n.asText().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double numDouble(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return 0;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        try {
            return Double.parseDouble(n.asText().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Integer numIntOrNull(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || n.asText().isEmpty()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asInt();
        }
        try {
            return Integer.parseInt(n.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? "" : n.asText("");
    }

    /** 把书籍 tab 的 data(list) 解析成规整的书目列表。 */
    private static List<Book> parseSearchCells(JsonNode tab) {
        List<Book> out = new ArrayList<>();
        if (tab == null) {
            return out;
        }
        JsonNode cells = tab.path("data");
        if (!cells.isArray()) {
            return out;
        }
        for (JsonNode cell : cells) {
            if (!cell.isObject()) {
                continue;
            }
            JsonNode bd = cell.path("book_data");
            JsonNode b = null;
            if (bd.isArray() && bd.size() > 0) {
                b = bd.get(0);
            } else if (bd.isObject()) {
                b = bd;
            }
            JsonNode hl = cell.path("search_high_light");
            if (b == null || !b.isObject()) {
                ObjectNode fallback = null;
                if (hl.isObject()) {
                    String t = text(hl.path("title").path("text"));
                    String a = text(hl.path("abstract").path("text"));
                    fallback = new ObjectMapper().createObjectNode();
                    fallback.put("book_name", t);
                    fallback.put("abstract", a);
                }
                if (fallback == null) {
                    continue;
                }
                b = fallback;
            }
            // 只有 search_high_light 的单元格没有书号，无法定位书籍，直接丢弃。
            String bid = text(b.path("book_id"));
            if (bid.isEmpty()) {
                bid = text(cell.path("book_id"));
            }
            if (bid.isEmpty()) {
                continue;
            }
            out.add(toBook(b, hl, bid));
        }
        return out;
    }

    /** 解析单个书目节点；详情接口与搜索结果共用同一套字段名。 */
    static List<Book> parseBookNode(JsonNode node) {
        if (node == null || !node.isObject()) return List.of();
        String bid = text(node.path("book_id"));
        if (bid.isEmpty()) return List.of();
        return List.of(toBook(node, null, bid));
    }

    /** 按番茄书目字段组装 {@link Book}，高亮节点仅在搜索结果里提供兜底文本。 */
    static Book toBook(JsonNode b, JsonNode highlight, String bookId) {
        Integer status = numIntOrNull(b.path("creation_status"));
        String name = text(b.path("book_name"));
        if (name.isEmpty() && highlight != null && highlight.isObject()) {
            name = text(highlight.path("title").path("text"));
        }
        String abs = text(b.path("abstract"));
        if (abs.isEmpty() && highlight != null && highlight.isObject()) {
            abs = text(highlight.path("abstract").path("text"));
        }
        return new Book(bookId, name, text(b.path("author")), abs,
                numLong(b.path("word_number")), status,
                status != null && status == 1,
                numDouble(b.path("score")), numLong(b.path("read_count")),
                text(b.path("category")), text(b.path("thumb_url")),
                text(b.path("last_chapter_title")));
    }

    /** 取一页搜索结果，返回 (书目列表, 下一页 passback 令牌或 null)。 */
    private Map.Entry<List<Book>, String> searchPage(String keyword, int count, String passback)
            throws IOException, InterruptedException {
        int offset = 0;
        if (passback != null) {
            try {
                offset = Integer.parseInt(passback);
            } catch (NumberFormatException ignore) {
                offset = 0;
            }
        }
        return searchPage(keyword, offset, count, passback);
    }

    /**
     * 取一页搜索结果。
     *
     * @param offset   上游 offset 参数
     * @param passback 上游 passback 令牌，可为 null
     */
    private Map.Entry<List<Book>, String> searchPage(String keyword, int offset, int count, String passback)
            throws IOException, InterruptedException {
        JsonNode res = searchRaw(keyword, offset, count, 3, passback);
        int code = res.path("code").asInt(0);
        if (res.hasNonNull("code") && code != 0) {
            throw new IllegalStateException("搜索失败 code=" + res.get("code")
                    + " message=" + text(res.path("message")));
        }
        JsonNode tab = pickBookTab(res);
        if (tab == null) {
            JsonNode res2 = searchRaw(keyword, offset, count, 0, passback);
            tab = pickBookTab(res2);
        }
        List<Book> items = parseSearchCells(tab);
        String next = null;
        if (tab != null && tab.path("has_more").asBoolean(false)
                && tab.hasNonNull("passback")) {
            next = tab.get("passback").asText();
        }
        Map<List<Book>, String> map = new LinkedHashMap<>();
        map.put(items, next);
        return map.entrySet().iterator().next();
    }

    /** 搜索书籍（page>0 时自动用 passback 逐页走到目标页）。 */
    public List<Book> search(String keyword, int page, int count) throws IOException, InterruptedException {
        if (page > 0) {
            String token = null;
            for (int i = 0; i < page; i++) {
                token = searchPage(keyword, count, token).getValue();
                if (token == null) {
                    return List.of();
                }
            }
            return searchPage(keyword, count, token).getKey();
        }
        return searchPage(keyword, count, null).getKey();
    }

    /**
     * 取一页搜索结果。
     *
     * <p>offset 与 passback 都直接透传给上游：passback 是上游续页令牌，offset 决定结果窗口。
     * 书源层按页码换算 offset 直接请求，因此不依赖上一次响应，可任意跳页。</p>
     */
    public SearchPage searchOnePage(String keyword, int offset, int count, String passback)
            throws IOException, InterruptedException {
        Map.Entry<List<Book>, String> page = searchPage(keyword, offset, count, passback);
        return new SearchPage(List.copyOf(page.getKey()), page.getValue());
    }

    /**
     * 解析书籍详情。
     *
     * <p>详情接口返回 <code>data</code>：实测是书目对象组成的数组（每个元素字段与搜索结果一致）；
     * 旧版本为 <code>data.book_info</code> / <code>data.book_data</code> 单个对象。
     * 两种结构都在这里兼容，解析逻辑复用 {@link #parseBookNode}。</p>
     *
     * @return 解析成功返回书目，接口不可用或字段缺失时返回 null（调用方回退到搜索结果里缓存的书目）
     */
    public Book detailBook(String bookId) throws IOException, InterruptedException {
        JsonNode res = detail(bookId);
        if (res.path("code").asInt(0) != 0) {
            throw new IllegalStateException("详情获取失败：" + text(res.path("message")));
        }
        JsonNode info = detailBookNode(res.path("data"));
        if (info == null) return null;
        List<Book> parsed = parseBookNode(info);
        if (parsed.isEmpty()) return null;
        Book book = parsed.getFirst();
        return book.bookId().isEmpty() ? null : book;
    }

    /**
     * 从详情响应的 <code>data</code> 里取出书目对象。
     *
     * <p>实测 <code>data</code> 是书目对象组成的数组；旧版本是
     * <code>data.book_info</code> / <code>data.book_data</code> 单个对象。
     * 数组里只取第一个对象（按书号请求，正常只会有一本）。</p>
     *
     * @return 书目对象；结构不认识时返回 null
     */
    static JsonNode detailBookNode(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) return null;
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (item.isObject()) return item;
            }
            return null;
        }
        if (!data.isObject()) return null;
        JsonNode info = data.path("book_info");
        if (!info.isObject()) info = data.path("book_data");
        return info.isObject() ? info : data;
    }

    /** 翻多页搜索（用 passback 令牌），按 book_id 去重。 */
    public List<Book> searchMore(String keyword, int pages, int count)
            throws IOException, InterruptedException {
        Set<String> seen = new LinkedHashSet<>();
        List<Book> out = new ArrayList<>();
        String token = null;
        for (int i = 0; i < pages; i++) {
            Map.Entry<List<Book>, String> page = searchPage(keyword, count, token);
            token = page.getValue();
            for (Book b : page.getKey()) {
                if (seen.add(b.bookId())) {
                    out.add(b);
                }
            }
            if (token == null) {
                break;
            }
        }
        return out;
    }

    /** 拉取完整目录。 */
    public List<ChapterEntry> catalog(String bookId) throws IOException, InterruptedException {
        if (params == null) {
            loadParams();
        }
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("book_id", String.valueOf(bookId));
        JsonNode res = get("/reading/bookapi/directory/all_items/v", p, 3, 8.0);
        if (res.path("code").asInt(0) != 0) {
            throw new IllegalStateException("目录获取失败：" + text(res.path("message")));
        }
        JsonNode rows = res.path("data").path("item_data_list");
        List<ChapterEntry> out = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode r : rows) {
                long order = numLong(r.path("real_chapter_order"));
                if (order == 0) {
                    order = numLong(r.path("order"));
                }
                out.add(new ChapterEntry(text(r.path("item_id")), text(r.path("title")),
                        order, text(r.path("volume_name")), numLong(r.path("chapter_word_number"))));
            }
        }
        return out;
    }

    /** 书籍详情（原始 JSON）。 */
    public JsonNode detail(String bookId) throws IOException, InterruptedException {
        if (params == null) loadParams();
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("book_id", String.valueOf(bookId));
        return get("/reading/bookapi/multi-detail/v", p, 3, 8.0);
    }

    /** 单章下载并解密。 */
    public ChapterText chapter(String itemId) throws IOException, InterruptedException {
        if (fetched >= QUOTA_CHAPTERS) renewSession();
        ensureSession();
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("item_id", String.valueOf(itemId));
        p.put("keyver", String.valueOf(keyver));
        JsonNode res = get("/reading/reader/full/v", p, 3, 8.0);
        fetched++;
        JsonNode d = res.path("data");
        String ct = text(d.path("content"));
        if (ct.isEmpty() || "Invalid".equals(ct)) {
            throw new IllegalStateException("章节 " + itemId + " 无可下载内容 (crypt_status="
                    + text(d.path("crypt_status")) + ")");
        }
        List<String> paras = xhtmlToParagraphs(decryptContent(ct, contentKey));
        if (paras.isEmpty()) throw new IllegalStateException("章节正文为空，请重试");
        return new ChapterText(text(d.path("title")), paras, String.join("\n", paras));
    }

    /** 批量下载并解密。 */
    public Map<String, Fetched> chaptersBatch(List<String> itemIds)
            throws IOException, InterruptedException {
        ensureSession();
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("item_ids", String.join(",", itemIds));
        p.put("keyver", String.valueOf(keyver));
        JsonNode res = get("/reading/reader/batch_full/v", p, 3, 8.0);
        JsonNode data = res.path("data");
        Map<String, Fetched> out = new LinkedHashMap<>();
        for (String rawId : itemIds) {
            String iid = String.valueOf(rawId);
            JsonNode it = data.path(iid);
            String ct = text(it.path("content"));
            String title = text(it.path("novel_data").path("chapter_title"));
            if (title.isEmpty()) {
                title = text(it.path("title"));
            }
            if (ct.isEmpty() || "Invalid".equals(ct)) {
                out.put(iid, new Fetched(iid, title, "", 0,
                        "no content (crypt_status=" + text(it.path("crypt_status")) + ")"));
                continue;
            }
            try {
                List<String> paras = xhtmlToParagraphs(decryptContent(ct, contentKey));
                String txt = String.join("\n", paras);
                out.put(iid, new Fetched(iid, title, txt, txt.length(), null));
            } catch (RuntimeException ex) {
                out.put(iid, new Fetched(iid, title, "", 0,
                        String.valueOf(ex.getMessage()).substring(0,
                                Math.min(120, String.valueOf(ex.getMessage()).length()))));
            }
        }
        return out;
    }

    /**
     * 下载整本书，写入 TXT。返回 (章节结果列表, 总字数)。
     *
     * @deprecated 使用 {@link #downloadResult} 获取结构化的整本下载结果。
     */
    @Deprecated
    public Object[] download(String bookId, String outPath, boolean progress,
                             Integer maxChapters, int retries)
            throws IOException, InterruptedException {
        return downloadResult(bookId, outPath, progress, maxChapters, retries).split();
    }

    /** 整本下载结果：有序章节、总字数、目录大小。 */
    public record DownloadResult(List<Fetched> chapters, long totalChars, int totalChapters) {
        /** 兼容旧调用方的 (List, Long) 二元组形式。 */
        public Object[] split() {
            return new Object[]{chapters, totalChars};
        }
    }

    /** 下载整本书并返回结构化结果（不写文件时 outPath 传 null）。 */
    public DownloadResult downloadResult(String bookId, String outPath, boolean progress,
                                         Integer maxChapters, int retries)
            throws IOException, InterruptedException {
        ensureSession();
        List<ChapterEntry> cat = catalog(bookId);
        if (maxChapters != null && maxChapters > 0 && cat.size() > maxChapters) {
            cat = new ArrayList<>(cat.subList(0, maxChapters));
        }
        if (cat.isEmpty()) {
            throw new IllegalStateException("目录为空: " + bookId);
        }

        List<ChapterEntry> pending = new ArrayList<>(cat);
        Map<String, Fetched> results = new LinkedHashMap<>();
        long total = 0;
        long t0 = System.currentTimeMillis();
        int rounds = 0;

        while (!pending.isEmpty()) {
            List<ChapterEntry> failed = new ArrayList<>();
            for (int i = 0; i < pending.size(); i += APP_BATCH_SIZE) {
                List<ChapterEntry> grp = pending.subList(i, Math.min(i + APP_BATCH_SIZE, pending.size()));
                List<String> ids = new ArrayList<>();
                for (ChapterEntry c : grp) {
                    ids.add(c.itemId());
                }
                // 单设备配额用尽 -> 主动换设备续传
                if (fetched + ids.size() > QUOTA_CHAPTERS) {
                    if (progress) {
                        System.out.println("  -- 设备配额将满，切换新设备 --");
                    }
                    renewSession();
                }
                Map<String, Fetched> got;
                try {
                    got = chaptersBatch(ids);
                    fetched += ids.size();
                } catch (IOException ex) {
                    // 触发冷却：等待后换设备重试本组
                    if (progress) {
                        System.out.printf("  冷却/失败 %d 章 (%s)，等待 %ds 后换设备%n",
                                grp.size(), abbreviate(String.valueOf(ex.getMessage()), 60),
                                COOLDOWN_SECONDS);
                    }
                    Thread.sleep(COOLDOWN_SECONDS * 1000L);
                    renewSession();
                    failed.addAll(grp);
                    continue;
                }
                for (ChapterEntry c : grp) {
                    Fetched r = got.get(c.itemId());
                    if (r == null || r.error() != null || r.text().isEmpty()) {
                        failed.add(c);
                        continue;
                    }
                    results.put(c.itemId(), new Fetched(c.itemId(),
                            c.title().isEmpty() ? r.title() : c.title(),
                            r.text(), r.chars(), null));
                    total += r.chars();
                }
                if (progress && (i / APP_BATCH_SIZE) % 10 == 0) {
                    long done = results.size();
                    double el = (System.currentTimeMillis() - t0) / 1000.0;
                    double eta = done > 0 ? el / done * (cat.size() - done) : 0;
                    System.out.printf("  %d/%d  %d 字  已用 %.0fs  ETA %.0fs%n",
                            done, cat.size(), total, el, eta);
                }
                if (intervalMs > 0) {
                    Thread.sleep(intervalMs);
                }
            }
            if (failed.isEmpty()) {
                break;
            }
            rounds++;
            if (rounds > retries) {
                if (progress) {
                    System.out.printf("  仍剩 %d 章未能下载%n", failed.size());
                }
                break;
            }
            if (progress) {
                System.out.printf("  --- 第 %d 轮重试，剩余 %d 章 ---%n", rounds, failed.size());
            }
            pending = failed;
        }

        List<Fetched> ordered = new ArrayList<>();
        for (ChapterEntry c : cat) {
            Fetched r = results.get(c.itemId());
            ordered.add(r != null ? r
                    : new Fetched(c.itemId(), c.title(), "", 0, "下载失败"));
        }

        if (outPath != null) {
            // 与 FanqieLibraryService 的章节索引保持一致：每章「标题 + 空行 + 正文 + 空行」，
            // 不再写 ==== 装饰行，否则按字符偏移切章会错位。
            StringBuilder sb = new StringBuilder();
            for (Fetched r : ordered) {
                sb.append(chapterBlock(r));
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of(outPath), sb.toString(),
                    StandardCharsets.UTF_8);
        }
        return new DownloadResult(ordered, total, cat.size());
    }

    /** 单章在 TXT 中的块：标题行 + 空行 + 正文 + 空行。 */
    public static String chapterBlock(Fetched chapter) {
        String title = FanqieChapterParser.normalizeTitle(chapter.title());
        if (title.isEmpty()) title = "未命名章节";
        String text = chapter.text() == null || chapter.text().isBlank()
                ? "[下载失败: " + chapter.error() + "]" : chapter.text();
        return title + "\n\n" + text + "\n\n";
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 清除失效状态，不在失败处理阶段额外发出网络请求。 */
    public void resetSession() {
        deviceId = null;
        installId = null;
        cdid = null;
        contentKey = null;
        keyver = null;
        params = null;
        fetched = 0;
    }

    /** 重新注册设备并换取新 content_key（应对配额/冷却）。 */
    public void renewSession() throws IOException, InterruptedException {
        resetSession();
        registerDevice();
        loadParams();
        registerKey();
        this.fetched = 0;
    }

    // ==================================================================
    // CLI
    // ==================================================================

    public static void main(String[] args) throws Exception {
        String bookId = null;
        String out = null;
        Integer num = null;
        String search = null;
        boolean catalog = false;
        String one = null;
        int pages = 1;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-o", "--out" -> out = args[++i];
                case "-n", "--num" -> num = Integer.parseInt(args[++i]);
                case "--search" -> search = args[++i];
                case "--catalog" -> catalog = true;
                case "--one" -> one = args[++i];
                case "--pages" -> pages = Integer.parseInt(args[++i]);
                case "-h", "--help" -> {
                    System.out.println("用法: java FanqieClient <book_id> [-o out.txt] [-n N] "
                            + "[--catalog] [--one item_id] [--search 关键词] [--pages N]");
                    return;
                }
                default -> bookId = a;
            }
        }

        FanqieClient c = new FanqieClient();
        c.registerDevice();
        System.out.printf("设备已注册 device_id=%s install_id=%s%n", c.deviceId, c.installId);
        c.loadParams();
        c.registerKey();
        System.out.printf("content_key=%s keyver=%s%n",
                FanqieCrypto.hexLower(c.contentKey), c.keyver);

        if (search != null) {
            List<Book> hits = c.searchMore(search, pages, 10);
            System.out.printf("%n搜索结果 %d 条:%n", hits.size());
            for (Book b : hits.subList(0, Math.min(30, hits.size()))) {
                System.out.printf("  %-20s 《%s》 %s  %s  %.0f 万字  %s%n",
                        b.bookId(), b.bookName(), b.author(),
                        b.finished() ? "完结" : "连载中",
                        b.wordNumber() / 10000.0, b.category());
            }
            if (hits.isEmpty()) {
                System.out.println("  （无结果。可用 book_id 直接下载：java FanqieClient <book_id>）");
            }
            return;
        }

        if (one != null) {
            ChapterText t = c.chapter(one);
            System.out.printf("%n%s  %d 段 %d 字%n", t.title(), t.paragraphs().size(), t.text().length());
            System.out.println(t.text().substring(0, Math.min(600, t.text().length())));
            if (out != null) {
                java.nio.file.Files.writeString(java.nio.file.Path.of(out), t.text(),
                        StandardCharsets.UTF_8);
            }
            return;
        }

        if (bookId == null) {
            System.out.println("需要 book_id");
            System.exit(2);
        }
        List<ChapterEntry> cat = c.catalog(bookId);
        System.out.printf("%n目录 %d 章%n", cat.size());
        if (catalog) {
            for (ChapterEntry ch : cat.subList(0, Math.min(40, cat.size()))) {
                System.out.printf("  %-20s %s%n", ch.itemId(), ch.title());
            }
            if (cat.size() > 40) {
                System.out.printf("  ... 共 %d 章%n", cat.size());
            }
            return;
        }

        String path = out != null ? out : bookId + ".txt";
        System.out.printf("%n开始下载 -> %s%n", path);
        Object[] res = c.download(bookId, path, true, num, 4);
        @SuppressWarnings("unchecked")
        List<Fetched> list = (List<Fetched>) res[0];
        long ok = list.stream().filter(r -> r.error() == null).count();
        System.out.printf("%n完成: %d/%d 章成功, 共 %d 字 -> %s%n",
                ok, list.size(), res[1], path);
    }
}
