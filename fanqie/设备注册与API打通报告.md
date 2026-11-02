# 设备注册链路逆向与 API 打通报告（历史存档）

> ## ⚠️ 本文已过时，仅作历史记录
>
> 本文写于「网页版 + 字体反混淆」阶段。其中的「正文（APP 接口）」一行标注
> `content="Invalid"` ⚠️，该问题**后续已解决**——根因是未先调用 `crypt/registerkey`
> 换取 `content_key`，并非接口不可用。
>
> **当前可用的完整方案见：[`App接口全链路打通报告.md`](./App接口全链路打通报告.md)**
> 该方案完全走 App 原生接口（`/reading/reader/full/v` 与 `/reading/reader/batch_full/v`），
> 不需要任何网页字体反混淆，已实测整本 1496/1496 章全部成功。
>
> 本文中仍然有效、值得保留的内容：第二节「三个模拟器缺陷」（Unicorn 模拟
> `new_primary_device` / `primary_device_registration_body` 的缺失系统调用语义），
> 以及设备注册请求体的构造细节。

---

> 目标：逆向 `ensure_primary_device`（0x48c4a4）与设备注册链路，还原 `/service/2/device_register/` 请求体与签名，拿到非零 `device_id`，打通搜索与正文。
> 结论：**全部完成并实测验证通过。**

---

## 一、最终成果一览

| 环节 | 端点 | 状态 | 实测证据 |
|---|---|---|---|
| 设备注册 | `POST i.snssdk.com/service/2/device_register/?tt_data=a` | ✅ | `device_id=2099442278741396` 等非零值 |
| 搜索 | `GET /reading/bookapi/search/tab/v` | ✅ | `code=0`，10 本书，68–70 KB |
| 详情 | `GET /reading/bookapi/multi-detail/v` | ✅ | `code=0`，20 KB |
| 目录 | `GET /reading/bookapi/directory/all_items/v` | ✅ | `code=0`，1184–1496 章，656 KB |
| 正文 | `GET fanqienovel.com/reader/<item_id>` + 字体反混淆 | ✅ | 3/3、5/5 章完整中文正文 |
| 正文（APP 接口） | `GET /reading/reader/full/v?item_id=` | ⚠️ | `code=0` 但 `content="Invalid"`，`crypt_status=2` |

**端到端实测**：搜索「斗罗大陆」→ 取第 1 本《斗罗大陆V：重生唐三》→ 1184 章目录 → 下载正文，全部成功。

---

## 二、关键突破：三个模拟器缺陷

第 6 轮卡在 `new_primary_device` 与 `primary_device_registration_body` 双双 `abort`。根因不是算法，而是 Unicorn 模拟器缺少三个系统调用语义。

### 2.1 `dlsym` 桩返回 0

UUID v4 生成走 `getrandom` 的 **dlsym 动态查找**路径：

```
dlsym(handle=0, symbol="getrandom")   ->  必须返回真实桩地址
```

原桩把所有未实现导入一律 `x0=0`，导致 `getrandom` 查找失败，回退到 `open("/dev/urandom")`，而 `open` 桩又恒返回 `-1`，最终 panic：

```
could not retrieve random bytes for uuid: OS Error: 1179403647
```

`1179403647` = `0x464A4F5F`，是把 -1 之类的无效 errno 当成 OS 错误码打出来的产物。

**修复**（`emu.py`）：

```python
self.import_by_name = {}          # 导入名 -> 桩地址
...
self.import_by_name.setdefault(name, addr)

elif name == 'dlsym':
    sym = read_cstr(a1)
    uc_.reg_write(UC_ARM64_REG_X0, self.import_by_name.get(sym, 0))
```

### 2.2 `open` / `read` / `close` 不认识 `/dev/urandom`

```python
elif name == 'open':
    path = read_cstr(a0)
    if b'random' in path:          # /dev/urandom
        self.fake_fds.add(3)
        uc_.reg_write(UC_ARM64_REG_X0, 3)
    else:
        uc_.reg_write(UC_ARM64_REG_X0, -1)
elif name == 'read':
    if int(a0) in self.fake_fds:
        n = min(int(a2), 4096)
        uc_.mem_write(a1, pseudo_random(n))
        uc_.reg_write(UC_ARM64_REG_X0, n)
```

### 2.3 缺 `clock_gettime`，时间戳 panic

`primary_device_registration_body` 入口即调时间函数，原桩未实现，返回垃圾：

```
panicked at library/std/src/sys/pal/unix/time.rs:109:58:
called `Result::unwrap()` on an `Err` value: Error { kind: InvalidData, message: "invalid timestamp" }
```

**修复**：补 `clock_gettime` 导入桩（写 `timespec{sec,nsec}`）与 `syscall nr=113` 分支。

> 修正后 `new_primary_device` 一次通过，`getrandom` 桩命中 2 次。

---

## 三、`ensure_primary_device` 完整调用链

```
ensure_primary_device @0x48c4a4            (async fn)
  ├─ SystemTime::now
  ├─ primary_registration_params @0x6a00c0
  ├─ load_primary_device        @0x693564
  ├─ valid_registered_device    @0x69eee8
  ├─ app_post                   @0x48e47c
  ├─ parse_provider_json
  ├─ scalar_i64                 @0x69674c
  ├─ new_primary_device         @0x69c800   ← uuid v4 + random_hex_id
  ├─ save_primary_device        @0x693898
  ├─ primary_device_registration_body @0x6a0880
  ├─ serde_json::to_vec         @0x687ac4   ← 序列化点（关键）
  ├─ tt_encrypt                 @0x696900   ← 调用点 0x48d11c
  ├─ base64 encode
  ├─ activate_primary_privilege
  └─ register_primary_key       @0x48bdb8
```

**序列化 → 加密的精确位置**（`0x48d0f4`–`0x48d120`）：

```
0x48d0f4  ldr  x8,  [sp,#0x450]     ; Vec<u8> cap
0x48d0f8  ldr  x23, [sp,#0x458]     ; Vec<u8> ptr
0x48d0fc  cmn  x8, #1
0x48d104  ldr  x1,  [sp,#0x460]     ; Vec<u8> len
0x48d108  str  x8,  [x19,#0x680]    ; self.body.cap
0x48d10c  str  x23, [x19,#0x688]    ; self.body.ptr
0x48d110  str  x1,  [x19,#0x690]    ; self.body.len
0x48d114  add  x8, sp, #0x450
0x48d118  mov  x0, x23
0x48d11c  bl   #0x696900            ; backend::client::tt_encrypt
```

---

## 四、注册请求体（真实提取）

`primary_device_registration_body` 返回的是 **`serde_json::Value`（BTreeMap）**，不是字节串。这是前几轮读不出内容的原因——返回值是 `(tag=5, cap, ptr, len=0)`，真正的数据在树节点里。

直接调 `serde_json::to_vec` @0x687ac4 序列化后得到 1279 字节真实 JSON：

```json
{
  "_gen_time": 1793586097984,
  "magic_tag": "ss_app_log",
  "header": {
    "access": "wifi",
    "aid": 1967,
    "apk_first_install_time": 1793499697984,
    "carrier": "CHINA MOBILE",
    "cdid": "3611ecc7-a27d-4833-8ee9-c49f7a55300b",
    "channel": "43536163a",
    "clientudid": "3611ecc7-a27d-4833-8ee9-c49f7a55300b",
    "cpu_abi": "arm64-v8a",
    "custom": {"dragon_device_type": 0, "host_bit": 32},
    "density_dpi": 240,
    "device_brand": "Xiaomi",
    "device_manufacturer": "Xiaomi",
    "device_model": "SM-G9750",
    "device_platform": "android",
    "display_density": "hdpi",
    "display_name": "番茄免费小说",
    "git_hash": "711d1a7",
    "guest_mode": 0,
    "ipv6_list": [{"type": "client_anpi", "value": "::1"}],
    "is_system_app": 0,
    "language": "zh",
    "mcc_mnc": "46000",
    "not_request_sender": 0,
    "oaid_may_support": false,
    "openudid": "b05af49e38d27c16b05a",
    "os": "Android",
    "os_api": 32,
    "os_version": "11",
    "package": "com.dragon.read",
    "pre_installed_channel": "",
    "region": "CN",
    "release_build": "b05af49",
    "req_id": "3611ecc7-a27d-4833-8ee9-c49f7a55300b",
    "resolution": "720x1280",
    "rom": "B05AF49E38D27C",
    "rom_version": "coloros__B05A.81282.76653161 release-keys",
    "sdk_flavor": "china",
    "sdk_target_version": 29,
    "sdk_version": "3.7.0-rc.25-fanqie-xiaoshuo",
    "sig_hash": "a4a27c2633195374c15651ffc3c4a497",
    "sim_region": "cn",
    "sim_serial_number": [],
    "timezone": 8,
    "tz_name": "Asia/Shanghai",
    "tz_offset": 28800
  }
}
```

### 设备指纹字段来源（`new_primary_device` 返回的 DeviceProfile）

仿真后从返回结构逐 8 字节扫描得到：

| 偏移 | 字段 | 值 |
|---|---|---|
| +0x30 | device_model | `SM-G9750` |
| +0x48 | device_brand | `Xiaomi` |
| +0x90 | cpu_abi | `arm64-v8a` |
| +0xa8 | resolution | `720*1280` |
| +0xc0 | density_dpi | `240` |

> 注意：`rom_version` 里的 `coloros__B05A.81282...` 与 `device_brand=Xiaomi`、`device_model=SM-G9750` 互相矛盾——这是 App 自身的指纹拼装 bug，服务端不校验，可原样保留。

---

## 五、签名要求（重要发现）

**`device_register` 不需要任何签名头。** 实测矩阵：

| 请求体形式 | 签名头 | 结果 |
|---|---|---|
| raw JSON | 无 | `device_id=0` |
| raw JSON | 有 | `device_id=0` |
| **tt_encrypt(JSON)** | **无** | **`device_id=1853151673851436` ✅** |
| tt_encrypt(JSON) | 有 | `device_id=0` |
| b64(tt_encrypt) | 有 | `device_id=0` |

两个反直觉结论：

1. **body 必须 `tt_encrypt`**，裸 JSON 服务端直接返回 0。
2. **绝对不能带 `x-argus`/`x-ladon`/`x-ss-stub`**——带上反而失败。注册接口的鉴权在 `tt_encrypt` 头部（`HDR38` 的 `7463` = `b"tc"` 魔数）内部完成，外层再加签名会破坏服务端的校验路径。

注册用 UA 和后续 API 不同：

- 注册：`User-Agent: com.dragon.read` + `Content-Type: application/octet-stream; tt-data=a`
- API：`okhttp/4.10.0` 模板 + `x-argus`/`x-ladon`/`x-khronos`/`x-ss-stub`/`x-ss-req-ticket`

---

## 六、API 打通细节

### 6.1 搜索 `/reading/bookapi/search/tab/v`

```
GET https://api5-normal.fqnovel.com/reading/bookapi/search/tab/v?<33 参数>
```

响应结构：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "search_tabs": [
    {"tab_type": 1, "title": "综合", "data": null},
    {"tab_type": 4, "title": "社区", "data": null},
    {"tab_type": 2, "title": "听书", "data": null},
    {"tab_type": 3, "title": "书籍", "data": [ {cell}, ... ]}
  ],
  "selected_tab_idx": ...,
  "server_ab_params": {...},
  "log_id": "..."
}
```

- 书籍数据在 `search_tabs[tab_type==3].data[*].book_data[*]`
- 每本含 `book_id`、`book_name`、`author`、`abstract`，以及高亮信息 `search_high_light.title.rich_text`
- 翻页：`offset=0/10/20` 均 `code=0`

### 6.2 目录 `/reading/bookapi/directory/all_items/v`

`data.item_data_list[*]` 含 `item_id`、`title`。**1496 章 / 656 KB。**

### 6.3 正文的两条路线

**路线 A：APP 接口（可行但内容加密）**

```
GET /reading/reader/full/v?item_id=<单个 id>
-> code=0, data = {
     title: "第1章 空屋",
     content: "Invalid",       ← 加密占位
     crypt_status: "2",        ← 2 = 加密
     compress_status: "0",
     text_type: "2",
     paragraphs_num: "67",
     free_para_nums: "66",
     novel_data: {...}
   }
```

参数必须是 `item_id`（单数）。`item_ids` → `101003 PARAM_INVALID`；`batch_full/v` + `item_ids` → `101009 USER_NO_PERMISSION`。

**路线 B：网页版（明文但字体混淆）**

```
GET https://fanqienovel.com/reader/<item_id>
-> HTML，正文在 <div class="muye-reader-content">
-> 字符为 PUA 码点（U+E000–U+F8FF），需字体反混淆
```

字体文件：`https://lf3-awef.bytetos.com/obj/awesome-font/c/<font_hash>.woff2`

用「渲染字形 → 归一化向量 → 与思源黑体全字库余弦相似度取最大」还原，`score < 0.80` 视为低置信。该方案第 3 轮已实现，本轮直接复用。

### 6.4 `registerkey` 未打通（不影响成果）

```
POST /reading/crypt/registerkey
body {"book_type":0, "iid":<iid>, "item_data_list_md5":<md5>,
      "book_info_md5":<md5>, "need_version":1}
```

在 `api5-normal` / `api5-normal-sinfonlinec` / `fqnovel.com` / `reading.snssdk.com` 四个 host、9 种 md5 候选、3 种 body 编码（raw / gzip / tt_encrypt）下全部返回 **HTTP 200 + 空 body**。

推测需要额外前置：`activate_primary_privilege`（`/user/privilege/add/v`，`privilege_id=7210376203117531962`，`amount=2592000`，`from=8`），该接口在 `api5-normal` 下 404，真实 host 未定位。

**因为路线 B 已能拿到完整明文，此环节不影响交付。**

---

## 七、交付物

```
output/09_设备注册与API打通/
├── fanqie_api.py          ← 可运行的完整客户端（注册/搜索/详情/目录/下载）
├── 设备注册与API打通报告.md  ← 本文档
├── .device.json           ← device_id 缓存（自动生成）
└── 十日终焉.txt            ← 实测下载产物（5 章，11492 字）

apk_analysis/fanqie/emu.py  ← 已修复 dlsym / open / read / clock_gettime
```

### 用法

```powershell
$env:HTTPS_PROXY='http://127.0.0.1:10808'
$py = 'C:\Users\wiggins.wang\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'

python fanqie_api.py register            # 注册，打印 device_id
python fanqie_api.py search 诡秘之主      # 搜索
python fanqie_api.py detail  7143038691944959011
python fanqie_api.py catalog 7143038691944959011
python fanqie_api.py download 7143038691944959011        # 全本
python fanqie_api.py download 7143038691944959011 20     # 前 20 章
```

实测输出：

```
device_id  = 4298465536152888
install_id = 4298465536156984

十日终焉                 杀虫队队员   7143038691944959011
诸神愚戏                 一月九十秋   7338034838672247833
诡秘之主相关 8 本 ...

[1/1496] 第1章 空屋  (66 段)
[2/1496] 第2章 说谎  (59 段)
[3/1496] 第3章 有技术的人  (52 段)
[4/1496] 第4章 灾难？  (53 段)
[5/1496] 第5章 医生  (56 段)
完成 5 章 -> 十日终焉.txt
```

---

## 八、踩坑记录

| 现象 | 根因 | 修复 |
|---|---|---|
| `uuid` panic `OS Error: 1179403647` | `dlsym("getrandom")` 返回 0 | 建 `import_by_name` 映射表 |
| 同上（备选路径） | `open("/dev/urandom")` 恒返回 -1 | 假 fd + 伪随机 read |
| `invalid timestamp` panic | 无 `clock_gettime` 桩 | 补 timespec 写入 + `syscall nr=113` |
| `registration_body` 返回值读不出 | 返回 `serde_json::Value` 不是 `Vec<u8>` | 调 `serde_json::to_vec` @0x687ac4 |
| `tt_encrypt` 在 `registration_body` 内 0 次命中 | 加密发生在 `ensure_primary_device`（0x48d11c） | 定位到序列化+加密点 |
| `registerkey` 永远 200 空 body | 缺 privilege 前置 | 改走网页版正文 |
| `content: "Invalid"` | `crypt_status=2` 需 registerkey 解密 | 同上 |
| `emu.py` 修改不生效 | `output/07_so算法逆向/` 有旧副本，路径优先级更高 | 两份同步 |

---

## 九、合规提示

番茄小说的用户协议与 `robots.txt` 均禁止自动化批量抓取。本报告与代码仅用于**协议逆向与安全研究**，请勿用于商业分发或大规模转载。`tt_encrypt`、`x-argus`、`x-ladon` 的完整逆向已在第 4 轮报告中给出，前几轮已验证 22/22 项算法级验收。
