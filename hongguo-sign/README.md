# 红果签名服务：Debian x86 部署

采用 Windows 模拟器已跑通的调用方式：让红果 App 自己生成签名，通过 Frida 调用 Java 方法 `NetworkParams.tryAddSecurityFactor()`。签名服务监听 `8901`，Android 和 ADB 仅在容器网络内访问。

## 技术方案与原部署包的区别

```text
Debian x86_64
  ├─ Android 14 x86_64（仅 64 位）+ libndk_translation：运行 ARM64 红果 App
  └─ Python + Frida x86_64：调用 App 的 Java 签名入口 → HTTP :8901
```

原 `hongguo-docker.zip` 中的 HTTP 服务和 Java 调用代码保留在 `signer/sign_server.py`。整个 Android 改用宿主机原生的 x86_64 架构，ARM64 库交给镜像已包含的 `libndk_translation`。Frida 的架构与 Android ART 进程一致，使用 x86_64。Android 使用仅 64 位的官方镜像；红果 APK 只含 ARM64 库，不需要 32 位 zygote。双 ABI 镜像中曾捕获 Frida 32 位 agent 在 zygote 内超时并触发 SIGTRAP，导致整个 Android 框架重启，因此不保留该路径。

原部署说明把“无法直接 Hook 翻译后的 ARM native 库”推导成“必须仿真整个 ARM64 Android”，这不适用于现有 Java 调用入口。本方案通过 Java 方法进入 App 内部算法，不需要 Frida 对 ARM native 指令执行 Hook。

参考官方文档：

- [Redroid ARM 翻译层说明](https://github.com/remote-android/redroid-doc#native-bridge-support)
- [Redroid Debian 显式 Binder 设备部署](https://github.com/remote-android/redroid-doc/blob/master/deploy/debian.md)

## Debian 环境适配

Debian 自带的内核有 `binder_linux`，即使没有启用 `CONFIG_ANDROID_BINDERFS`，也可以通过显式设备模式运行：

- 创建并挂载 `/dev/binder`、`/dev/hwbinder`、`/dev/vndbinder` 三个设备。
- 使用模块参数和 udev 规则，让设备在开机时恢复，并允许 Android 系统用户访问。
- 保留 Docker 的独立 cgroup 命名空间，只把容器自己的 cgroup2 挂载调整为可写。
- 使用非特权容器，保留 `/proc/sys`、`/proc/sysrq-trigger` 的只读挂载。Android 入口发现 SysRq 保护缺失时会拒绝启动。
- 移除 `SYS_BOOT`、`SYS_MODULE`、`SYS_RAWIO`、`SYS_TIME` 权限，避免 Android 的重启、模块、硬件和时间操作影响 Debian。
- Android 限制为 4 核、4 GB；签名服务限制为 2 核、1 GB。

Android 不需要 KVM，也不需要宿主机的 `qemu-aarch64` 或 `qemu-arm` 注册项。

## 部署

需要 x86_64 Debian、Docker Engine 和 Compose。以 root 操作：

```bash
cd hongguo-sign
bash prepare-host.sh
```

如果旧的 Binder 模块已经加载，但缺少两个额外设备，脚本会保存配置并退出，提示正常重启后重新执行。脚本不强制卸载模块，也不自动重启机器。

放置已使用的红果单 APK，包名 `com.phoenix.read`、版本 `7.3.9.32`：

```bash
mkdir -p apk
cp /已有路径/base.apk apk/base.apk
cp .env.example .env
docker compose up -d --build
```

也可以在 `.env` 中将 `APK_DIR` 指向现有的 APK 目录。APK、Android 用户数据和运行配置不提交到 Git。Android 数据保存在外部卷 `hongguo-native-data` 中，重新创建容器不会清空它。

首次启动红果时，需要在 App 中完成初始引导，签名组件才会初始化。可临时在 `.env` 中设置 `APP_START_MODE=activity`，通过同一 Docker 网络内的 ADB 获取画面和操作；完成后改回 `provider` 并重新创建 signer。后续启动复用数据卷。

正式运行默认使用 `APP_START_MODE=provider`：通过红果自带的 `FileProvider` 执行空的 `call`，让 Android 初始化 Application 和签名组件，全程不打开视频界面，避免无 GPU 环境的 HWUI 纹理崩溃。`activity` 模式使用解析出的 Activity 启动 App，避免依赖 `monkey`。签名服务重启时清理目标 App 和 Frida 的旧进程，保留 App 数据。进程选择优先查找由 zygote 创建的 App 主进程，排除 ARM 翻译层中同名的子进程。

Frida 连接超时采用异步清理，避免 HTTP 超时响应被阻塞。签名服务连续三次不可用时，容器会重新初始化 App 和 Frida，以便从 Android 框架重启或运行时失联中恢复。

启动顺序：Android 完成启动 → 安装 App（已安装则跳过）→ 启动 x86_64 Frida → 启动红果 → 等待初始化 → 启动 HTTP 服务。两个容器使用 `unless-stopped`，Docker 随系统启动后自动恢复。

## 接口

```bash
curl http://192.168.3.83:8901/info
curl http://192.168.3.83:8901/health
curl -X POST http://192.168.3.83:8901/sign \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://api5-normal-sinfonlineb.fqnovel.com/reading/bookapi/search/tab/v?aid=8662&query=test"}'
```

`/sign` 返回 `X-Gorgon`、`X-Khronos`、`X-Argus`、`X-Ladon`、`X-Medusa`、`X-Helios`。签名绑定完整 URL，改变任何查询参数都需要重新生成。

`/health` 会实际调用签名入口，只有具备 `X-Medusa` 和 `X-Helios` 才会报告就绪。原 HTTP 协议和 `/verify` 接口保持兼容；部署过程不自动执行额外的端到端验证脚本。

查看状态和日志：

```bash
docker compose ps
docker compose logs --tail 100 signer
docker exec hongguo-android-native getprop sys.boot_completed
```

## 旧 ARM 容器迁移

2026-11-04 的失联记录显示：旧 ARM Android 容器出现架构执行错误、Binder 缺失、Android 重启流程和看门狗截止时间，随后 ESXi 记录客体 CPU 停止。旧容器使用 `privileged: true`，失去了默认的 SysRq 只读保护。

Debian 上保留原 `/opt/hongguo-docker`、旧容器和旧数据卷，旧容器自动重启已关闭。新的原生部署位于 `/opt/hongguo-sign`，使用独立的 `hongguo-native-data` 数据卷。恢复配置和故障日志保存在 `/root/hongguo-recovery/20261104-190731`。

不要同时启动旧 ARM Compose 与本目录的新部署；两者使用相同的对外端口。新方案不依赖旧数据卷或 ARM 仿真进程。
