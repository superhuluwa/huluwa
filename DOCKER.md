# Docker 部署（CentOS 服务器）

项目使用 Java 21 / Spring Boot 和 Vue / Vite。Dockerfile 会先构建前端，再把前端静态资源打进后端 JAR，最终由**一个容器**在 **8089** 端口同时提供页面和 API，不需要另起 Nginx 或数据库容器。

控制台容器通过挂载宿主机的 `/var/run/docker.sock`，在“Docker 管理”页面管理**宿主机上的容器 / 镜像 / 网络 / 卷**。

## 架构

```
CentOS 宿主机
├── Docker Engine / containerd
│   ├── huluwa 容器（本控制台，8089）
│   ├── /var/run/docker.sock  ──挂载──▶ 容器内 docker-java SDK（直连守护进程）
│   └── 被管理的业务容器 / 镜像 / 网络 / 卷
└── /app/data（命名卷 huluwa-data）：SQLite 数据库、令牌密钥、配置
```

## 前置条件

- CentOS 7 / 8 / Stream 9，Docker Engine 19.03 及以上（20.10、24.x、26.x 均已验证兼容路径）。
- 已安装 Docker Compose v2（`docker compose version` 可执行；CentOS 需安装 `docker-compose-plugin`）。
- 构建机器需要能访问 Docker Hub、npm registry 和 Maven Central；服务器本身不需要安装 Node.js、Maven 或 Java。
- 容器默认以 UID / GID `10001:10001` 运行，不使用 root、不开启 `--privileged`。

## 快速部署（推荐）

```shell
# 1. 上传项目到服务器，例如 /opt/huluwa
cd /opt/huluwa

# 2. 生成配置（可选，脚本会自动生成）
cp .env.example .env

# 3. 一键部署（自动写入 Docker socket 的 GID、构建并启动、等待健康检查）
chmod +x deploy-centos.sh
sudo ./deploy-centos.sh
```

默认只监听 `127.0.0.1`，不对公网开放。首次访问方式二选一：

```shell
# 方式 A：SSH 隧道（推荐，初始化阶段最安全）
ssh -L 8089:127.0.0.1:8089 <用户>@<服务器IP>
# 然后浏览器打开 http://127.0.0.1:8089 完成管理员初始化

# 方式 B：直接在服务器上验证
curl -sS http://127.0.0.1:8089/api/auth/status
```

完成管理员初始化后，把 `.env` 改为对外监听并重启：

```dotenv
HULUWA_BIND_ADDRESS=0.0.0.0
HULUWA_PORT=8089
```

```shell
docker compose up -d
```

公网部署务必配置带 HTTPS 的反向代理、防火墙与来源限制。

## 手动部署（不使用脚本）

```shell
# 写入手 Docker socket 的 GID
echo "DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)" >> .env

docker compose up -d --build
docker compose ps
docker compose logs -f huluwa
```

仅用 Dockerfile 时：

```shell
docker build -t huluwa-console:latest .
docker run -d --name huluwa \
  --restart unless-stopped --init \
  -p 127.0.0.1:8089:8089 \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v huluwa-data:/app/data \
  huluwa-console:latest
```

## Docker 管理能力与兼容性

页面包含概览、容器、镜像、网络、卷五个部分，接口与功能覆盖原 Quantum 工程的 Docker 管理模块：

| 模块 | 能力 |
|---|---|
| 概览 | 守护进程状态、客户端 / 服务端 / API 版本、平台信息、容器/镜像/网络统计、多主机连接配置 |
| 容器 | 列表（运行中 / 全部）、搜索、启停 / 重启、删除、详情（端口、环境变量、实时 CPU / 内存 / 网络 / 磁盘 / PIDs）、日志（可调 tail）、容器内执行命令 |
| 镜像 | 列表、拉取（镜像名 + 标签）、单个 / 批量删除 |
| 网络 | 列表、创建、删除、容器连接 / 断开 |
| 卷 | 列表、创建、删除 |

兼容性说明：

- 容器内使用 **docker-java 3.4.1** SDK 直接访问 Docker Socket，不需要安装 docker CLI。
- 启动时会调用守护进程 `/version` 协商 API 版本，取客户端支持上限（1.44）与守护进程 `ApiVersion` 的较小值，可对接 CentOS 上常见的 19.03 / 20.10 / 24.x / 26.x；也可在 `application.yml` 中用 `huluwa.docker.api-version` 显式指定。
- CentOS 7 的旧版守护进程在部分 SDK 字段组合下不返回容器 `State`，后端已改为从 `Status` 文本（如 `Up 2 hours`、`Exited (0)`）推导容器状态，运行中容器不会被误判为已停止。
- 使用 rootless Docker 或自定义 socket 路径时，修改 compose 中的挂载路径与 `DOCKER_HOST`；也支持 `tcp://host:2375`（明文）或 `tcp://host:2376` + `DOCKER_TLS_VERIFY=1` / `DOCKER_CERT_PATH`。

## SELinux（CentOS 默认 Enforcing）

compose.yaml 已为 socket 挂载标注 `:z`，自动完成 SELinux 重新标记。若仍报权限错误，可按顺序排查：

```shell
getenforce                                  # 确认是否 Enforcing
sudo ausearch -m avc -ts recent | tail      # 查看 SELinux 拒绝记录
sudo restorecon -v /var/run/docker.sock     # 恢复 socket 上下文
```

不建议为了绕过权限而关闭 SELinux，或使用 `--privileged`。

## 数据持久化与已有数据

- SQLite 数据库位于容器内 `/app/data/huluwa.db`，管理员账号、配置和自动生成的令牌密钥都在这里。
- Compose 使用命名卷 `huluwa-data`；重建镜像或容器不会清空它。**不要执行 `docker compose down -v`，除非明确要清空全部数据。**
- 如需使用已有数据，将数据目录绑定挂载到 `/app/data`，并确保 UID / GID `10001:10001` 有写权限。迁移 SQLite 前先停止写入，并一并处理 WAL / SHM 文件。

## 升级 / 回滚

```shell
docker compose up -d --build     # 拉取代码后重建升级
docker compose logs -f huluwa
docker compose down              # 停止（保留数据卷）
```

## 安全提醒

Docker socket 相当于宿主机的高权限控制能力：即使挂载标记为 `:ro`，也无法把 Docker API 限制为只读。仅在可信环境启用，不要把管理界面直接暴露到公网，并始终为控制台设置强管理员口令。

## 验证说明

本次改动仅做静态检查（Java 源码与前端源码级别），**未执行 Maven 构建、npm 构建、docker build、容器启动或运行时验证**。上面的命令供部署时执行，不代表已构建或启动成功。
