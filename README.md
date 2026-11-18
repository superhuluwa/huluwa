# 葫芦娃助手（huluwa-console）

自托管的家庭服务器 / NAS 一站式控制台：以**聊天式消息中心**为核心，聚合影视搜索订阅、磁力 / HLS 下载、Docker 管理、Frp 内网穿透管理与消息推送，前后端打包为**单个容器**即可运行。

![技术栈](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green) ![Vue](https://img.shields.io/badge/Vue-3-brightgreen) ![SQLite](https://img.shields.io/badge/数据库-SQLite-blue) ![License](https://img.shields.io/badge/部署-单容器-9cf)

## 功能特性

### 💬 消息中心（WebSocket 实时推送）
类 IM 的会话式界面，发送即受理、结果以机器人气泡实时推送：

| 会话 | 说明 |
| --- | --- |
| 电影港 / 电影天堂 | 发送剧名即搜索，结果按「封面 + 配文」气泡逐条展示，可一键订阅 |
| BT影视（安卓） | 搜索 btbtla.com，高清优先、最新单集在前、全集靠后，可复制磁力或使用电影港默认下载器下载 |
| 磁力下载 | 提交磁力链接到 qBittorrent，`帮助` / `状态` / `路径 xxx` 等指令管理任务 |
| 视频解析 | 解析抖音等分享口令，视频 / 图文作品按气泡逐条推送 |
| 番茄小说 | 发送书名 / 作者搜索，或粘贴番茄 App 的分享链接 / 分享文案，按书号直达阅读页 |
| 迅雷下载 | 对接 Docker 版迅雷的离线下载 |
| 系统通知 | Frp / Docker / 推送等系统事件的统一通知流 |

### 📺 影视订阅与下载
- **剧集订阅 + 定时巡检**：订阅后后台定期巡检详情页，新资源自动推送通知，并有独立的巡检记录页
- **搜索监听**：暂无资源时挂起监听，命中后自动通知并结束
- **HLS（m3u8）订阅下载**：内置 `N_m3u8DL-RE` + FFmpeg，支持离线订阅与单集下载，可随时取消等待中或进行中的任务，完成文件归档到 `下载根目录/剧名/剧名 S01E01.mp4`，可直接被飞牛影视等媒体库识别

### 🐳 Docker 管理
通过 docker-java SDK 直连宿主机 Docker 守护进程（unix socket / tcp），管理宿主机上的**容器 / 镜像 / 网络 / 卷**，并提供资源占用图表。

### 🌐 Frp 管理
与 frpc 容器共用 `frpc.ini`，网页编辑配置，保存后热加载即时生效，支持状态查询与容器重启。

### 🔔 推送设置
系统事件（订阅命中、下载完成、巡检异常等）的推送渠道配置。

## 网页界面

网页端已于 2026-10-23 归档，现有源码和访问入口继续保留。后续默认只维护安卓端与后端；仅在用户明确要求时修改网页端。归档版本与范围见 [网页端归档说明](docs/网页端归档.md)。

网页端与原生安卓端共用同一套「松柏绿 × 雾白」界面：顶部大标题栏 + 底部四个标签（**功能 / 系统 / 下载 / 我的**），
桌面浏览器里内容以手机宽度居中显示，手机浏览器里铺满整屏。

| 标签 | 内容 |
| --- | --- |
| 功能 | 系统通知、电影港、电影天堂、磁力下载、视频解析、迅雷下载等聊天会话 |
| 系统 | Frp 管理、Docker 管理、App 构建管理、**巡检记录**、**剧集管理**、**我的订阅**（本地服务会话，带运行摘要；「在线升级」仅在安卓 App 内提供） |
| 下载 | 离线剧集 / 磁力 / 迅雷【Win】三种来源的下载任务与进度 |
| 我的 | 连接配置、修改密码、退出登录，以及服务器概览 / 推送设置入口 |

「巡检记录」「剧集管理」原本只在网页端提供，现在两端都已作为系统会话实现：巡检记录汇总电影港与电影天堂的巡检历史，
剧集管理扫描共享下载目录、按剧名与季度分组并支持批量删除。「我的订阅」由原底部订阅标签迁入系统会话，
保留电影港与电影天堂订阅的追更状态、巡检时间与资源列表。

## 原生安卓客户端

安卓源码位于同一 Git 仓库的 [`android/`](android/README.md) 目录，使用 Kotlin + Jetpack Compose 实现独立的手机界面，通过现有后端接口管理会话、下载、订阅、Frp、Docker、巡检记录与剧集，默认首页为“功能”会话列表。底部导航为“功能 / 系统 / 下载 / 我的”。

网页「系统 → App 构建管理」可更新源码、编译签名 Release APK、查看日志并下载安装包。独立构建服务位于 [`android-builder/`](android-builder/README.md)，首次部署需导入已有 Release 签名。

使用 Android Studio 打开 `android/` 目录即可导入安卓工程。工程配置和开发说明见 [安卓客户端说明](android/README.md)。

## 架构与 tech stack

```
┌─────────────────────────────────────────────┐
│           huluwa-console 容器 (:8000)        │
│  Vue 3 + Vite + Naive UI（打包进 JAR 托管）   │
│  Spring Boot 3.5（REST + WebSocket）          │
│  SQLite（WAL） │ JWT 认证 │ docker-java SDK   │
└──────┬──────────────┬───────────────────────┘
       │ unix socket  │ 绑定挂载
       ▼              ▼
  Docker 守护进程    /vol1/1000/downloads（与 qB / 迅雷共用下载目录）
```

- **后端**：Java 21、Spring Boot 3.5（Web / WebSocket / JDBC / Validation）、SQLite、jjwt、docker-java
- **前端**：Vue 3、Vite 7、Naive UI、Pinia、Vue Router、ECharts、Axios
- **零外部依赖**：无需 Nginx、无需外部数据库，单个容器同时提供页面与 API

## 快速部署

### 前置条件
- Docker Engine 19.03+ 与 Docker Compose v2
- 构建机器需能访问 Docker Hub / npm registry / Maven Central（服务器本身无需安装 Java、Node）
- 可选：qBittorrent、迅雷、frpc 容器

### 一键部署（CentOS）

```shell
cd /path/to/huluwa
cp .env.example .env   # 按需修改配置
bash deploy-centos.sh
```

Windows 用 `deploy.bat`，飞牛 fnOS 用 `deploy_fnos.bat`（自动处理 TrimACL 目录授权）。详细说明见 [DOCKER.md](DOCKER.md)。

### 手动 compose 部署

```shell
cp .env.example .env
# 获取 docker.sock 的组 ID 填入 DOCKER_GID：stat -c '%g' /var/run/docker.sock
docker compose up -d --build
```

### 关键配置（.env）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `HULUWA_BIND_ADDRESS` | `127.0.0.1` | 监听地址；**完成管理员初始化前保持 127.0.0.1**，之后再改 `0.0.0.0` |
| `HULUWA_PORT` | `8000` | 监听端口 |
| `HULUWA_DATA_DIR` | `./data` | 数据目录（SQLite、令牌密钥、配置），需归 UID 10001 所有 |
| `DOCKER_GID` | `999` | 宿主机 `/var/run/docker.sock` 的组 ID，不填则 Docker 管理页无法连接 |
| `HULUWA_FRP_DIR` | `/root/frp` | frpc 配置目录（飞牛：`/vol1/1000/docker/frpc`） |
| `HULUWA_MEDIA_DIR` | `./data/media` | 旧版本媒体目录，只读挂载保留历史文件 |

### 首次启动

1. 浏览器访问 `http://127.0.0.1:8000`，完成管理员账号初始化
2. 修改 `.env` 将 `HULUWA_BIND_ADDRESS` 改为 `0.0.0.0` 后 `docker compose up -d` 重建
3. 在各会话内配置 qBittorrent / 迅雷 / 视频解析服务等接入信息

## 本地开发

```shell
# 后端（默认 8000 端口，数据落在 ./data）
./mvnw spring-boot:run

# 前端（默认 5173 端口，/api 与 /ws 已代理到后端）
cd frontend && npm install && npm run dev
```

测试与联编：

```shell
./mvnw test                  # 后端测试
cd frontend && npm test      # 前端 vitest
./mvnw -Pfrontend clean package   # 前后端一起构建，产物 target/huluwa-console.jar
```

## 目录结构

```
huluwa/
├── src/main/java/com/huluwa/console
│   ├── web/        # REST 控制器（Auth / Chat / Hls / Docker / Frp / Push ...）
│   ├── ws/         # 聊天 WebSocket 通道与推送
│   ├── service/    # 业务服务（搜索、订阅、HLS 下载、qB / 迅雷、Docker、Frp ...）
│   └── security/   # JWT 令牌与认证拦截
├── src/main/resources
│   ├── application.yml   # 全部可调参数（HLS 工具路径、巡检间隔、Docker 连接 ...）
│   ├── schema.sql        # SQLite 表结构
│   └── static/           # 前端构建产物（由 Vite 生成）
├── frontend/             # 已归档的 Vue 3 网页源码（默认不再修改）
├── android/              # Kotlin + Jetpack Compose 原生安卓客户端
├── scripts/              # HLS 工具安装、下载目录授权等辅助脚本
├── docs/                 # 飞牛影视 m3u8 订阅适配方案
├── Dockerfile / compose.yaml / deploy-centos.sh / deploy.bat / deploy_fnos.bat
└── DOCKER.md             # Docker 部署详细文档
```

## 安全说明

- 默认只监听 `127.0.0.1`，完成管理员初始化后才对外开放
- 容器以非 root 用户 `10001:10001` 运行，不使用 `--privileged`，通过 socket 组权限访问 Docker
- 登录基于 JWT（默认永不过期，`huluwa.token.expire-hours` 可改成正数限时），令牌密钥首次启动自动生成并持久化；修改密码会吊销全部历史令牌

## 相关文档

- [android/README.md](android/README.md) — 原生安卓客户端导入与开发说明

- [DOCKER.md](DOCKER.md) — Docker 部署完整文档（CentOS / 飞牛）
- [docs/飞牛影视-m3u8-订阅适配方案.md](docs/飞牛影视-m3u8-订阅适配方案.md) — m3u8 订阅下载与飞牛影视适配细节
