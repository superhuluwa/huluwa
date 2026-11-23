# 红果 Frida 签名服务：单容器

默认 Compose 只启动一个 `hongguo-frida` 容器，镜像包含 Android 14、ARM64 翻译层、Python、Frida 客户端和服务端，以及构建时放入的红果 APK。默认对外为 HTTP `8902`（容器内 `8901`），`/info`、`/sign` 和请求体签名协议保持兼容。

```text
Debian x86_64
  └─ hongguo-frida（一个容器、一份 Android 数据卷）
       ├─ Android init（PID 1）→ Android 14 → 红果 App → Frida 服务端
       ├─ Android init 管理的 Python + Frida 客户端 → HTTP :8901
       └─ 独立看门狗 → 整条签名链持续失效时重启容器
```

Android 使用 Bionic，Python、ADB 和 Frida 客户端在镜像内的 Debian 根目录 `/signer-rootfs` 中运行。双方共用进程和网络命名空间，ADB 直接连接 `127.0.0.1:5555`，Frida 直接连接 `127.0.0.1:27042`，不再依赖两个容器之间的 DNS、启动顺序或端口转发。Android 必须保持为 PID 1，签名服务通过 Android init 的服务机制启动和恢复。

## 首次准备

宿主机需要 x86_64 Debian、Docker Engine 和 Compose，不需要 KVM 或 QEMU。首次以 root 执行：

```bash
cd hongguo-sign
bash prepare-host.sh
mkdir -p apk
cp /已有路径/base.apk apk/base.apk
cp .env.example .env
```

使用当前已兼容的 `com.phoenix.read` 单 APK，版本 `7.3.9.32`。APK 不提交到 Git；构建时放入镜像，运行时无需挂载宿主机源码或 APK 目录。构建后可将镜像导出到另一台已经准备好 Binder 的 x86_64 主机。

`prepare-host.sh` 会保存 Binder 模块和设备权限配置，并配置 `hongguo-binder.service` 在 Docker 启动前准备设备，避免宿主机重启后的设备创建顺序问题。它不会重启 Docker 或宿主机。如果已加载的 Binder 模块缺少额外设备，按提示正常重启后重新执行。

## 从现有双容器迁移

旧部署位于 `192.168.3.83:/opt/hongguo-sign`，设备数据卷为 `hongguo-native-data`，APK 位于 `/opt/hongguo-docker/apk/base.apk`。更新本目录后，先构建镜像，成功后再停止旧服务：

```bash
cd /opt/hongguo-sign
mkdir -p apk
cp /opt/hongguo-docker/apk/base.apk apk/base.apk
bash prepare-host.sh
docker compose build hongguo

# 旧容器都必须停止，避免同时写同一份 Android 数据或争用 8901 端口。
docker compose -f compose.legacy.yaml stop signer android

# 独立复制现有设备资料；新卷必须为空，原卷保留用于回退。
docker volume create hongguo-frida-data
cp -a /var/lib/docker/volumes/hongguo-native-data/_data/. /var/lib/docker/volumes/hongguo-frida-data/_data/
docker compose up -d hongguo
```

默认使用独立的外部卷 `hongguo-frida-data`，从原卷复制后保留设备身份和 App 初始化状态，原卷 `hongguo-native-data` 不变。Compose 不负责删除外部卷。完成新端口播放取址、容器重启和再次取址检查后，再将飞牛 `frpc-caiji` 的公网 `8056` 映射改为 `192.168.3.83:8902`。公网取址再次成功后才删除旧的两个容器，原数据卷继续保留。

旧容器删除前可先停止新容器，再恢复旧方案；若已迁移公网映射，还应将目标端口恢复为 `8901`：

```bash
docker compose stop hongguo
docker compose -f compose.legacy.yaml up -d
```

这台 Debian 内核未启用 BinderFS，多个 Android 共用 `/dev/binder`、`/dev/hwbinder`、`/dev/vndbinder` 会发生服务注册冲突。签名 Android 与 TV 的 `redroid` 也不能同时运行；需要并存时，先在宿主机配置独立的 Binder 设备或启用 BinderFS。

不要同时运行新旧方案。`compose.legacy.yaml` 是原来的双容器配置，仍使用旧镜像和宿主机 APK 挂载，保留用于迁移回退。

## 日常启动

镜像已构建、宿主机已准备好以后：

```bash
docker compose up -d
docker compose restart hongguo
docker compose logs -f --tail 100 hongguo
```

也可只用已有镜像启动：

```bash
docker run -d --name hongguo-frida --restart unless-stopped \
  --cgroupns private \
  --cap-add ALL --cap-drop SYS_BOOT --cap-drop SYS_MODULE \
  --cap-drop SYS_RAWIO --cap-drop SYS_TIME \
  --security-opt seccomp=unconfined --security-opt apparmor=unconfined \
  --device /dev/binder --device /dev/hwbinder --device /dev/vndbinder \
  --device /dev/kmsg --device /dev/fuse \
  --cpus 6 --memory 5g --shm-size 512m \
  -p 8902:8901 -v hongguo-frida-data:/data \
  hongguo-frida:single-amd64
```

初次使用全新数据卷时会自动安装 APK。App 自身的首次隐私确认和引导仍可能需要完成一次；可临时设置 `APP_START_MODE=activity`，通过容器内 ADB 操作，完成后恢复 `provider`。迁移现有数据卷时不需要重新完成引导。不能把“镜像包含 APK”当成已经包含注册完成的设备数据。

## 启动与故障恢复

启动顺序为 Android 完成引导 → ADB 就绪 → 安装或复用 App → Frida 协议可用 → 启动 App → 初始化 → 完整设备档案与六头签名可用。

- 每条 ADB 命令都有超时；启动时清除旧连接并重连，防止卡在离线会话。
- 单容器直接访问 Frida，不执行会与同一端口冲突的 ADB 转发。
- 使用真实 Frida 调用等待就绪，替代固定等待三秒后直接注入。
- `/health` 在一次 RPC 中检查 `device_id`、`iid`、`cdid`、User-Agent 和六个签名头。只返回部分签名或设备注册未完成时返回 503。
- 签名服务连续三次检查失败后退出，由 Android init 清理其进程组并重新初始化 App 和 Frida。
- 独立看门狗覆盖首次引导失败和 Android 自身卡死。启动后默认允许 600 秒就绪；正常工作后，持续 300 秒不可用会终止本容器 PID 1，由 Docker 的重启策略恢复整个环境。Docker 的 `unhealthy` 状态本身不会自动触发重启。

如修改 `ANDROID_DATA_VOLUME`，需要先通过 `docker volume create <卷名>` 创建对应的外部卷。

`.env` 中可以调整 `BOOT_TIMEOUT`、`APP_WARMUP`、`SIGN_READY_TIMEOUT`、`STARTUP_TIMEOUT`、`RECOVERY_TIMEOUT`、对外地址和端口。`STARTUP_TIMEOUT` 必须大于 `BOOT_TIMEOUT + APP_WARMUP + SIGN_READY_TIMEOUT`；较慢的首次安装可增大这个值。

Android 保持非特权容器，使用私有 cgroup，只将本容器的 cgroup 挂载改为可写。保留 Docker 对 `/proc/sys`、`/proc/sysrq-trigger` 的只读保护，并移除 `SYS_BOOT`、`SYS_MODULE`、`SYS_RAWIO` 和 `SYS_TIME`。不要改用 `--privileged`、宿主机 PID 或宿主机 cgroup 命名空间。

## 接口与定位

```bash
curl http://192.168.3.83:8902/info
curl http://192.168.3.83:8902/health
curl -X POST http://192.168.3.83:8902/sign \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://api5-normal-sinfonlineb.fqnovel.com/reading/bookapi/search/tab/v?aid=8662&query=test"}'
```

`/info.deviceProfile` 提供 App 的设备参数和 `user_agent`，调用端应使用相同身份。`supportsBodySigning=true` 表示 `POST /sign` 支持 `body_base64` 原始请求字节，也兼容 UTF-8 `body`。`/sign` 返回 `X-Gorgon`、`X-Khronos`、`X-Argus`、`X-Ladon`、`X-Medusa`、`X-Helios`；改变 URL 或请求体后应重新签名。原 `/verify` 接口保留。

红果 TV 仍经 `taihe.678120.cn:8056` → 飞牛 `192.168.3.77` 上的 `frpc-caiji` → `192.168.3.83:8902` 访问，无需修改客户端或公网转发。

```bash
docker exec hongguo-frida getprop sys.boot_completed
docker exec hongguo-frida getprop init.svc.hongguo-signer
docker exec hongguo-frida /busybox chroot /signer-rootfs /usr/bin/adb -s 127.0.0.1:5555 shell pidof com.phoenix.read
docker inspect --format '{{json .State.Health}}' hongguo-frida
```

所有启动、恢复和 HTTP 日志均写入容器标准输出。Python 服务打包在镜像内，更新源码后需要重新构建镜像。

## 播放取址检查

检查脚本使用 Python 标准库，读取当前设备档案、请求六头签名、调用与客户端相同的 `video_tab/video_model/v` 单集路由，并从 CDN 读取 1024 字节。应选取仍然有效的普通短剧分集 ID；历史下架内容返回错误码不能作为容器可用性的判断。

```bash
python3 tools/verify-playback.py --endpoint http://127.0.0.1:8902 --episode <有效分集ID> --count 3
docker compose restart hongguo
# 等待容器健康后，再执行同样的取址检查。
python3 tools/verify-playback.py --endpoint http://127.0.0.1:8902 --episode <有效分集ID> --count 3
```

## 历史部署

2026-11-04 的旧 ARM 全系统仿真容器曾出现架构执行错误、Binder 缺失、Android 重启流程和看门狗截止时间，随后 ESXi 记录客体 CPU 停止。因此继续采用 Android 原生 x86_64 加 `libndk_translation`，通过 Java `NetworkParams.tryAddSecurityFactor()` 进入 ARM64 App 的算法，不使用 ARM 系统仿真或 native 指令 Hook。

Debian 上旧的 `/opt/hongguo-docker`、旧 ARM 容器和旧数据卷作为存档保留，自动重启已关闭。恢复记录位于 `/root/hongguo-recovery/20261104-190731`，不要恢复旧 ARM 容器与当前端口争用。
