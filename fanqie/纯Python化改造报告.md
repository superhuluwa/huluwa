# 脱离 libfanqie.so：纯 Python 化改造报告

> 目标：把 `fanqie_api.py` 从"必须带 libfanqie.so + unicorn"改成完全纯 Python。
> **结果：目标已达成，并进一步合并为单文件。**
> `libfanqie.so`、`unicorn`、以及全部辅助模块均已删除，
> 现在**只需 `fanqie_api.py` 一个文件**（零第三方依赖），全部功能实测通过。

## 单文件验收

隔离目录只放 `fanqie_api.py`（无 `.so`、无任何其他模块）实测：

```
目录内容 = ['fanqie_api.py']
unicorn/emu/fanqie_pure/fanqie_algo/aes_min 均未加载

算法自检
  AES-128 FIPS197       OK
  SM3 标准向量          OK
  x_ladon               OK
  tt_encrypt/decrypt    OK
  build_proto/x_argus   OK  proto=160 argus=260

线上全链路
  1. 注册    device_id = 639290951351587
  2. 参数    30 项
  3. 密钥    content_key = fb6103af24d0cbdc9d3be5ac9c0fbac1
  4. 搜索    10 条, 首条 十日终焉
  5. 目录    1496 章
  6. 单章    第1章 空屋  段=66 字=2410
  7. 批量    8/8
  8. 整本    20/20 章, 46674 字
```

删除全部辅助模块后**再次复验**，结果不变。

### 已删除的文件

| 文件 | 原用途 |
|---|---|
| `libfanqie.so.bak` | 原 APK 原生库（18.2 MB） |
| `output/07_so算法逆向/fanqie_pure.py` | tt_encrypt / x_argus / params |
| `output/07_so算法逆向/aes_min.py` | AES-128 |
| `output/07_so算法逆向/fanqie_algo.py` | x-ladon / SM3 / UA 模板 |
| `output/07_so算法逆向/emu.py` | Unicorn 仿真器 |
| `output/07_so算法逆向/ladon_pure.py` | x-ladon 早期版本 |
| `output/07_so算法逆向/tt_encrypt_algo.py` | tt_encrypt 早期版本 |
| `output/07_so算法逆向/fanqie_sign.py` | 签名早期版本 |
| `output/07_so算法逆向/fanqie_static.json` | 导出的参数样本 |
| `aes_min.py`（根目录副本） | AES-128 副本 |

### `fanqie_api.py` 内部结构（约 1370 行）

| 区块 | 内容 |
|---|---|
| 一 | AES-128（CBC / PKCS#7） |
| 二 | SHA-512 压缩函数 / rev64 |
| 三 | `tt_encrypt`（注册体加密） |
| 四 | SM3 |
| 五 | `x_ladon` |
| 六 | `x_argus` + `build_proto` + `hash160` |
| 七 | 请求参数 / 设备注册体 |
| 八 | 业务层（HTTP、正文解密、下载） |

### 作为库使用

```python
from fanqie_api import FanqieClient

c = FanqieClient()
c.register_device()      # 注册设备
c.load_params()          # 30 项参数
c.register_key()         # 换 content_key

hits = c.search('十日终焉')
cat  = c.catalog(hits[0]['book_id'])
t, paras, text = c.chapter(cat[0]['item_id'])
c.download(book_id, out_path='out.txt')
```

---

## 一、结论

| 环节 | 原实现 | 现在 | 依赖 `.so` |
|---|---|---|---|
| 设备注册请求体 | 仿真 | 纯 Python 常量 + 随机化 | ❌ |
| `tt_encrypt` | 仿真 `0x696900` | 纯 Python（逐字节对拍） | ❌ |
| `default_params`(30 项) | 仿真 `0x698D40` | 静态常量 | ❌ |
| `x-ladon` | 纯 Python | 不变 | ❌ |
| **`x-argus`** | 仿真 `sign_query` | **纯 Python（逐字节对拍 + 线上实测）** | ❌ |
| 正文解密 AES | 纯 Python | 不变 | ❌ |

**`libfanqie.so` 与 `unicorn` 已完全移除。**

### 验收证据

```
libfanqie.so 存在 = False
unicorn 已加载 = False
emu 已加载     = False

1. 设备注册      device_id = 3383671972696660              OK
2. 参数          30 项                                      OK
3. 密钥交换      content_key = 55b3e572..., keyver=...      OK
4. 搜索          10 条，首条《十日终焉》                     OK
5. 目录          1496 章                                    OK
6. 单章          66 段 / 2410 字                            OK
7. 批量 8 章     8/8                                        OK
8. 整本(前20章)  20/20 章 / 46674 字                        OK
```

---

## 二、`x-argus` 完整算法（本轮攻克）

```
x-argus = base64( b"\xf2\x81" || AES-128-CBC-PKCS7(msg, ARGUS_KEY, ARGUS_IV) )

msg  = C9(9B) || D168 || b"\x61\x6f"
C9   = a66ead9f7701d00c18                    # 常量
D168 = mirror8( KEY8 || (hash160(proto) XOR KEY8[i%8]) )
KEY8 = f2f7fcfff2f7fcff                      # 8B 常量
```

### 2.1 `hash160`（自定义类 SM3 哈希）

```python
BUF_INIT = [0x740c7a65a9e078fc, 0x03cf3c905915e58c,
            0xd732f2cfd3510e51, 0x04531c328ae84313]
CONST9 = 0x04531c328ae84313      # = BUF_INIT[3]
X27    = 0x3dc94c3a046d678b      # 常量

def build_w():                   # 消息扩展：68 轮（关键，不是 62）
    w = BUF_INIT + [0]*68
    x9 = CONST9
    for j in range(68):
        shift = j if (j + 4 < 0x42) else (j - 0x3e)
        x13 = X27 >> shift
        x9  = w[j+1] ^ ror(x9, 3)
        t   = (x13 & 1) | 0xfffffffffffffffc
        r   = w[j] ^ ror(x9, 1)
        x9  = (t ^ x9) ^ (r ^ x9)
        w[(j+4) % 72] = x9
    return w

def compress(x23, x26, w):       # 压缩：72 轮
    for k in range(72):
        x9  = ror(x23, 63) & ror(x23, 56)
        x11 = x26 ^ ror(x23, 62)
        x23, x26 = ((x11 ^ w[k]) ^ x9), x23
    return x23, x26
```

按 16 字节分块（每块 `ldp` 得 `x26=lo, x23=hi`），逐块压缩，
输出顺序 `x26` 在前、`x23` 在后。

### 2.2 `mirror8`（8 字节块反转 + 块序颠倒）

```python
def mirror8(b):
    u = [b[i:i+8] for i in range(0, len(b) - len(b) % 8, 8)]
    return b''.join(x[::-1] for x in u[::-1])
```

### 2.3 三处关键坑

| # | 坑 | 说明 |
|---|---|---|
| 1 | 扩展是 **68 轮**不是 62 | `j+4 < 0x42(66)` 只让 `j<62` 走一个分支，循环实际到 `x8=0x40`，共 68 次。少算会让 `w[66..71]` 全 0 |
| 2 | `S = H XOR KEY8` 循环异或 | 不是简单拼接；`S[i] = H[i] ^ KEY8[i%8]` |
| 3 | proto 长度**不截断**，按 16B 对齐填充 | 填充长度 `n = 16 - len%16`，填充字节值 = `n`；已对齐时追加单字节 `0x01` |

### 2.4 `proto` 字段结构

| 字段 | 值 | 动态 |
|---|---|---|
| f1/f2/f3 | `0x40401252` / 2 / `0x7a55300b` | 常量 |
| f4 | aid `"1967"` | 常量 |
| f5 | device_id | **动态** |
| f6 | `"1611921764"` | 常量 |
| f7 | `"6.8.1.32"` | 常量 |
| f8 | `"v04.04.05-ov-android"` | 常量 |
| f9/f10/f11 | 134744640 / 8×00 / 0 | 常量 |
| f12 | `secs * 2` | **动态** |
| f13 | `106e34a2b8c7` | 常量 |
| f14 | `sm3(query)[:6]` | **动态** |
| f15 | `08011001180138ccd9cbbc0c` | 常量（整体） |
| f16 | `""` | 常量 |
| f20/f21 | `"none"` / 738 | 常量 |
| f23 | 17B 常量 | 常量 |
| 尾 | `c8 01 02` + 对齐填充 | — |

---

## 三、`tt_encrypt` 完整公式

```
R    = os.urandom(32)
call0: compress(IV, R || 0x80 || 0*55 || be128(256))   -> A
call1: compress(IV, rev64(A) || CONST)                  -> B
call2: compress(B,  0x80 || 0*110 || be128(1024))       -> C
D    = rev64(C)          →  key=D[0:16], iv=D[16:32]
out  = b"tc" + b"\x05\x10\x00\x00" + R + AES-CBC-PKCS7(sha512(gz)||gz)
```

两个坑：call2 长度字段是 **1024 bit**（非 0）；gzip 头 XFL=`0x00`、OS=`0xff`。

---


## 四、说明

现在全部算法都在 `fanqie_api.py` 一个文件内，无任何外部模块、无 `.so`、无第三方包。
文件拷贝到任意目录即可运行。

---

## 五、合规提示

以上技术仅用于接口协议研究与个人学习。批量抓取番茄小说正文很可能违反其
服务条款，请勿用于分发或商业用途。
