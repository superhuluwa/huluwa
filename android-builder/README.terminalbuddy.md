# TerminalBuddy 安卓 Docker 打包服务

TerminalBuddy 安卓源码来自 `https://gitee.com/updateme/terminal-buddy.git` 的 `master` 分支，使用其中的 `android/` 工程和原 Release 签名，包名为 `com.terminalbuddy.android`。构建服务以独立 Docker 容器部署在 Debian 13 服务器 **192.168.3.83**，连接葫芦娃主服务 `http://192.168.3.77:8000`。

## 管理入口

新版葫芦娃安卓 App 的「系统会话 → TerminalBuddy App 打包」提供编译签名 Release APK、增量日志、取消、下载日志与下载安装包，界面复用「App 构建管理」。两个入口均只保留一个撑满宽度的「编译 APK」按钮，提交后在所选节点先更新代码，再编译最新提交；更新失败立即停止本次任务，不继续编译旧源码。两款 App 分别保留节点选择、下载状态、任务历史、签名与版本计数。TerminalBuddy 的 APK 不参与葫芦娃在线升级。

后台接口为 `/api/terminalbuddy/android`，路径与原 `/api/android` 构建接口一致。管理和下载沿用葫芦娃登录认证，节点轮询与上传使用本节点密钥；短时下载票据绑定具体项目与任务。主服务将 TerminalBuddy 数据保存到 `/app/data/terminalbuddy-build.db`，产物和日志保存到共享构建卷的 `terminalbuddy/` 子目录。两款 App 共用一个任务执行入口，同一时间只执行一个任务。

TerminalBuddy 版本号独立递增，首次自动分配 `1001 / 1.0.1`，按 `major × 1000 + minor × 100 + patch` 生成版本名；已有源码或历史更高时继续向上分配。失败和取消也不回收编号。

## 供 TerminalBuddy 使用的免登录接口

新增独立入口 `/api/public/terminalbuddy/android`，供 TerminalBuddy 的「我的 → 在线更新」使用，只需服务地址，不需要葫芦娃账号密码，不签发或复用后台令牌。原 `/api/terminalbuddy/android` 管理员入口和葫芦娃其他后台功能继续要求登录。

当前可分享的接口地址（部署包含本改动的后端后生效）：`http://taihe.678120.cn:8086/api/public/terminalbuddy/android`。已有 `frpc-caiji` 仍将公网 8086 映射至 `192.168.3.77:8000`，无需修改打包节点和端口映射。

| 方法与相对路径 | 共享能力 |
| --- | --- |
| `GET /`、`GET /status`、`GET /servers` | 服务标识、构建状态和节点列表 |
| `GET /releases/latest` | 最新 TerminalBuddy Release 元数据 |
| `GET /tasks`、`GET /tasks/{id}`、`GET /tasks/{id}/logs` | 历史任务和增量日志 |
| `POST /build` | 更新源码后编译 TerminalBuddy APK |
| `POST /tasks/{id}/cancel` | 仅取消本设备发起的任务 |
| `POST /tasks/{id}/download-ticket` | 申请 TerminalBuddy APK 或完整日志的短时票据 |
| `GET /downloads/{ticket}` | 只兑换 TerminalBuddy 项目的票据 |

提交和取消使用客户端自动生成的 `X-TerminalBuddy-Client` 随机设备标识，无需用户输入。标识按服务地址隔离；任务发起者保存 SHA-256 摘要，公开历史只显示「共享更新用户」或「管理员」，并返回 `canCancel` 控制取消按钮。不能取消管理员或其他设备发起的任务；共享接口不开放节点创建/删除、节点密钥、代理上传、源码独立更新或葫芦娃 App 构建。公开编译提交间隔为全局 30 秒，继续通过原协调器与葫芦娃构建串行执行。

环境变量 `TERMINALBUDDY_PUBLIC_ENABLED` 默认 `true`，改为 `false` 并重启主服务可关闭该入口。部署时先更新葫芦娃后端，再通过原管理员入口打包和安装新版 TerminalBuddy，然后可将 APK 和接口地址分享给别人。葫芦娃安卓 App、Debian 节点及节点密钥无需更新。

## 葫芦娃后端与 App 更新

新增接口需要部署包含本次改动的葫芦娃后端，系统会话入口需要安装包含本次改动的葫芦娃安卓 App。后端专用 `Dockerfile.backend` 从当前运行镜像复制已归档网页资源，仅打包 Java 后端；不运行网页构建。编译和 APK 生成遵循项目的当次授权规则。

## Docker 部署

部署目录：`/opt/terminalbuddy-android-builder`。先准备已有 `huluwa-android-builder:debian` 基础镜像，再构建 `android-builder/Dockerfile.terminalbuddy`，继承 JDK 21、SDK 36、Build Tools 与 Python 环境，不部署 TerminalBuddy 桌面端或服务端。

```bash
cd /opt/terminalbuddy-android-builder
docker compose -f android-builder/compose.terminalbuddy.yaml build
docker compose -f android-builder/compose.terminalbuddy.yaml up -d --no-build
# 运维命令
docker compose -f android-builder/compose.terminalbuddy.yaml logs -f --tail=100
docker compose -f android-builder/compose.terminalbuddy.yaml stop
```

节点配置 `data/terminalbuddy-builder/config.json` 只读挂载，必须包含主服务地址、节点 ID、密钥，以及以下项目设置：

```json
{
  "apiPath": "/api/terminalbuddy/android",
  "repository": "https://gitee.com/updateme/terminal-buddy.git",
  "branch": "master",
  "packageName": "com.terminalbuddy.android",
  "artifactPrefix": "terminalbuddy"
}
```

路径设置与现有 Debian 远程节点一致：数据 `/build-data`、源码 `/workspace`、签名 `/signing`、JDK `/opt/java/openjdk`、SDK `/opt/android-sdk`。密钥与签名配置只保存在受保护的部署目录和卷中，不放入镜像、命令参数或日志。

| Docker 卷 | 用途 |
| --- | --- |
| `terminalbuddy-android-build-data` | 节点任务、日志和待回传 APK |
| `terminalbuddy-android-workspace` | TerminalBuddy Git 源码与 Gradle 缓存 |
| `terminalbuddy-android-signing` | TerminalBuddy 原 Release 密钥和配置，只读 |
| `huluwa-android-credentials` | 现有 Gitee 凭据，只读 |

默认资源上限为 16 CPU、12GB 容器内存、8 个 Gradle 工作线程、6GB JVM 堆。容器随 Docker 自动启动，主动连接主服务，不开放端口、不挂载 Docker socket。两款 App 的编译串行执行，避免内存争抢；重建容器保留卷，勿执行 `down -v`。
