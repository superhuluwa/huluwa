# 红果独立签名服务

红果短剧的无 Android 签名实现。服务使用
[openhongguo](https://github.com/beilunyang/openhongguo) v0.2.0（提交
`42b73f201309423f012d483bfc0ce579dcc0937c`）的公开 Go SDK 注册匿名设备，
并在本机完成签名计算。

它兼容葫芦娃使用的原签名接口：

| 接口 | 说明 |
|---|---|
| `GET /health` | 生成一次签名并返回健康状态 |
| `GET /info` | 当前设备参数、引擎版本和恢复统计 |
| `POST /sign` | 输入 `{"url":"..."}`，返回 6 个签名头 |
| `POST /recover` | 更换匿名设备并立即重载，不依赖 Android/Frida |

与 redroid 方案相比，这个服务不需要 Android、ADB、Frida、KVM 或时钟同步，
冷启动约 1 秒。播放接口返回 `110` 后，葫芦娃后端现有的 `/recover` 兜底会更换匿名
设备，必要时由 SDK 自动再换一次；旧的 `/sign` 调用协议不变。

## 构建

构建时需要本机已有这个固定版本的 openhongguo 源码：

```bash
git clone https://github.com/beilunyang/openhongguo.git ../../../openhongguo
git -C ../../../openhongguo checkout 42b73f201309423f012d483bfc0ce579dcc0937c
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
