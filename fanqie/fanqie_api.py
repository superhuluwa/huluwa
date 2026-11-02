# -*- coding: utf-8 -*-
"""番茄小说 App 协议客户端 —— 单文件纯 Python 实现（零第三方依赖）。

本文件自带全部加解密与签名算法，不需要 libfanqie.so、unicorn 或任何外部模块。

================================================================
一、接口链路
================================================================
  1. 设备注册  POST https://i.snssdk.com/service/2/device_register/?tt_data=a
     body = tt_encrypt(JSON)，UA = "com.dragon.read"
     ★ 此请求不得携带 x-argus / x-ladon / x-ss-stub，否则被拒。

  2. 密钥交换  POST /reading/crypt/registerkey
     body = {"content": base64(IV(16) || AES128CBC(K0, IV, le64(device_id)||PKCS7))}
     resp = {"key": base64(IV(16) || AES128CBC(K0, IV, content_key||PKCS7)), "keyver": N}
     ★ 此请求不得携带 x-ss-stub，否则返回 200 空 body。

  3. 正文  GET /reading/reader/full/v?item_id=..&keyver=..
           GET /reading/reader/batch_full/v?item_ids=a,b,c&keyver=..
     content 仅在携带有效 keyver 时返回密文（否则为字面量 "Invalid"）
     解密：base64 -> IV(16)+密文 -> AES128CBC(content_key, IV) -> gzip -> XHTML -> 纯文本

================================================================
二、签名头（每个请求都要算）
================================================================
  x-ladon  时间戳签名，见 x_ladon()
  x-argus  查询串绑定签名，见 x_argus()
           结构 = base64( b"\xf2\x81" || AES-128-CBC-PKCS7(msg, KEY, IV) )
           其中 msg = C9 || mirror8(KEY8 || (hash160(proto) XOR KEY8[i%8])) || b"ao"
           proto = 见 build_proto()（protobuf 编码的查询上下文）

================================================================
三、tt_encrypt（设备注册请求体加密）
================================================================
  HDR38 || AES-128-CBC/PKCS7( sha512(gz) || gz )
  HDR38 = b"tc" + b"\x05\x10\x00\x00" + R(32B 随机)
  key/iv 来自 D = rev64(C)，C 由 sha512 压缩链从 R 推导，见 derive_D()

================================================================
四、固定常量
================================================================
  K0（registerkey 密钥）  = ac25c67ddd8f38c1b37a2348828e222e
  ARGUS_AES_KEY/IV        = d31e3718288a1027baab59f146a09a9c / ea180a0336ed352fcd24e4d50018ae54
  ARGUS_KEY8              = f2f7fcfff2f7fcff
"""
import base64
import binascii
import hashlib
import json
import os
import re
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib

M64 = (1 << 64) - 1
MASK64 = (1 << 64) - 1


# ==========================================================================
# 一、AES-128（CBC / PKCS#7）
# ==========================================================================
SBOX = [
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b,
    0xfe, 0xd7, 0xab, 0x76, 0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0,
    0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0, 0xb7, 0xfd, 0x93, 0x26,
    0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2,
    0xeb, 0x27, 0xb2, 0x75, 0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0,
    0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84, 0x53, 0xd1, 0x00, 0xed,
    0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f,
    0x50, 0x3c, 0x9f, 0xa8, 0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5,
    0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2, 0xcd, 0x0c, 0x13, 0xec,
    0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14,
    0xde, 0x5e, 0x0b, 0xdb, 0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c,
    0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79, 0xe7, 0xc8, 0x37, 0x6d,
    0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f,
    0x4b, 0xbd, 0x8b, 0x8a, 0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e,
    0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e, 0xe1, 0xf8, 0x98, 0x11,
    0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f,
    0xb0, 0x54, 0xbb, 0x16,
]
INV_SBOX = [0] * 256
for _i, _v in enumerate(SBOX):
    INV_SBOX[_v] = _i

RCON = [0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36]


def _xtime(b):
    b <<= 1
    if b & 0x100:
        b = (b ^ 0x1b) & 0xff
    return b & 0xff


def _gmul(a, b):
    r = 0
    for _ in range(8):
        if b & 1:
            r ^= a
        a = _xtime(a)
        b >>= 1
    return r


def _expand_key(key):
    if len(key) != 16:
        raise ValueError('AES-128 密钥必须 16 字节')
    w = [list(key[i * 4:i * 4 + 4]) for i in range(4)]
    for i in range(4, 44):
        t = list(w[i - 1])
        if i % 4 == 0:
            t = t[1:] + t[:1]
            t = [SBOX[x] for x in t]
            t[0] ^= RCON[i // 4 - 1]
        w.append([w[i - 4][j] ^ t[j] for j in range(4)])
    return [sum(w[4 * r:4 * r + 4], []) for r in range(11)]


def _add_round_key(s, k):
    return [s[i] ^ k[i] for i in range(16)]


def _sub_bytes(s, box):
    return [box[x] for x in s]


def _shift_rows(s):
    return [s[(i + 4 * (i % 4)) % 16] for i in range(16)]


def _inv_shift_rows(s):
    return [s[(i - 4 * (i % 4)) % 16] for i in range(16)]


def _mix_columns(s):
    o = [0] * 16
    for c in range(4):
        a = s[c * 4:c * 4 + 4]
        o[c * 4 + 0] = _gmul(a[0], 2) ^ _gmul(a[1], 3) ^ a[2] ^ a[3]
        o[c * 4 + 1] = a[0] ^ _gmul(a[1], 2) ^ _gmul(a[2], 3) ^ a[3]
        o[c * 4 + 2] = a[0] ^ a[1] ^ _gmul(a[2], 2) ^ _gmul(a[3], 3)
        o[c * 4 + 3] = _gmul(a[0], 3) ^ a[1] ^ a[2] ^ _gmul(a[3], 2)
    return o


def _inv_mix_columns(s):
    o = [0] * 16
    for c in range(4):
        a = s[c * 4:c * 4 + 4]
        o[c * 4 + 0] = (_gmul(a[0], 14) ^ _gmul(a[1], 11)
                        ^ _gmul(a[2], 13) ^ _gmul(a[3], 9))
        o[c * 4 + 1] = (_gmul(a[0], 9) ^ _gmul(a[1], 14)
                        ^ _gmul(a[2], 11) ^ _gmul(a[3], 13))
        o[c * 4 + 2] = (_gmul(a[0], 13) ^ _gmul(a[1], 9)
                        ^ _gmul(a[2], 14) ^ _gmul(a[3], 11))
        o[c * 4 + 3] = (_gmul(a[0], 11) ^ _gmul(a[1], 13)
                        ^ _gmul(a[2], 9) ^ _gmul(a[3], 14))
    return o


def aes128_encrypt_block(rk, block):
    s = _add_round_key(list(block), rk[0])
    for r in range(1, 10):
        s = _sub_bytes(s, SBOX)
        s = _shift_rows(s)
        s = _mix_columns(s)
        s = _add_round_key(s, rk[r])
    s = _sub_bytes(s, SBOX)
    s = _shift_rows(s)
    s = _add_round_key(s, rk[10])
    return bytes(s)


def aes128_decrypt_block(rk, block):
    s = _add_round_key(list(block), rk[10])
    for r in range(9, 0, -1):
        s = _inv_shift_rows(s)
        s = _sub_bytes(s, INV_SBOX)
        s = _add_round_key(s, rk[r])
        s = _inv_mix_columns(s)
    s = _inv_shift_rows(s)
    s = _sub_bytes(s, INV_SBOX)
    s = _add_round_key(s, rk[0])
    return bytes(s)


def aes128_cbc_encrypt(key, iv, data):
    rk = _expand_key(bytes(key))
    out = b''
    prev = bytes(iv)
    for i in range(0, len(data), 16):
        blk = bytes(a ^ b for a, b in zip(data[i:i + 16], prev))
        prev = aes128_encrypt_block(rk, blk)
        out += prev
    return out


def aes128_cbc_decrypt(key, iv, data):
    rk = _expand_key(bytes(key))
    out = b''
    prev = bytes(iv)
    for i in range(0, len(data), 16):
        ct = data[i:i + 16]
        pt = aes128_decrypt_block(rk, ct)
        out += bytes(a ^ b for a, b in zip(pt, prev))
        prev = ct
    return out


def pkcs7_pad(data, bs=16):
    n = bs - (len(data) % bs)
    return data + bytes([n]) * n


def pkcs7_unpad(data, bs=16):
    if not data or len(data) % bs:
        raise ValueError('PKCS7: 长度不是块大小的整数倍')
    n = data[-1]
    if n < 1 or n > bs or data[-n:] != bytes([n]) * n:
        raise ValueError('PKCS7: 填充无效')
    return data[:-n]


# ==========================================================================
# 二、SHA-512 压缩函数 / rev64（tt_encrypt 需要中间态）
# ==========================================================================
_K512 = [
    0x428a2f98d728ae22, 0x7137449123ef65cd, 0xb5c0fbcfec4d3b2f, 0xe9b5dba58189dbbc,
    0x3956c25bf348b538, 0x59f111f1b605d019, 0x923f82a4af194f9b, 0xab1c5ed5da6d8118,
    0xd807aa98a3030242, 0x12835b0145706fbe, 0x243185be4ee4b28c, 0x550c7dc3d5ffb4e2,
    0x72be5d74f27b896f, 0x80deb1fe3b1696b1, 0x9bdc06a725c71235, 0xc19bf174cf692694,
    0xe49b69c19ef14ad2, 0xefbe4786384f25e3, 0x0fc19dc68b8cd5b5, 0x240ca1cc77ac9c65,
    0x2de92c6f592b0275, 0x4a7484aa6ea6e483, 0x5cb0a9dcbd41fbd4, 0x76f988da831153b5,
    0x983e5152ee66dfab, 0xa831c66d2db43210, 0xb00327c898fb213f, 0xbf597fc7beef0ee4,
    0xc6e00bf33da88fc2, 0xd5a79147930aa725, 0x06ca6351e003826f, 0x142929670a0e6e70,
    0x27b70a8546d22ffc, 0x2e1b21385c26c926, 0x4d2c6dfc5ac42aed, 0x53380d139d95b3df,
    0x650a73548baf63de, 0x766a0abb3c77b2a8, 0x81c2c92e47edaee6, 0x92722c851482353b,
    0xa2bfe8a14cf10364, 0xa81a664bbc423001, 0xc24b8b70d0f89791, 0xc76c51a30654be30,
    0xd192e819d6ef5218, 0xd69906245565a910, 0xf40e35855771202a, 0x106aa07032bbd1b8,
    0x19a4c116b8d2d0c8, 0x1e376c085141ab53, 0x2748774cdf8eeb99, 0x34b0bcb5e19b48a8,
    0x391c0cb3c5c95a63, 0x4ed8aa4ae3418acb, 0x5b9cca4f7763e373, 0x682e6ff3d6b2b8a3,
    0x748f82ee5defb2fc, 0x78a5636f43172f60, 0x84c87814a1f0ab72, 0x8cc702081a6439ec,
    0x90befffa23631e28, 0xa4506cebde82bde9, 0xbef9a3f7b2c67915, 0xc67178f2e372532b,
    0xca273eceea26619c, 0xd186b8c721c0c207, 0xeada7dd6cde0eb1e, 0xf57d4f7fee6ed178,
    0x06f067aa72176fba, 0x0a637dc5a2c898a6, 0x113f9804bef90dae, 0x1b710b35131c471b,
    0x28db77f523047d84, 0x32caab7b40c72493, 0x3c9ebe0a15c9bebc, 0x431d67c49c100d4c,
    0x4cc5d4becb3e42b6, 0x597f299cfc657e2a, 0x5fcb6fab3ad6faec, 0x6c44198c4a475817,
]

SHA512_IV = struct.pack(
    '<8Q',
    0x6a09e667f3bcc908, 0xbb67ae8584caa73b,
    0x3c6ef372fe94f82b, 0xa54ff53a5f1d36f1,
    0x510e527fade682d1, 0x9b05688c2b3e6c1f,
    0x1f83d9abfb41bd6b, 0x5be0cd19137e2179)


def _ror(x, n):
    n &= 63
    x &= M64
    return x if n == 0 else ((x >> n) | (x << (64 - n))) & M64


def compress512(state, block):
    """单块 SHA-512 压缩。

    state : 64 字节（8 个小端 u64，SHA-512 IV 顺序）
    block : 128 字节原始消息块（内部按大端解析）
    返回  : 64 字节新状态（小端 u64）
    """
    if len(state) != 64 or len(block) != 128:
        raise ValueError('compress512: state 需 64B、block 需 128B')
    v = list(struct.unpack('<8Q', state))
    w = list(struct.unpack('>16Q', block))
    for i in range(16, 80):
        s0 = _ror(w[i - 15], 1) ^ _ror(w[i - 15], 8) ^ (w[i - 15] >> 7)
        s1 = _ror(w[i - 2], 19) ^ _ror(w[i - 2], 61) ^ (w[i - 2] >> 6)
        w.append((w[i - 16] + s0 + w[i - 7] + s1) & M64)
    a, b, c, d, e, f, g, h = v
    for i in range(80):
        S1 = _ror(e, 14) ^ _ror(e, 18) ^ _ror(e, 41)
        ch = (e & f) ^ ((~e & M64) & g)
        t1 = (h + S1 + ch + _K512[i] + w[i]) & M64
        S0 = _ror(a, 28) ^ _ror(a, 34) ^ _ror(a, 39)
        mj = (a & b) ^ (a & c) ^ (b & c)
        t2 = (S0 + mj) & M64
        h, g, f, e = g, f, e, (d + t1) & M64
        d, c, b, a = c, b, a, (t1 + t2) & M64
    return struct.pack('<8Q', *[(x + y) & M64
                                for x, y in zip(v, [a, b, c, d, e, f, g, h])])


def rev64(data):
    """arm64 `rev64 v.16b` 等价：每 8 字节一组内部字节反转。"""
    out = bytearray()
    n = len(data) - len(data) % 8
    for i in range(0, n, 8):
        out += data[i:i + 8][::-1]
    return bytes(out)


# ==========================================================================
# 三、tt_encrypt（设备注册请求体加密）
# ==========================================================================
# .so RVA 0x1B873D 处的 64 字节常量
TT_CONST = bytes.fromhex(
    '4dd4c2e6b83162090e52b3c7a6733ba41cb2462b829ab58a196b39db5717752'
    '4f49baf7f08e8d68d26a72e37c1a95a2f1f05a51892aef2949732b62a38aadd58')

# 固定头：b"tc" + 0x05 0x10 0x00 0x00，其后紧跟 32 字节随机数 R
TT_HDR_PREFIX = b'tc\x05\x10\x00\x00'


def derive_D(R):
    """由 32 字节随机数 R 推导 64 字节密钥材料 D。"""
    if len(R) != 32:
        raise ValueError('derive_D: R 必须 32 字节')
    blk0 = R + b'\x80' + bytes(128 - 32 - 1 - 16) + (256).to_bytes(16, 'big')
    A = compress512(SHA512_IV, blk0)
    B = compress512(SHA512_IV, rev64(A) + TT_CONST)
    blk2 = b'\x80' + bytes(128 - 1 - 16) + (1024).to_bytes(16, 'big')
    C = compress512(B, blk2)
    return rev64(C)


def _gzip_rs(data, level=6):
    """生成与 zlib-rs（Rust flate2）默认参数一致的 gzip 流。

    与 CPython zlib 只差 gzip 头 2 字节：XFL=0x00、OS=0xff。
    """
    co = zlib.compressobj(level, zlib.DEFLATED, 31)
    gz = bytearray(co.compress(data) + co.flush())
    if len(gz) >= 10:
        gz[8] = 0x00
        gz[9] = 0xff
    return bytes(gz)


def tt_encrypt(data, R=None):
    """加密设备指纹/请求体，返回 38 字节头 + AES-128-CBC/PKCS7 密文。"""
    if isinstance(data, str):
        data = data.encode()
    if R is None:
        R = os.urandom(32)
    if len(R) != 32:
        raise ValueError('tt_encrypt: R 必须 32 字节')
    D = derive_D(R)
    gz = _gzip_rs(data)
    pt = hashlib.sha512(gz).digest() + gz
    ct = aes128_cbc_encrypt(D[0:16], D[16:32], pkcs7_pad(pt, 16))
    return TT_HDR_PREFIX + R + ct


def tt_decrypt_with_R(blob, R):
    """已知该次调用的 R 时，把 tt_encrypt 输出解密回原文。"""
    D = derive_D(R)
    pt = pkcs7_unpad(aes128_cbc_decrypt(D[:16], D[16:32], blob[38:]))
    inner_sha, gz = pt[:64], pt[64:]
    if hashlib.sha512(gz).digest() != inner_sha:
        raise ValueError('tt_encrypt 内层 sha512 校验失败')
    return zlib.decompress(gz, 31)


# ==========================================================================
# 四、SM3（国密；x-argus 的 proto 需要）
# ==========================================================================
_SM3_IV = [0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
           0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e]


def _sm3_rotl(x, n):
    n &= 31
    return ((x << n) | (x >> (32 - n))) & 0xFFFFFFFF


def _sm3_p0(x):
    return x ^ _sm3_rotl(x, 9) ^ _sm3_rotl(x, 17)


def _sm3_p1(x):
    return x ^ _sm3_rotl(x, 15) ^ _sm3_rotl(x, 23)


def _sm3_cf(v, block):
    w = list(struct.unpack('>16I', block)) + [0] * 52
    for j in range(16, 68):
        w[j] = (_sm3_p1(w[j - 16] ^ w[j - 9] ^ _sm3_rotl(w[j - 3], 15))
                ^ _sm3_rotl(w[j - 13], 7) ^ w[j - 6]) & 0xFFFFFFFF
    w1 = [(w[j] ^ w[j + 4]) & 0xFFFFFFFF for j in range(64)]
    a, b, c, d, e, f, g, h = v
    for j in range(64):
        if j < 16:
            t = 0x79CC4519
            ff = a ^ b ^ c
            gg = e ^ f ^ g
        else:
            t = 0x7A879D8A
            ff = (a & b) | (a & c) | (b & c)
            gg = (e & f) | ((~e & 0xFFFFFFFF) & g)
        a12 = _sm3_rotl(a, 12)
        ss1 = _sm3_rotl((a12 + e + _sm3_rotl(t, j % 32)) & 0xFFFFFFFF, 7)
        ss2 = ss1 ^ a12
        tt1 = (ff + d + ss2 + w1[j]) & 0xFFFFFFFF
        tt2 = (gg + h + ss1 + w[j]) & 0xFFFFFFFF
        d = c
        c = _sm3_rotl(b, 9)
        b = a
        a = tt1
        h = g
        g = _sm3_rotl(f, 19)
        f = e
        e = _sm3_p0(tt2)
    return [(x ^ y) & 0xFFFFFFFF for x, y in zip(v, [a, b, c, d, e, f, g, h])]


def sm3(data):
    """标准国密 SM3，返回 32 字节摘要。"""
    if isinstance(data, str):
        data = data.encode()
    n = len(data)
    pad = b'\x80' + b'\x00' * ((55 - n) % 64) + struct.pack('>Q', n * 8)
    v = list(_SM3_IV)
    buf = data + pad
    for i in range(0, len(buf), 64):
        v = _sm3_cf(v, buf[i:i + 64])
    return struct.pack('>8I', *v)


# ==========================================================================
# 五、x-ladon（时间戳签名，48 字符 base64）
# ==========================================================================
_LADON_MAGIC = '-1611921764-'
_LADON_B = {0: '3019', 1: '1967'}   # 随 flag 变化
_LADON_A_DEFAULT = '0'              # 真机上是堆指针；服务端不校验
_LADON_PAD = 0x06


def _le64(b, off):
    return int.from_bytes(b[off:off + 8], 'little')


def _ladon_seed(ts, aid='1967'):
    """md5(le32(ts) || aid) 的 32 个 ASCII hex 字符 -> 4 个 LE-u64。"""
    h = hashlib.md5(struct.pack('<I', ts) + aid.encode()).hexdigest().encode()
    return [int.from_bytes(h[i:i + 8], 'little') for i in (0, 8, 16, 24)]


def _ladon_mix(seed, rounds=34):
    """34 轮滚动混合 -> 35 个 u64 缓冲区。"""
    q = list(seed)
    acc = q[0]
    buf = [acc]
    for i in range(rounds):
        t = ((_ror(q[i + 1], 8) + acc) & M64) ^ i
        acc = t ^ _ror(acc, 61)
        buf.append(acc)
        q.append(t)
    return buf


def _ladon_fold(seq, x22, x25):
    """34 字折叠 -> 两个 u64。"""
    x8 = ((_ror(x25, 8) + x22) & M64) ^ seq[0]
    x9 = x8 ^ _ror(x22, 61)
    for wv in seq[1:-1]:
        x8 = ((_ror(x8, 8) + x9) & M64) ^ wv
        x9 = x8 ^ _ror(x9, 61)
    x19 = ((_ror(x8, 8) + x9) & M64) ^ seq[-1]
    return x19 ^ _ror(x9, 61), x19


def x_ladon(ts=None, flag=0, aid='1967', A=None):
    """生成 x-ladon。

    ts   : unix 秒；默认当前时间
    flag : 0 用于请求头，1 是内部另一路
    A    : 种子串第一个数字；真机上是堆指针，服务端不校验
    """
    if ts is None:
        ts = int(time.time())
    buf = _ladon_mix(_ladon_seed(ts, aid))
    seedstr = ('%s%s%s' % (A if A is not None else _LADON_A_DEFAULT,
                           _LADON_MAGIC, _LADON_B[flag])).encode()
    seedstr += bytes([_LADON_PAD]) * (32 - len(seedstr))
    a, b = _ladon_fold(buf[0:34], _le64(seedstr, 0), _le64(seedstr, 8))
    c, d = _ladon_fold(buf[0:34], _le64(seedstr, 16), _le64(seedstr, 24))
    raw = (struct.pack('<I', ts) + struct.pack('<QQ', a, b)
           + struct.pack('<QQ', c, d))
    return base64.b64encode(raw).decode()


# ==========================================================================
# 六、x-argus（查询串绑定签名）
# ==========================================================================
#   proto --hash160--> H
#   S    = H XOR ARGUS_KEY8[i % 8]
#   src  = ARGUS_KEY8 || S
#   msg  = ARGUS_C9 || mirror8(src) || b"\x61\x6f"
#   x-argus = base64( ARGUS_MAGIC || AES-128-CBC-PKCS7(msg, KEY, IV) )
ARGUS_KEY8 = bytes.fromhex('f2f7fcfff2f7fcff')
ARGUS_C9 = bytes.fromhex('a66ead9f7701d00c18')
ARGUS_MAGIC = b'\xf2\x81'
ARGUS_AES_KEY = bytes.fromhex('d31e3718288a1027baab59f146a09a9c')
ARGUS_AES_IV = bytes.fromhex('ea180a0336ed352fcd24e4d50018ae54')
ARGUS_CONST9 = 0x04531c328ae84313
ARGUS_X27 = 0x3dc94c3a046d678b
ARGUS_BUF_INIT = [0x740c7a65a9e078fc, 0x03cf3c905915e58c,
                  0xd732f2cfd3510e51, ARGUS_CONST9]


def _argus_build_w():
    """消息扩展 68 轮，产出 72 个 u64 工作区（纯常量，模块加载时算一次）。

    注意是 68 轮不是 62 轮：j+4 < 0x42(66) 只让 j<62 走一个分支，
    循环实际执行到 x8 = 0x40，共 68 次。少算会让 w[66..71] 全为 0。
    """
    w = ARGUS_BUF_INIT[:] + [0] * 68
    x9 = ARGUS_CONST9
    for j in range(68):
        shift = j if (j + 4 < 0x42) else (j - 0x3e)
        x13 = (ARGUS_X27 >> (shift & 63)) & M64
        x9 = (w[j + 1] ^ _ror(x9, 3)) & M64
        t = ((x13 & 1) | 0xfffffffffffffffc) & M64
        r = (w[j] ^ _ror(x9, 1)) & M64
        x9 = (t ^ x9) & M64
        x9 = (r ^ x9) & M64
        w[(j + 4) % 72] = x9
    return w


_ARGUS_W = _argus_build_w()


def _argus_hash160(proto):
    """按 16 字节分块压缩 72 轮；每块输出 (x26, x23) 共 16 字节。"""
    out = bytearray()
    for i in range(0, len(proto), 16):
        lo, hi = struct.unpack('<2Q', proto[i:i + 16])
        x23, x26 = hi, lo
        for k in range(72):
            x9 = (_ror(x23, 63) & _ror(x23, 56)) & M64
            x11 = (x26 ^ _ror(x23, 62)) & M64
            x23, x26 = ((x11 ^ _ARGUS_W[k]) ^ x9) & M64, x23
        out += x26.to_bytes(8, 'little') + x23.to_bytes(8, 'little')
    return bytes(out)


def _mirror8(b):
    """每 8 字节单元内反转 + 单元顺序颠倒。"""
    u = [b[i:i + 8] for i in range(0, len(b) - len(b) % 8, 8)]
    return b''.join(x[::-1] for x in u[::-1])


def build_proto(query, aid='1967', device_id='', iid='', millis=None,
                version='6.8.1.32'):
    """构造 x-argus 的 protobuf 输入（长度按 16 字节对齐）。

    query 为 URL-encoded 查询串（不含前导 '?'）。字段语义：
      f1 0x40401252 / f2 2 / f3 0x7a55300b          常量
      f4 aid                                         常量
      f5 device_id                                   动态
      f6 "1611921764" / f7 version / f8 版本串       常量
      f9 134744640 / f10 8x00 / f11 0                常量
      f12 secs*2                                     动态
      f13 106e34a2b8c7                               常量
      f14 sm3(query)[:6]                             动态
      f15 08011001180138ccd9cbbc0c                   常量（整体）
      f16 "" / f20 "none" / f21 738 / f23 17B         常量
      尾 c8 01 02 + 16 字节对齐填充
    """
    if millis is None:
        millis = int(time.time() * 1000)
    qbytes = query.encode() if isinstance(query, str) else query
    sm3q = sm3(qbytes)
    secs = millis // 1000

    def varint(v):
        out = bytearray()
        while True:
            x = v & 0x7f
            v >>= 7
            out.append(x | (0x80 if v else 0))
            if not v:
                return bytes(out)

    def fb(n, v):
        if isinstance(v, str):
            v = v.encode()
        return varint((n << 3) | 2) + varint(len(v)) + v

    def fv(n, v):
        return varint(n << 3) + varint(v)

    p = bytearray()
    p += fv(1, 0x40401252)
    p += fv(2, 2)
    p += fv(3, 0x7a55300b)
    p += fb(4, aid)
    p += fb(5, str(device_id))
    p += fb(6, '1611921764')
    p += fb(7, version)
    p += fb(8, 'v04.04.05-ov-android')
    p += fv(9, 134744640)
    p += fb(10, bytes(8))
    p += fv(11, 0)
    p += fv(12, secs * 2)
    p += fb(13, bytes.fromhex('106e34a2b8c7'))
    p += fb(14, sm3q[:6])
    p += fb(15, bytes.fromhex('08011001180138ccd9cbbc0c'))
    p += fb(16, b'')
    p += fb(20, 'none')
    p += fv(21, 738)
    p += fb(23, bytes.fromhex('0a064e583535314a1084402080b0838708'))
    p += bytes.fromhex('c80102')
    rem = len(p) % 16
    if rem:
        n = 16 - rem
        p += bytes([n]) * n
    else:
        p += b'\x01'
    return bytes(p)


def x_argus(proto):
    """由 proto 生成 x-argus 签名串（proto 长度须为 16 的倍数）。"""
    if len(proto) % 16:
        raise ValueError('proto 长度须为 16 的倍数，实际 %d' % len(proto))
    H = _argus_hash160(proto)
    S = bytes(H[i] ^ ARGUS_KEY8[i % 8] for i in range(len(H)))
    msg = ARGUS_C9 + _mirror8(ARGUS_KEY8 + S) + b'\x61\x6f'
    ct = aes128_cbc_encrypt(ARGUS_AES_KEY, ARGUS_AES_IV, pkcs7_pad(msg, 16))
    return base64.b64encode(ARGUS_MAGIC + ct).decode()


# ==========================================================================
# 七、请求参数与设备注册体
# ==========================================================================
UA_TEMPLATE = ('com.dragon.read/70132 (Linux; U; Android 11; zh_CN; '
               'SM-G9750; Build/RP1A.200720.011)')

# 30 项请求参数（与 .so default_params @0x698D40 等价）
# device_id / iid / cdid 在运行时注入
DEFAULT_PARAMS_TEMPLATE = {
    'ac': 'wifi',
    'aid': '1967',
    'app_name': 'novelapp',
    'channel': '43536163a',
    'device_platform': 'android',
    'os': 'android',
    'ssmix': 'a',
    'device_type': 'SM-G9750',
    'device_brand': 'Xiaomi',
    'language': 'zh',
    'os_api': '32',
    'os_version': '11',
    'version_code': '70132',
    'version_name': '7.0.1.32',
    'manifest_version_code': '70132',
    'resolution': '720*1280',
    'dpi': '240',
    'update_version_code': '70132',
    '_rticket': '0',
    'host_abi': 'arm64-v8a',
    'dragon_device_type': '0',
    'pv_player': '70132',
    'compliance_status': '0',
    'need_personal_recommend': '1',
    'player_so_load': '1',
    'is_android_pad_screen': '0',
    'rom_version': 'coloros__B05A.81282.76653161 release-keys',
}


def _uuid4(rng=None):
    rng = rng or os.urandom
    b = rng(16)
    return '%08x-%04x-%04x-%04x-%012x' % (
        int.from_bytes(b[0:4], 'big'),
        int.from_bytes(b[4:6], 'big'),
        (int.from_bytes(b[6:8], 'big') & 0x0fff) | 0x4000,
        (int.from_bytes(b[8:10], 'big') & 0x3fff) | 0x8000,
        int.from_bytes(b[10:16], 'big'))


def default_params(device_id, install_id, cdid=None):
    """构造一套完整请求参数（30 项）。"""
    p = dict(DEFAULT_PARAMS_TEMPLATE)
    p['device_id'] = str(device_id)
    p['iid'] = str(install_id)
    p['cdid'] = cdid or _uuid4()
    return p


def registration_body(device_id=None, install_id=None, rng=None):
    """构造 device_register 的 JSON 请求体（随机字段每次重新生成）。"""
    if rng is None:
        rng = os.urandom
    now_ms = int(time.time() * 1000)
    uuid = '%08x-%04x-%04x-%04x-%012x' % (
        int.from_bytes(rng(4), 'big'),
        int.from_bytes(rng(2), 'big'),
        (int.from_bytes(rng(2), 'big') & 0x0fff) | 0x4000,
        (int.from_bytes(rng(2), 'big') & 0x3fff) | 0x8000,
        int.from_bytes(rng(6), 'big'))
    openudid = rng(10).hex()
    body = {
        '_gen_time': now_ms,
        'magic_tag': 'ss_app_log',
        'header': {
            'access': 'wifi',
            'aid': 1967,
            'apk_first_install_time': now_ms - 86400000,
            'carrier': 'CHINA MOBILE',
            'cdid': uuid,
            'channel': '43536163a',
            'clientudid': uuid,
            'cpu_abi': 'arm64-v8a',
            'custom': {'dragon_device_type': 0, 'host_bit': 32},
            'density_dpi': 240,
            'device_brand': 'Xiaomi',
            'device_manufacturer': 'Xiaomi',
            'device_model': 'SM-G9750',
            'device_platform': 'android',
            'display_density': 'hdpi',
            'display_name': '番茄免费小说',
            'git_hash': '711d1a7',
            'guest_mode': 0,
            'ipv6_list': [{'type': 'client_anpi', 'value': '::1'}],
            'is_system_app': 0,
            'language': 'zh',
            'mcc_mnc': '46000',
            'not_request_sender': 0,
            'oaid_may_support': False,
            'openudid': openudid,
            'os': 'Android',
            'os_api': 32,
            'os_version': '11',
            'package': 'com.dragon.read',
            'pre_installed_channel': '',
            'region': 'CN',
            'release_build': 'b05af49',
            'req_id': uuid,
            'resolution': '720x1280',
            'rom': 'B05AF49E38D27C',
            'rom_version': 'coloros__B05A.81282.76653161 release-keys',
            'sdk_flavor': 'china',
            'sdk_target_version': 29,
            'sdk_version': '3.7.0-rc.25-fanqie-xiaoshuo',
            'sig_hash': 'a4a27c2633195374c15651ffc3c4a497',
            'sim_region': 'cn',
            'sim_serial_number': [],
            'timezone': 8,
            'tz_name': 'Asia/Shanghai',
            'tz_offset': 28800,
        },
    }
    return json.dumps(body, separators=(',', ':'),
                      ensure_ascii=False).encode()


# ==========================================================================
# 八、业务层：网络请求与正文处理
# ==========================================================================


# 固定密钥（.so 0x17d200），用于 registerkey 的 content 加解密
K0 = bytes.fromhex('ac25c67ddd8f38c1b37a2348828e222e')

API_HOST = 'https://api5-normal.fqnovel.com'
REG_HOST = 'https://i.snssdk.com'
REG_PATH = '/service/2/device_register/?tt_data=a'


# --------------------------------------------------------------------------
# 工具
# --------------------------------------------------------------------------
def _pkcs7_pad(data, bs=16):
    n = bs - len(data) % bs
    return data + bytes([n]) * n


def _pkcs7_unpad(data, bs=16):
    if not data or len(data) % bs:
        raise ValueError('length not multiple of block size')
    n = data[-1]
    if n < 1 or n > bs or data[-n:] != bytes([n]) * n:
        raise ValueError('bad pkcs7 padding')
    return data[:-n]


def make_register_content(device_id):
    """构造 registerkey 的 content 字段。"""
    pt = _pkcs7_pad(struct.pack('<Q', int(device_id) & MASK64))
    iv = os.urandom(16)
    ct = aes128_cbc_encrypt(K0, iv, pt)
    return base64.b64encode(iv + ct).decode()


def decrypt_register_key(key_b64):
    """解密 registerkey 响应里的 key，得到 16 字节 content_key。"""
    raw = base64.b64decode(key_b64)
    if len(raw) < 32:
        raise ValueError('registerkey key too short: %d' % len(raw))
    iv, ct = raw[:16], raw[16:]
    return _pkcs7_unpad(aes128_cbc_decrypt(K0, iv, ct))


def decrypt_content(content_b64, content_key):
    """解密正文密文，返回 XHTML 字符串。"""
    raw = base64.b64decode(content_b64)
    if len(raw) < 32 or len(raw) % 16:
        raise ValueError('bad content ciphertext length %d' % len(raw))
    iv, ct = raw[:16], raw[16:]
    pt = aes128_cbc_decrypt(content_key, iv, ct)
    # gzip：末尾可能带少量填充字节，用 decompressobj 容错
    do = zlib.decompressobj(32 + 15)     # 自动识别 gzip/zlib
    out = do.decompress(pt)
    out += do.flush()
    return out.decode('utf-8', 'replace')


def xhtml_to_paragraphs(xhtml):
    """从章节 XHTML 中提取纯文本段落。"""
    i = xhtml.find('<body')
    body = xhtml[i:] if i > 0 else xhtml
    ps = re.findall(r'<p[^>]*>(.*?)</p>', body, re.S)
    if not ps:
        ps = re.findall(r'<div[^>]*class="[^"]*content[^"]*"[^>]*>(.*?)</div>',
                        body, re.S)
    out = []
    for p in ps:
        s = re.sub(r'<br\s*/?>', '\n', p)
        s = re.sub(r'<[^>]+>', '', s)
        s = (s.replace('&nbsp;', ' ').replace('&lt;', '<')
             .replace('&gt;', '>').replace('&amp;', '&')
             .replace('&quot;', '"').replace('&#39;', "'")
             .replace('&apos;', "'"))
        s = s.strip()
        if s:
            out.append(s)
    return out


# --------------------------------------------------------------------------
# App 下载节奏（逆向自 libfanqie.so）
# --------------------------------------------------------------------------
# fetch_group_with_retry 里 mov w8,#8            -> 每组 8 章 (chapter_batch_size)
#                        mov w10,#0x05f5e100     -> 100 ms 请求间隔
#                        FanqieClient::new: w0=1 -> Semaphore 许可 1（串行，不并发）
# 配额：实测每台设备累计约 800 章后服务端返回空 body（冷却），换设备可继续。
APP_BATCH_SIZE = 8
APP_INTERVAL = 0.10        # 秒
APP_CONCURRENCY = 1        # 串行
QUOTA_CHAPTERS = 800       # 单设备软配额
COOLDOWN_SLEEP = 35        # 触发冷却后的等待时间（秒）


# --------------------------------------------------------------------------
# 客户端
# --------------------------------------------------------------------------
class FanqieClient:
    """番茄小说 App 接口客户端（纯 Python，无需 libfanqie.so / unicorn）。"""

    def __init__(self, device_id=None, install_id=None, host=API_HOST,
                 timeout=45, interval=APP_INTERVAL):
        self.host = host.rstrip('/')
        self.timeout = timeout
        self.interval = interval
        self.device_id = str(device_id) if device_id else None
        self.install_id = str(install_id) if install_id else None
        self.content_key = None
        self.keyver = None
        self.params = None
        self._fetched = 0          # 本设备已取章节数（配额跟踪）

    # ---- 底层 HTTP ------------------------------------------------------
    def _sign(self, qs):
        """纯 Python 生成签名头（x-argus + x-ladon）。"""
        m = int(time.time() * 1000)
        proto = build_proto(qs, device_id=self.device_id or '',
                                 iid=self.install_id or '', millis=m)
        argus = x_argus(proto)
        ladon = x_ladon(m // 1000, 0)
        return m, {'x-argus': argus, 'x-ladon': ladon,
                   'x-khronos': str(m // 1000),
                   'x-ss-req-ticket': str(m)}

    def _get(self, path, params, retries=3, backoff=8.0):
        """GET 请求。服务端限流时会返回**空 body**（不是错误码），按 backoff 退避重试。"""
        qs = urllib.parse.urlencode(params)
        url = self.host + path + '?' + qs
        attempt = 0
        while True:
            m, hdr = self._sign(qs)
            hdr['User-Agent'] = UA_TEMPLATE
            hdr['Accept'] = 'application/json'
            req = urllib.request.Request(url, headers=hdr)
            last = None
            try:
                with urllib.request.urlopen(req, timeout=self.timeout) as r:
                    raw = r.read()
                if raw:
                    return json.loads(raw)
                last = '空响应体（服务端限流）'
            except urllib.error.HTTPError as ex:
                last = 'HTTP %s' % ex.code
            except (urllib.error.URLError, OSError) as ex:
                last = '%s: %s' % (type(ex).__name__, str(ex)[:80])
            except json.JSONDecodeError as ex:
                last = 'JSON 解析失败: %s' % str(ex)[:80]
            if attempt >= retries:
                raise RuntimeError('%s 失败（已重试 %d 次）: %s'
                                   % (path, attempt, last))
            attempt += 1
            time.sleep(backoff)
            backoff = min(backoff * 1.6, 45.0)

    def _post(self, path, params, body, extra_headers=None):
        qs = urllib.parse.urlencode(params)
        m, hdr = self._sign(qs)
        hdr['User-Agent'] = UA_TEMPLATE
        hdr['Content-Type'] = 'application/json; charset=utf-8'
        if extra_headers:
            hdr.update(extra_headers)
        url = self.host + path + '?' + qs
        req = urllib.request.Request(url, headers=hdr, data=body, method='POST')
        with urllib.request.urlopen(req, timeout=self.timeout) as r:
            return json.loads(r.read())

    # ---- 1. 设备注册 ----------------------------------------------------
    def register_device(self):
        """注册新设备，得到 device_id / install_id。

        纯 Python 实现：请求体与 tt_encrypt 均不再依赖 libfanqie.so。
        关键：此请求不能带 x-argus / x-ladon，否则被拒。
        """
        body = registration_body()
        payload = tt_encrypt(body)
        req = urllib.request.Request(
            REG_HOST + REG_PATH, data=payload, method='POST',
            headers={'User-Agent': 'com.dragon.read',
                     'Content-Type': 'application/octet-stream; tt-data=a'})
        with urllib.request.urlopen(req, timeout=self.timeout) as r:
            reg = json.loads(r.read())
        did = str(reg.get('device_id') or '')
        if not did or did == '0':
            raise RuntimeError('设备注册失败: %s'
                               % json.dumps(reg, ensure_ascii=False)[:200])
        self.device_id = did
        self.install_id = str(reg.get('install_id') or did)
        self._device_token = reg.get('device_token')
        self._cdid = _uuid4()
        return reg

    # ---- 2. 参数集 ------------------------------------------------------
    def load_params(self):
        """生成完整请求参数（30 项，纯 Python，无需 .so）。"""
        if self.device_id is None:
            self.register_device()
        self.params = default_params(
            self.device_id, self.install_id, getattr(self, '_cdid', None))
        return self.params

    # ---- 3. 密钥交换 ----------------------------------------------------
    def register_key(self):
        """POST /reading/crypt/registerkey 换取 content_key。

        关键：不能带 x-ss-stub，否则返回 200 空 body。
        """
        if self.params is None:
            self.load_params()
        body = json.dumps({'content': make_register_content(self.device_id)}).encode()
        res = self._post('/reading/crypt/registerkey', self.params, body)
        if not res.get('data', {}).get('key'):
            raise RuntimeError('registerkey 失败: %s'
                               % json.dumps(res, ensure_ascii=False)[:200])
        self.content_key = decrypt_register_key(res['data']['key'])
        self.keyver = res['data'].get('keyver')
        return self.content_key, self.keyver

    def ensure_session(self):
        if self.content_key is None:
            self.register_key()
        return self

    # ---- 4. 目录与详情 --------------------------------------------------
    def search_raw(self, keyword, offset=0, count=10, tab_type=3, passback=None):
        """原始搜索响应（未解析）。

        响应结构：{code, message, search_tabs: [{tab_type, title, data, has_more,
                                                passback, search_id, next_offset}]}
        其中「书籍」tab（tab_type=3）的 data 是 **list**，每个元素是一个 cell：
            {book_id, search_high_light:{title:{text}, abstract:{text}},
             book_data: [{book_id, book_name, author, abstract, word_number, ...}]}
        注意：data 不是 dict，也没有 cell_data 字段。

        翻页：服务端**忽略 offset**，必须把上一页 tab 里的 `passback`（字符串）
        原样回传，配合 `next_offset` 才能拿到下一页。只传 offset 会永远返回第一页。
        """
        if self.params is None:
            self.load_params()
        p = dict(self.params)
        p.update({'query': keyword, 'offset': offset,
                  'count': count, 'tab_type': tab_type})
        if passback is not None:
            p['passback'] = passback
        return self._get('/reading/bookapi/search/tab/v', p)

    @staticmethod
    def _pick_book_tab(res):
        """从 search_tabs 里挑出书籍 tab；取不到就回退到第一个有数据的 tab。"""
        tabs = res.get('search_tabs') or []
        for t in tabs:
            if t.get('tab_type') == 3 and isinstance(t.get('data'), list):
                return t
        for t in tabs:
            if isinstance(t.get('data'), list) and t.get('data'):
                return t
        return None

    @staticmethod
    def _num(v, cast=int, default=0):
        """服务端部分数值字段是字符串（如 word_number="3201288"），统一转成数字。"""
        if v is None or v == '':
            return default
        try:
            return cast(v)
        except (TypeError, ValueError):
            return default

    @classmethod
    def _parse_search_cells(cls, tab):
        """把书籍 tab 的 data(list) 解析成规整的书目列表。"""
        out = []
        for cell in (tab or {}).get('data') or []:
            if not isinstance(cell, dict):
                continue
            bd = cell.get('book_data') or []
            if isinstance(bd, list):
                b = bd[0] if bd else None
            elif isinstance(bd, dict):
                b = bd
            else:
                b = None
            if not isinstance(b, dict):
                # 极少数 cell 只有 search_high_light，没有 book_data
                hl = cell.get('search_high_light') or {}
                b = {'book_name': ((hl.get('title') or {}).get('text') or ''),
                     'abstract': ((hl.get('abstract') or {}).get('text') or ''),}
            bid = b.get('book_id') or cell.get('book_id')
            if not bid:
                continue
            hl = cell.get('search_high_light') or {}
            status = cls._num(b.get('creation_status'), int, None)
            out.append({
                'book_id': str(bid),
                'book_name': str(b.get('book_name')
                                 or (hl.get('title') or {}).get('text') or ''),
                'author': str(b.get('author') or ''),
                'abstract': str(b.get('abstract')
                                or (hl.get('abstract') or {}).get('text') or ''),
                'word_number': cls._num(b.get('word_number'), int, 0),
                'creation_status': status,
                'is_finished': status == 1,
                'score': cls._num(b.get('score'), float, 0.0),
                'read_count': cls._num(b.get('read_count'), int, 0),
                'category': str(b.get('category') or ''),
                'thumb_url': str(b.get('thumb_url') or ''),
                'last_chapter_title': str(b.get('last_chapter_title') or ''),
            })
        return out

    def search(self, keyword, page=0, count=10, passback=None):
        """搜索书籍，返回规整列表：[{book_id, book_name, author, ...}, ...]

        page>0 时用 passback 令牌翻页（服务端忽略 offset）。若未给 passback，
        会自动从第 0 页逐页走到目标页。
        """
        if page > 0 and passback is None:
            token = None
            for _ in range(page):
                _, token = self._search_page(keyword, count=count,
                                             passback=token)
                if token is None:
                    return []
            return self._search_page(keyword, count=count,
                                     passback=token)[0]
        return self._search_page(keyword, count=count,
                                 passback=passback)[0]

    def _search_page(self, keyword, count=10, passback=None, tab_type=3):
        """取一页搜索结果，返回 (书目列表, 下一页 passback 令牌或 None)。"""
        offset = 0
        if passback is not None:
            try:
                offset = int(passback)
            except (TypeError, ValueError):
                offset = 0
        res = self.search_raw(keyword, offset=offset, count=count,
                              tab_type=tab_type, passback=passback)
        if res.get('code') not in (0, None):
            raise RuntimeError('搜索失败 code=%r message=%r'
                               % (res.get('code'), res.get('message')))
        tab = self._pick_book_tab(res)
        if tab is None:
            # 带 tab_type=3 有时会拿到全空 tab，退回不指定 tab 再试一次
            res2 = self.search_raw(keyword, offset=offset, count=count,
                                   tab_type=0, passback=passback)
            tab = self._pick_book_tab(res2)
        items = self._parse_search_cells(tab)
        nxt = None
        if tab and tab.get('has_more') and tab.get('passback') is not None:
            nxt = tab.get('passback')
        return items, nxt

    def search_more(self, keyword, pages=2, count=10):
        """翻多页搜索（用 passback 令牌），按 book_id 去重。"""
        seen, out, token = set(), [], None
        for _ in range(pages):
            items, token = self._search_page(keyword, count=count,
                                             passback=token)
            for it in items:
                if it['book_id'] not in seen:
                    seen.add(it['book_id'])
                    out.append(it)
            if token is None:
                break
        return out

    def catalog(self, book_id):
        """拉取完整目录，返回 [{item_id, title, order, volume_name}, ...]"""
        if self.params is None:
            self.load_params()
        p = dict(self.params)
        p['book_id'] = str(book_id)
        res = self._get('/reading/bookapi/directory/all_items/v', p)
        data = res.get('data') or {}
        rows = data.get('item_data_list') or []
        out = []
        for r in rows:
            out.append({
                'item_id': str(r.get('item_id')),
                'title': str(r.get('title') or ''),
                'order': r.get('real_chapter_order') or r.get('order') or 0,
                'volume_name': str(r.get('volume_name') or ''),
                'word_number': r.get('chapter_word_number') or 0,
            })
        return out

    def detail(self, book_id):
        p = dict(self.params)
        p['book_id'] = str(book_id)
        return self._get('/reading/bookapi/multi-detail/v', p)

    # ---- 5. 正文 --------------------------------------------------------
    def chapter(self, item_id):
        """单章下载并解密，返回 (title, paragraphs, plain_text)。"""
        self.ensure_session()
        p = dict(self.params)
        p['item_id'] = str(item_id)
        p['keyver'] = str(self.keyver)
        res = self._get('/reading/reader/full/v', p)
        d = res.get('data') or {}
        ct = str(d.get('content') or '')
        if not ct or ct == 'Invalid':
            raise RuntimeError('章节 %s 无可下载内容 (crypt_status=%r)'
                               % (item_id, d.get('crypt_status')))
        title = str(d.get('title') or '')
        paras = xhtml_to_paragraphs(decrypt_content(ct, self.content_key))
        return title, paras, '\n'.join(paras)

    def chapters_batch(self, item_ids):
        """批量下载并解密，返回 {item_id: {title, paragraphs, text, error}}。"""
        self.ensure_session()
        p = dict(self.params)
        p['item_ids'] = ','.join(str(i) for i in item_ids)
        p['keyver'] = str(self.keyver)
        res = self._get('/reading/reader/batch_full/v', p)
        data = res.get('data') or {}
        out = {}
        for iid in item_ids:
            iid = str(iid)
            it = data.get(iid) or {}
            ct = str(it.get('content') or '')
            nd = it.get('novel_data') or {}
            title = str(nd.get('chapter_title') or it.get('title') or '')
            if not ct or ct == 'Invalid':
                out[iid] = {'title': title, 'paragraphs': [], 'text': '',
                            'error': 'no content (crypt_status=%r)'
                                     % it.get('crypt_status')}
                continue
            try:
                paras = xhtml_to_paragraphs(decrypt_content(ct, self.content_key))
                out[iid] = {'title': title, 'paragraphs': paras,
                            'text': '\n'.join(paras), 'error': None}
            except Exception as ex:
                out[iid] = {'title': title, 'paragraphs': [], 'text': '',
                            'error': str(ex)[:120]}
        return out

    # ---- 6. 整本下载 ----------------------------------------------------
    def download(self, book_id, out_path=None, progress=True,
                 max_chapters=None, retries=4):
        """下载整本书，写入 TXT。返回 (章节列表, 总字数)。

        服务端限流表现为空响应体；_get 已有退避重试。若仍失败，则
        重建会话（重新注册设备 + 换 content_key）后重试，最多 retries 轮。
        """
        self.ensure_session()
        cat = self.catalog(book_id)
        if max_chapters:
            cat = cat[:max_chapters]
        if not cat:
            raise RuntimeError('目录为空: %s' % book_id)

        pending = list(cat)
        results = {}
        total = 0
        t0 = time.time()
        rounds = 0

        while pending:
            failed = []
            for i in range(0, len(pending), APP_BATCH_SIZE):
                grp = pending[i:i + APP_BATCH_SIZE]
                ids = [c['item_id'] for c in grp]
                # 单设备配额用尽 -> 主动换设备续传
                if self._fetched + len(ids) > QUOTA_CHAPTERS:
                    if progress:
                        print('  -- 设备配额将满，切换新设备 --', flush=True)
                    self.renew_session()
                try:
                    got = self.chapters_batch(ids)
                    self._fetched += len(ids)
                except Exception as ex:
                    # 触发冷却：等待后换设备重试本组
                    if progress:
                        print('  冷却/失败 %d 章 (%s)，等待 %ds 后换设备'
                              % (len(grp), str(ex)[:60], COOLDOWN_SLEEP), flush=True)
                    time.sleep(COOLDOWN_SLEEP)
                    self.renew_session()
                    failed.extend(grp)
                    continue
                for c in grp:
                    r = got.get(c['item_id']) or {}
                    if r.get('error') or not r.get('text'):
                        failed.append(c)
                        continue
                    results[c['item_id']] = {
                        'item_id': c['item_id'],
                        'title': c['title'] or r.get('title') or '',
                        'text': r['text'],
                        'chars': len(r['text']),
                        'error': None,
                    }
                    total += len(r['text'])
                if progress and (i // APP_BATCH_SIZE) % 10 == 0:
                    done = len(results)
                    el = time.time() - t0
                    eta = el / done * (len(cat) - done) if done else 0
                    print('  %d/%d  %d 字  已用 %.0fs  ETA %.0fs'
                          % (done, len(cat), total, el, eta), flush=True)
                if self.interval:
                    time.sleep(self.interval)   # App 节奏：100ms/组
            if not failed:
                break
            rounds += 1
            if rounds > retries:
                if progress and failed:
                    print('  仍剩 %d 章未能下载' % len(failed), flush=True)
                break
            if progress:
                print('  --- 第 %d 轮重试，剩余 %d 章 ---' % (rounds, len(failed)),
                      flush=True)
            pending = failed

        ordered = [results[c['item_id']] if c['item_id'] in results else {
            'item_id': c['item_id'], 'title': c['title'], 'text': '',
            'chars': 0, 'error': '下载失败'} for c in cat]

        if out_path:
            with open(out_path, 'w', encoding='utf-8') as f:
                for r in ordered:
                    f.write('\n\n' + ('=' * 24) + ' ' + r['title'] + ' ' + ('=' * 24) + '\n\n')
                    f.write(r['text'] if r['text'] else '[下载失败: %s]' % r['error'])
                    f.write('\n')
        return ordered, total

    def renew_session(self):
        """重新注册设备并换取新 content_key（应对配额/冷却）。"""
        self.register_device()
        self.load_params()
        self.register_key()
        self._fetched = 0
        return self



# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def _cli():
    import argparse
    ap = argparse.ArgumentParser(description='番茄小说 纯接口下载器（对齐 App）')
    ap.add_argument('book_id', nargs='?', help='书号')
    ap.add_argument('-o', '--out', help='输出 TXT 路径')
    ap.add_argument('-n', '--num', type=int, help='只下前 N 章')
    ap.add_argument('--search', help='搜索关键词')
    ap.add_argument('--catalog', action='store_true', help='只列目录')
    ap.add_argument('--one', help='只下单个 item_id')
    ap.add_argument('--pages', type=int, default=1, help='搜索翻页数（默认 1）')
    a = ap.parse_args()

    c = FanqieClient()
    reg = c.register_device()
    print('设备已注册 device_id=%s install_id=%s'
          % (c.device_id, c.install_id), flush=True)
    c.load_params()
    c.register_key()
    print('content_key=%s keyver=%s'
          % (c.content_key.hex(), c.keyver), flush=True)

    if a.search:
        hits = c.search_more(a.search, pages=a.pages)
        print('\n搜索结果 %d 条:' % len(hits))
        for it in hits[:30]:
            state = '完结' if it['is_finished'] else '连载中'
            print('  %-20s 《%s》 %s  %s  %.0f 万字  %s'
                  % (it['book_id'], it['book_name'], it['author'], state,
                     it['word_number'] / 10000.0, it['category']))
        if not hits:
            print('  （无结果。可用 book_id 直接下载：'
                  'python fanqie_api.py <book_id>）')
        return

    if a.one:
        t, paras, txt = c.chapter(a.one)
        print('\n%s  %d 段 %d 字' % (t, len(paras), len(txt)))
        print(txt[:600])
        if a.out:
            open(a.out, 'w', encoding='utf-8').write(txt)
        return

    if not a.book_id:
        ap.error('需要 book_id')
    cat = c.catalog(a.book_id)
    print('\n目录 %d 章' % len(cat))
    if a.catalog:
        for ch in cat[:40]:
            print('  %-20s %s' % (ch['item_id'], ch['title']))
        if len(cat) > 40:
            print('  ... 共 %d 章' % len(cat))
        return

    out = a.out or ('%s.txt' % a.book_id)
    print('\n开始下载 -> %s' % out, flush=True)
    res, total = c.download(a.book_id, out_path=out, max_chapters=a.num)
    ok = sum(1 for r in res if not r['error'])
    print('\n完成: %d/%d 章成功, 共 %d 字 -> %s' % (ok, len(res), total, out))


if __name__ == '__main__':
    _cli()
