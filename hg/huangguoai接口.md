# 黄果短剧（huangguoai.com）搜索与播放接口

> 验证日期：2026-11-19
> 验证方式：直接请求源站；参考 `DRDRDRRDDRDR/huangguo-web` 的 `worker/index.js` 和 `app.js`
> 配套自检脚本：`node hg/huangguoai_check.mjs`

## 1. 搜索

```http
GET https://huangguoai.com/api/search?q={关键词}&page=1&size=20
Referer: https://huangguoai.com/search/
Accept: application/json, text/plain, */*
```

参数名必须是 `q`；传 `keyword` 会返回“请输入搜索关键词”。

响应关键字段：

```json
{
  "status": 1,
  "data": {
    "items": [
      {
        "id": 10586,
        "title": "任意门",
        "episode_count": 6,
        "duration": "549",
        "cover": "https://...",
        "playback_endpoint": "/api/videos/10586/playback"
      }
    ],
    "pagination": {
      "page": 1,
      "pages": 2,
      "size": 2,
      "total": 3
    }
  }
}
```

`items[].id` 就是后续详情和播放请求使用的剧集 ID。`episode_count` 是当前实际集数。

## 2. 详情与选集

```http
GET https://huangguoai.com/api/videos/{id}
Referer: https://huangguoai.com/video/{id}/
```

响应关键字段：

```json
{
  "data": {
    "id": 10586,
    "title": "任意门",
    "episode_count": 6,
    "episodes": [
      { "ep_num": 1, "title": "第1集", "duration": 549, "status": 1 },
      { "ep_num": 2, "title": "...", "duration": 531, "status": 1 }
    ]
  }
}
```

详情页 HTML 里的 `videoInitialData.epPlaySrcs` 通常为空。播放地址不要从这里拼，也不要使用 `/api/videos/{id}/play`，该接口当前返回空 `video_url`。

## 3. 单集播放地址

普通 HTTP 直接请求受保护，必须走站点自己的加密通道。

### 3.1 协议

- 通道：`POST https://huangguoai.com/api/playback/request`
- 版本标记：`dx150-edge-v1`
- 握手：ECDH P-256
- 派生：HKDF-SHA256
- 载荷：AES-GCM

### 3.2 握手

```json
{
  "v": 1,
  "type": "hello",
  "client_pub": "<base64url(ECDH P-256 raw public key)>"
}
```

响应：

```json
{
  "v": 1,
  "type": "hello",
  "kid": "v20261109",
  "sid": "...",
  "server_pub": "...",
  "salt": "...",
  "site": "huangguoai.com",
  "expires_at": 1795062646,
  "server_time": 1795062046
}
```

派生规则：

```text
shared = ECDH(client_private, server_pub)
salt = base64url_decode(hello.salt)
info = "dx150-edge-v1\nplayback\nhuangguoai.com\n{hello.kid}\n{hello.sid}"
request_key  = HKDF-SHA256(shared, salt, info + "\nreq")，AES-256-GCM encrypt
response_key = HKDF-SHA256(shared, salt, info + "\nres")，AES-256-GCM decrypt
```

### 3.3 调用

请求体：

```json
{
  "v": 1,
  "type": "call",
  "kid": "v20261109",
  "sid": "...",
  "seq": 1,
  "ts": 1795062046,
  "ciphertext": "<base64url(AES-GCM 密文)>"
}
```

- `seq` 是会话内递增序号，从 1 开始。
- `ts = floor(Date.now()/1000) + (hello.server_time - 本地时间)`。
- AES-GCM 的 IV 是 12 字节，前 8 字节为 0，后 4 字节为大端 `seq`。
- `additionalData = [info, "req", seq, ts].join("\n")`；解密响应时把 `req` 换成 `res`。
- 明文是 `{"request": packet}`，其中 `packet` 如下：

```json
{
  "method": "POST",
  "path": "/api/videos/10586/episodes/1/playback",
  "body": "<base64('{}')>",
  "content_type": "application/json",
  "visitor_id": ""
}
```

调用响应体：

```json
{
  "v": 1,
  "type": "result",
  "kid": "v20261109",
  "sid": "...",
  "seq": 1,
  "ts": 1795062046,
  "ciphertext": "..."
}
```

解密后得到 `inner`，`inner.body` 是 **base64 编码的 JSON**，再解码得到最终数据：

```json
{
  "data": {
    "id": "10586",
    "ep": 1,
    "streams": {
      "main": "https://...m3u8?auth_key=...",
      "hevc": "https://...m3u8?auth_key=...",
      "preview": "https://...m3u8?auth_key=..."
    },
    "cdn": {
      "m3u8Enabled": true,
      "authKey": "x-cdn-auth",
      "authValue": "4aae16c3b20ec822f87518504478bb35"
    }
  },
  "status": 1
}
```

每一集要单独请求一次：

```text
POST /api/videos/{id}/episodes/{ep}/playback
```

`streams.main` 是正式播放地址，`streams.hevc` 可能为空。

## 4. 播放地址的有效期

m3u8 的 `auth_key` 首个数字段是 Unix 过期时间：

```text
auth_key=1795062107-150.0.web.....
```

实测该时间就是请求后约 1 秒，链接很快就会失效。因此：

- 不要缓存或持久化 m3u8 地址。
- 每次播放、切集、恢复播放前重新调用加密接口。
- 前端拿到地址后应立即加载；HLS 播放器继续使用其中的分片和密钥地址。
- m3u8 本身含 `#EXT-X-KEY:METHOD=AES-128`，分片由播放器按 m3u8 解密。

## 5. 自检脚本

```bash
# 搜索
node hg/huangguoai_check.mjs search 任意门 1 10

# 查集数与每集信息
node hg/huangguoai_check.mjs episodes 10586

# 取第 1 集播放地址，并探测 m3u8 时长、分片数、密钥方式
node hg/huangguoai_check.mjs play 10586 1

# 搜索后批量取前 10 部、每部前 20 集的播放地址
node hg/huangguoai_check.mjs all 任意门
```

脚本只依赖 Node.js 18+ 自带的 `fetch` 和 WebCrypto，没有第三方依赖。
