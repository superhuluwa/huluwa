# 红果独立签名服务

红果短剧的无 Android 签名实现。服务使用
[openhongguo](https://github.com/beilunyang/openhongguo) 最新主分支（固定提交
`015a388112ee148a7d2dfb7ecfafe171bbab957f`，2026-11-12）的公开 Go SDK 注册匿名设备，
并在本机完成签名计算。播放签名包含已合并的 [PR #7](https://github.com/beilunyang/openhongguo/pull/7)
修复（原提交 `bed14595d35b02e0d550270621812ca7386c4108`）。

它兼容葫芦娃使用的原签名接口：

| 接口 | 说明 |
|---|---|
| `GET /health` | 生成一次签名并返回健康状态 |
| `GET /info` | 当前设备参数、引擎版本和恢复统计 |
| `POST /sign` | 输入 `{"url":"...","body":"原样请求体（可选）"}`，返回签名头 |
| `POST /recover` | 更换匿名设备并立即重载，不依赖 Android/Frida |

与 redroid 方案相比，这个服务不需要 Android、ADB、Frida、KVM 或时钟同步，
冷启动约 1 秒。播放模型接口从 2026-11-10 起校验真实 `X-Argus`/`X-Ladon`，普通六头中的
时间戳占位会得到 HTTP 200 空响应。`/novel/player/video_model/` 与
`/novel/player/multi_video_model/` 自动使用播放专用签名，移除 `X-Medusa`；
其他路由继续返回通用六头。POST 时 `body` 必须是实际发送的 UTF-8 JSON 字符串，
服务按原始字节计算 Argus、Gorgon 和 `X-SS-STUB`，不会重新序列化请求体。
省略 `body` 的旧 GET 客户端保持兼容。签名过程只做本地计算，不预先请求播放接口，
也不在 `/sign` 中轮换设备。`/info` 提供 `playbackRevision`、`playbackSigning` 与
`supportsBodySigning`，用于识别支持 PR #7 的部署版本。

最新正式标签仍为 `v0.2.0`，未包含 PR #7；部署固定使用上述主分支提交。
`/info.upstreamRevision` 返回该提交，便于确认 8901 容器的实际来源。

## 构建

构建时需要本机已有这个固定版本的 openhongguo 源码：

```bash
git clone https://github.com/beilunyang/openhongguo.git ../../../openhongguo
git -C ../../../openhongguo checkout 015a388112ee148a7d2dfb7ecfafe171bbab957f
docker compose build
```

`Dockerfile` 使用 Go 1.27.3 多阶段构建。生产机也可以先在能访问 Go 镜像的机器上
生成静态二进制，再用 `Dockerfile.runtime` 打包：

```bash
GOTOOLCHAIN=local CGO_ENABLED=0 go build -trimpath -o bin/hg-signer .
cd ../../../openhongguo && GOTOOLCHAIN=local CGO_ENABLED=0 \
  go build -trimpath -o ../huluwa/hg/portable-signer/bin/hg-register \
  ../huluwa/hg/portable-signer/tools/register-device.go
```

`register-device.go` 带有 `//go:build ignore`，必须在 openhongguo 模块目录内编译。

## 部署

```bash
docker compose up -d
docker compose ps
curl http://127.0.0.1:8901/info
```

设备档案保存在 `hongguo-portable-device-profile` 卷，权限 `0600`。首次启动自动向红果
注册一台匿名设备；重复启动复用同一身份。`POST /recover` 会保留带时间戳的旧档案备份
后再注册新设备，30 秒冷却期内并发请求会合并。

默认只发布 `8901`。如果仍保留旧的 redroid 签名服务，请先停掉它，否则端口冲突。

## 回退

旧方案的服务和卷不删除时，停掉本服务并重新启动原有 compose 项目即可回退。
本服务不修改红果 Android 容器、APK 或葫芦娃数据库。

## 第三方许可

`internal/portable` 中的签名算法来自 openhongguo（Apache-2.0），逐文件来源和
SHA256 记录在 `upstream-sources.json`。二进制包含 Go runtime，许可见
`LICENSE.Go`。详细说明见 `NOTICE.md`。
