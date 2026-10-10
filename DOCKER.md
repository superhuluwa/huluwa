# Docker 部署

项目使用 Java 21 / Spring Boot 和 Vue / Vite。Dockerfile 会先构建前端，再将前端静态资源打进后端 JAR，最终由一个容器在 **8089** 端口提供页面和 API，不需要另起 Nginx 或数据库容器。

## 前置条件

- 使用 Linux 容器模式的 Docker Engine / Docker Desktop；使用 Compose 方式时需安装 Docker Compose v2。
- 构建使用 BuildKit，Dockerfile 中包含 npm / Maven 缓存挂载。
- 构建机器需要能访问 Docker Hub、npm registry 和 Maven Central；不需要本机安装 Node.js、Maven 或 Java。
- 容器默认以 UID / GID `10001:10001` 运行，不使用 root。

## 推荐：Docker Compose

在项目根目录执行：

```shell
docker compose up -d --build
docker compose ps
docker compose logs -f huluwa
```

浏览器访问 `http://localhost:8089`。首次启动使用新的数据库时，页面会引导设置管理员密码，没有预置密码。

默认仅绑定 `127.0.0.1`，不对局域网或公网开放。需要远程访问时，建议先完成管理员初始化，再在项目根目录创建 `.env`：

```dotenv
HULUWA_BIND_ADDRESS=0.0.0.0
HULUWA_PORT=8089
```

随后重新执行 `docker compose up -d`。通过服务器 IP 和配置的端口访问；公网部署建议使用带 HTTPS 的反向代理，并限制防火墙访问来源。

查看日志、停止、更新：

```shell
docker compose logs -f huluwa
docker compose down
docker compose up -d --build
```

## 仅使用 Dockerfile

不使用 Compose 时，在项目根目录执行：

```shell
docker build -t huluwa-console:latest .
docker run -d --name huluwa-console --restart unless-stopped --init -p 127.0.0.1:8089:8089 --mount type=volume,source=huluwa-data,target=/app/data huluwa-console:latest
docker logs -f huluwa-console
```

上面的端口同样默认只允许本机访问。完成初始化后，如需远程访问，重建容器并将 `-p 127.0.0.1:8089:8089` 改为 `-p 8089:8089`。

## 数据持久化与已有数据

- SQLite 数据库位于容器内 `/app/data/huluwa.db`，管理员账号、配置和自动生成的令牌密钥都保存在这里。
- Compose 使用项目命名空间内的 `huluwa-data` 命名卷；单独 `docker run` 的例子使用名为 `huluwa-data` 的卷。**这两个卷不相同**，请选择一种启动方式。
- 重建镜像或容器不会清空命名卷。**不要执行 `docker compose down -v` 或删除数据卷，除非明确要清空全部数据。**
- `.dockerignore` 会排除本机 `data/` 和数据库文件，因此已有本机数据不会自动进入镜像，新卷会初始化新的数据库。
- 如需使用已有数据，可将数据目录绑定挂载到 `/app/data`，替代命名卷。先停止旧进程，再迁移数据库及可能存在的 WAL / SHM 文件，避免复制正在写入的 SQLite 数据库；不要同时运行旧进程和新容器。
- Linux 绑定挂载需要确保目录及数据库文件对 UID / GID `10001:10001` 可写。不要通过改为 root 或开启 `--privileged` 来解决普通的数据目录权限问题。

## 可选：查询宿主机 Docker

镜像已包含 Docker CLI，默认**不挂载**宿主机 Docker socket，因此普通控制台功能可用，但“Docker 管理”会显示守护进程不可用。

如果确实需要查询宿主机 Docker，在 Linux Docker 主机上创建 `compose.docker-host.yaml`：

```yaml
services:
  huluwa:
    group_add:
      - "${DOCKER_GID:?请先设置宿主机 Docker socket 的 GID}"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
```

然后在该 Linux 主机执行：

```sh
export DOCKER_GID="$(stat -c '%g' /var/run/docker.sock)"
docker compose -f compose.yaml -f compose.docker-host.yaml up -d --build
```

附加 socket 的实际组 ID，应用仍以非 root 用户运行。上述方法假设使用标准 rootful Docker 的 `/var/run/docker.sock`；rootless Docker 或 Docker Desktop 的 socket 路径与权限可能不同，需要按实际环境配置。

**安全提醒：Docker socket 访问权限相当于宿主机的高权限控制能力。即使当前应用只执行查询，或者将 socket 挂载标记为 `:ro`，也不能把 Docker API 限制成只读。只在可信环境中启用，且不要把管理界面直接暴露到公网。**

## 健康检查与验证说明

容器通过无需登录的 `/api/auth/status` 检查 HTTP 服务和数据库访问，启动等待期为 60 秒。可通过 `docker compose ps` 查看健康状态。

本次仅新增部署配置并做静态检查，未执行 npm / Maven 构建、`docker build`、容器启动或运行时验证。上面的命令是部署时供你执行的步骤，不代表已经构建或启动成功。
