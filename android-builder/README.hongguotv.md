# 红果TV App 打包服务

红果TV 原生源码来自 `https://github.com/N3urda/hongguoTV.git` 的 `main` 分支，使用仓库中的 `kotlin-tv/` 工程，包名为 `com.hongguotv.nativeapp`。构建服务以独立 Docker 容器部署，与葫芦娃、TerminalBuddy 共用 Debian 打包机（192.168.3.83），并共用主服务的串行构建队列。

## 管理入口

葫芦娃安卓 App 的「系统会话 → 红果TV App 打包」提供编译签名 Release APK、增量日志、取消、下载日志与下载安装包，界面复用「App 构建管理」。点击「编译 APK」后，节点先更新源码，再编译最新提交；更新失败会立即停止，不继续编译旧源码。

后台接口为 `/api/hongguotv/android`，路径与原 `/api/android` 构建接口一致。管理与下载沿用葫芦娃登录认证，节点轮询与上传使用本节点密钥；短时下载票据绑定具体项目与任务。主服务将红果TV 数据保存到 `/app/data/hongguotv-build.db`，产物与日志保存到共享构建卷的 `hongguotv/` 子目录。

版本号沿用 TerminalBuddy 规则：独立递增，首次自动分配 `1001 / 1.0.1`，按 `major × 1000 + minor × 100 + patch` 生成版本名；已有源码或历史更高时继续向上分配。失败和取消也不回收编号。

> 注意：版本号从 1001 起分配，低于红果TV 官方渠道的 `10000 + 构建序号`。如果电视上已安装官方渠道的更高版本，本服务生成的 APK 不能覆盖安装，需要先卸载旧版，或后续按需调整版本号规则。

## 源码差异适配

红果TV 的工程与葫芦娃 / TerminalBuddy 有几处结构性差异，worker 已按 `appDirectory` 与 `appName` 区分：

| 差异 | 葫芦娃 / TerminalBuddy | 红果TV |
| --- | --- | --- |
| 应用工程目录 | 仓库根 `android/` | 仓库 `kotlin-tv/` |
| 签名变量 | `HULUWA_*`（初始化脚本） | `HONGGUOTV_*`（源码自带） |
| 版本变量 | `HULUWA_VERSION_*` | `HONGGUOTV_VERSION_*` |
| Gradle Wrapper | 固定 `distributionSha256Sum` | 源码未提供，由节点配置 `gradleSha256` 补充 |
| JDK 工具链 | 无额外要求 | `core` 模块要求 JDK 17，镜像内预装 `openjdk-17-jdk-headless` 供 Gradle 自动检测 |
| 编译任务 | `:app:assembleRelease` | `:core:test :app:lintRelease :app:assembleRelease` |

红果TV 保留自己的 `:core:test` 与 `lintRelease` 门槛，与官方发布流程一致；测试或 lint 失败即整体失败。

## Release 签名

仓库内置了红果TV 专属的 Release 签名（`hongguotv/android/release.keystore` 与 `keystore.properties`），随源码一起提交，便于任何机器复现同证书的安装包。当前证书 SHA-256：

```text
b1ace1e3563a05724e8f813604101b47c2373e88e699a21812939259200914cc
```

构建时签名经只读卷 `hongguotv-android-signing` 提供，不写入镜像、命令参数或日志。首次部署导入：

```bash
cd /opt/hongguotv-android-builder
HULUWA_ANDROID_BUILDER_IMAGE=hongguotv-android-builder:latest bash android-builder/seed-hongguotv-signing.sh
```

`seed-hongguotv-signing.sh` 默认读取仓库内 `hongguotv/android/`，也可传入其他目录作为参数；已存在的完整签名卷不会被覆盖。

> 该证书与红果TV 官方渠道证书（`3579f7a8…ca98`）不同。电视上若已装官方版，需要卸载后重装，或改用官方 keystore 替换签名卷后重新编译。

## Docker 部署

部署目录：`/opt/hongguotv-android-builder`（位于 Debian 打包机 192.168.3.83）。先准备 `huluwa-android-builder:debian` 基础镜像，再构建 `android-builder/Dockerfile.hongguotv`；后者在基础镜像上补装 JDK 17，供红果TV 的 `core` 模块工具链使用。

节点配置中的 `serverUrl` 填 `http://192.168.3.77:8000`（主服务在飞牛 NAS，打包机通过局域网访问），与 TerminalBuddy 节点保持一致。不要填 `host.docker.internal`，那是主服务本机容器内的地址。

```bash
cd /opt/hongguotv-android-builder
# 导入签名（只需一次）
bash android-builder/seed-hongguotv-signing.sh
# 配置主服务地址、节点 ID 与密钥（密钥不回显）
python3 android-builder/configure-hongguotv.py
# 构建并启动
docker compose -f android-builder/compose.hongguotv.yaml build
docker compose -f android-builder/compose.hongguotv.yaml up -d --no-build
# 运维命令
docker compose -f android-builder/compose.hongguotv.yaml logs -f --tail=100
docker compose -f android-builder/compose.hongguotv.yaml stop
```

节点配置 `data/hongguotv-builder/config.json` 只读挂载。关键字段：

```json
{
  "apiPath": "/api/hongguotv/android",
  "repository": "https://github.com/N3urda/hongguoTV.git",
  "branch": "main",
  "packageName": "com.hongguotv.nativeapp",
  "artifactPrefix": "hongguotv",
  "appDirectory": "kotlin-tv",
  "appName": "hongguotv",
  "gradleSha256": "bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531"
}
```

`gradleSha256` 是 Gradle 8.14.3 官方发行包的 SHA-256，用于校验下载和已有缓存；红果TV 源码未在 Wrapper 中固定该值。节点每次编译前会把 `distributionUrl` 改写为已验证的本地缓存，不再走网络下载器。

红果TV 源码仓库为公开仓库，不需要 Gitee 凭据卷。路径设置与现有 Debian 远程节点一致：数据 `/build-data`、源码 `/workspace`、签名 `/signing`、JDK `/opt/java/openjdk`、SDK `/opt/android-sdk`。

| Docker 卷 | 用途 |
| --- | --- |
| `hongguotv-android-build-data` | 节点任务、日志和待回传 APK |
| `hongguotv-android-workspace` | 红果TV Git 源码、Gradle 缓存与自动下载的 JDK 17 |
| `hongguotv-android-signing` | 红果TV Release 密钥和配置，只读 |

默认资源上限为 16 CPU、12GB 容器内存、8 个 Gradle 工作线程、6GB JVM 堆。容器随 Docker 自动启动，主动连接主服务，不开放端口、不挂载 Docker socket。三款 App 的编译串行执行，避免内存争抢；重建容器保留卷，勿执行 `down -v`。

## 后端与 App 更新

新增接口需要部署包含本次改动的葫芦娃后端，系统会话入口需要安装包含本次改动的葫芦娃安卓 App。后端专用 `Dockerfile.backend` 从当前运行镜像复制已归档网页资源，仅打包 Java 后端；不运行网页构建。编译和 APK 生成遵循项目的当次授权规则。
