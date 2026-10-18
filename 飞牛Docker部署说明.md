# 葫芦娃助手 · 飞牛 fnOS Docker 部署说明

本文按当前这台飞牛的实际部署目录编写。项目已改为在飞牛本机直接部署，不再需要外部电脑通过 SSH/scp 上传。通用示例中的 IP、端口和账号请替换为你的环境；路径和挂载关系以第 1 节表格为准。核心目标是：**代码和镜像可以反复更新，SQLite 数据库始终保存在宿主机 `/vol1` 下的独立目录中。**

## 1. 当前部署参数

| 项目 | 宿主机路径 / 值 |
| --- | --- |
| 项目目录 | `/vol1/1000/docker/terminal-buddy/workspace/huluwa` |
| 持久化数据目录 | `/vol1/1000/docker/terminal-buddy/workspace/huluwa/data` |
| 容器内数据目录 | `/app/data` |
| SQLite 数据库 | `.../huluwa/data/huluwa.db` |
| 旧媒体目录 | `.../huluwa/data/media`，只读挂载到容器 `/media` |
| 下载共享目录 | `/vol1/1000/downloads`，可写，容器内保持同一路径 |
| frpc 配置目录 | `/vol1/1000/docker/frpc`，可写，挂载到容器 `/app/frp` |
| Docker socket | `/var/run/docker.sock`，挂载到容器内同路径，需要组权限 |
| 容器名 | `huluwa-console` |
| 镜像名 | `huluwa-console:latest` |
| Compose 项目名 | `huluwa` |
| 对外端口 | `8000` |
| 容器运行用户 | `10001:10001` |
| socket 组 ID | `994`（以飞牛实际值为准） |
| 数据目录属主 | `10001:10001` |
| 下载目录授权对象 | UID `10001`，通过飞牛 TrimACL 授予读写执行权限 |
| frpc 目录属主 | `10001:1001`，目录权限 `2775`，供葫芦娃与 frpc 容器共享 |
| 建议备份根目录 | `/vol1/1000/docker/huluwa-backups`（项目目录外） |

数据目录必须是宿主机绝对路径。当前固定为：

```text
/vol1/1000/docker/terminal-buddy/workspace/huluwa/data
```

不要使用 `./data` 作为生产数据路径。虽然相对路径会解析到项目目录，但换项目位置、换 Compose 工作目录或误用旧脚本时容易挂载到另一个空目录。

## 2. 持久化目录与 `.env`

### 2.1 `.env` 示例

项目根目录的 `.env` 至少应包含以下内容：

```dotenv
HULUWA_BIND_ADDRESS=0.0.0.0
HULUWA_PORT=8000

# 所有业务数据都保存在这个宿主机外部目录
HULUWA_DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data
HULUWA_MEDIA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data/media

# 飞牛 Docker socket 的组 ID，执行 stat -c '%g' /var/run/docker.sock 获取
DOCKER_GID=994
HULUWA_FRP_DIR=/vol1/1000/docker/frpc
```

`.env` 已被 `.gitignore` 和 `.dockerignore` 排除，不会进入 Git 或镜像。不要把宿主机绝对路径写进 Dockerfile。

### 2.2 Compose 绑定挂载

`compose.yaml` 使用以下绑定挂载：

```yaml
volumes:
  - ${HULUWA_DATA_DIR}:/app/data
  - /vol1/1000/downloads:/vol1/1000/downloads
  - ${HULUWA_MEDIA_DIR}:/media:ro
  - /var/run/docker.sock:/var/run/docker.sock:z
  - ${HULUWA_FRP_DIR}:/app/frp
```

绑定挂载（bind mount）的数据存在于宿主机，不随镜像删除、容器删除或 `docker compose down` 消失。**不要执行 `docker compose down -v`**；虽然本项目当前主要使用绑定挂载，但保留该禁忌可以避免以后误加 named volume 后连卷一起删除。

权限责任可以概括为：数据目录让容器用户 `10001:10001` 可写；frpc 目录按项目脚本使用 `10001:1001`、权限 `2775`，让葫芦娃与 frpc 共享；下载目录必须让 UID `10001` 可写；旧媒体目录只读；Docker socket 通过 `group_add: ["${DOCKER_GID:-0}"]` 加入 socket 所属组。

## 3. 首次部署

以下命令在飞牛终端中执行。账号需要能访问 Docker socket；无需 `sudo`，目录属主由临时 root 容器处理。

### 3.1 检查目录和基础环境

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

docker version
docker compose version
stat -c '%g' /var/run/docker.sock
getent passwd 10001 >/dev/null || echo '镜像内 UID 10001 无需存在于宿主机'
```

### 3.2 创建持久化目录

```bash
mkdir -p \
  /vol1/1000/docker/terminal-buddy/workspace/huluwa/data/media \
  /vol1/1000/downloads \
  /vol1/1000/docker/frpc
```

### 3.3 写入 `.env`

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

cp -n .env.example .env
sed -i \
  -e 's#^HULUWA_BIND_ADDRESS=.*#HULUWA_BIND_ADDRESS=0.0.0.0#' \
  -e 's#^HULUWA_PORT=.*#HULUWA_PORT=8000#' \
  -e 's#^HULUWA_DATA_DIR=.*#HULUWA_DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data#' \
  -e 's#^HULUWA_MEDIA_DIR=.*#HULUWA_MEDIA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data/media#' \
  -e "s#^DOCKER_GID=.*#DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)#" \
  -e 's#^HULUWA_FRP_DIR=.*#HULUWA_FRP_DIR=/vol1/1000/docker/frpc#' \
  .env
chown "$(id -u):$(id -g)" .env
chmod 600 .env
```

如果 `.env` 已经存在，`cp -n` 不会覆盖；修改前先备份。`.env` 应由执行 `docker compose` 的部署账号持有并保持 `600` 权限。Compose 客户端会读取它来解析宿主机挂载路径，容器运行用户不需要直接读取该文件。


### 3.4 本机一键部署（推荐）

在飞牛本机进入项目根目录执行：

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa
bash deploy-fnos.sh
```

脚本会自动完成：

- 校验 Docker、Docker Compose v2、`/var/run/docker.sock`
- 根据当前 `.env` 校验并修正数据、媒体、下载、frpc 目录路径
- 自动写入实际 socket GID 到 `.env` 的 `DOCKER_GID`
- 修正数据目录属主为 `10001:10001`
- 修正下载目录 TrimACL / POSIX ACL
- 修正 frpc 共享目录权限
- 构建当前工作区最新源码镜像
- 构建成功后停止旧容器，完整备份 `/app/data`
- 重建容器并等待健康检查
- 校验实际挂载、UID `10001` 写权限、Docker socket、frpc 目录和媒体只读状态
- 新版本失败时尝试恢复旧镜像

只做预检、不构建、不重建：

```bash
bash deploy-fnos.sh --check
```

跳过数据快照、直接部署（不建议）：

```bash
bash deploy-fnos.sh --no-backup
```

部署前先拉取 Git 代码：

```bash
bash deploy-fnos.sh --git-pull
```

默认不执行 `git pull`，只部署当前工作区源码，避免本地改动被自动覆盖。

可用环境变量覆盖路径：

```bash
HULUWA_DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data
HULUWA_MEDIA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data/media
HULUWA_DOWNLOAD_DIR=/vol1/1000/downloads
HULUWA_FRP_DIR=/vol1/1000/docker/frpc
HULUWA_BACKUP_ROOT=/vol1/1000/docker/huluwa-backups
bash deploy-fnos.sh
```

脚本会根据这些值同步写入 `.env` 并校验 Compose 的实际解析结果。部署成功后还会生成 `.deploy-state`，记录本次镜像 ID、容器名和实际目录。

### 3.5 权限设置（完整）

容器以 UID/GID `10001:10001` 运行，不是 root。请分别检查四类权限：

1. `/app/data`：数据库和运行配置，必须可写；
2. `/vol1/1000/downloads`：下载归档目录，UID `10001` 必须可写；
3. `/app/frp`：frpc 配置原子替换目录，葫芦娃用户必须可写，同时 frpc 容器要能读取；
4. `/var/run/docker.sock`：容器通过附加 socket 组访问 Docker 守护进程。

#### 3.5.1 数据目录 `/app/data`

```bash
DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data

sudo chown -R 10001:10001 "${DATA_DIR}"
sudo chmod 755 "${DATA_DIR}"
sudo chmod -R u+rwX,g+rwX "${DATA_DIR}"

stat -c '%n owner=%u:%g mode=%a' \
  "${DATA_DIR}" "${DATA_DIR}/huluwa.db" "${DATA_DIR}/media"
```

预期数据库目录和数据库文件归 `10001:10001`；数据库文件至少允许属主读写，目录至少允许属主进入和写文件。

如果当前账号没有 `sudo`，可以使用一次性 root 容器修正：

```bash
docker run --rm --network none --user 0:0 --entrypoint sh \
  -v /vol1/1000/docker/terminal-buddy/workspace/huluwa/data:/data \
  huluwa-console:latest \
  -c 'chown -R 10001:10001 /data && chmod 755 /data && chmod -R u+rwX,g+rwX /data'
```

#### 3.5.2 下载目录 `/vol1/1000/downloads`

飞牛 `/vol1` 使用自家 TrimACL。正确命令是项目脚本中实际使用的形式：

```bash
sudo bash scripts/hls-download-dir.sh fix huluwa-console
```

该脚本会：

- 检查 `/vol1/1000/downloads` 是否以可写方式挂载到容器同一路径；
- 给下载根目录和第一层剧名目录授予 UID `10001` 的继承读写权限；
- 以容器内 UID `10001` 实际创建、硬链接并清理测试文件。

飞牛 TrimACL 的底层命令为（项目脚本已封装，通常只需使用上一段命令）：

```bash
TRIMACL=/usr/trim/bin/trimacl
for d in /vol1/1000/downloads \
         /vol1/1000/downloads/*/; do
  [ -d "$d" ] || continue
  "$TRIMACL" -m a:u:10001:rwxpdDaAeEc:sfd "$d"
done
```

`sfd` 表示权限作用于目录自身并向文件、子目录继承。不要使用 `setfacl` 代替飞牛的 TrimACL；在 `/vol1` 上 POSIX ACL 不参与飞牛权限判定。

#### 3.5.3 frpc 配置目录 `/app/frp`

frpc 配置保存采用原子替换。葫芦娃容器用户 `10001` 必须能写目录，并且要能替换 `frpc.ini`；frpc 容器同时需要读取该文件。项目脚本采用的共享权限如下：

```bash
FRP_DIR=/vol1/1000/docker/frpc
sudo mkdir -p "${FRP_DIR}"
sudo chown 10001:1001 "${FRP_DIR}"
# 2775 = setgid + 属主/组可写，保持 frpc 配置目录可被两个容器共享
sudo chmod 2775 "${FRP_DIR}"
sudo chown 10001:1001 "${FRP_DIR}/frpc.ini" 2>/dev/null || true
sudo chmod 664 "${FRP_DIR}/frpc.ini" 2>/dev/null || true
```

这里使用 `10001:1001` 是项目脚本与当前飞牛部署的实际约定，不是笔误。如果飞牛上 frpc 容器运行组不同，以 `docker inspect frpc --format '{{.Config.User}}'` 和实际部署用户为准调整，但不要使用 `777`。

#### 3.5.4 Docker socket `/var/run/docker.sock`

先读取飞牛实际 GID：

```bash
stat -c 'socket owner=%U(%u) group=%G(%g) mode=%a' /var/run/docker.sock
SOCKET_GID=$(stat -c '%g' /var/run/docker.sock)
printf 'DOCKER_GID=%s\n' "$SOCKET_GID"
```

把结果写入 `.env`：

```bash
sed -i "s#^DOCKER_GID=.*#DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)#" .env
```

`compose.yaml` 会把这个 GID 通过 `group_add` 注入容器。重建后验证：

```bash
docker inspect huluwa-console --format '{{json .HostConfig.GroupAdd}}'
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -r /var/run/docker.sock && test -w /var/run/docker.sock'
```

容器内 UID `10001` 能够读写 socket，说明 `DOCKER_GID` 配置正确。若 socket GID 变化，必须更新 `.env` 并重建容器，单纯 `restart` 不会改变 `group_add`。

#### 3.5.5 旧媒体目录 `/media`（只读）

旧媒体目录是兼容历史文件，必须保持只读。`compose.yaml` 中已经写了 `:ro`，不要改成可写：

```bash
docker inspect huluwa-console \
  --format '{{range .Mounts}}{{if eq .Destination "/media"}}{{.Source}} RW={{.RW}}{{end}}{{end}}'
```

预期输出中的 `RW=false`。如果改成可写，历史媒体可能被误删或覆盖。


### 3.6 构建并启动

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa
# 再次确认挂载变量解析正确
docker compose config | sed -n '/volumes:/,/networks:/p'
docker compose build --pull
# 首次部署 / 更新后使用 --build，确保 latest 镜像 ID 被重新解析
docker compose up -d --build --force-recreate
docker compose ps
```

查看日志和健康状态：

```bash
docker logs --tail 100 -f huluwa-console
docker inspect huluwa-console \
  --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}'
curl -fsS http://127.0.0.1:8000/api/auth/status
```

浏览器访问：

```text
http://<飞牛局域网 IP>:8000
```

## 4. 确认数据确实挂载在外部

这一步应在首次部署后和每次更新后执行：

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

docker inspect huluwa-console \
  --format '{{range .Mounts}}{{println .Source " -> " .Destination " RW=" .RW}}{{end}}'

ls -ln data/huluwa.db
sha256sum data/huluwa.db
docker exec huluwa-console sha256sum /app/data/huluwa.db
```

预期必须能看到：

```text
/vol1/1000/docker/terminal-buddy/workspace/huluwa/data -> /app/data RW=true
```

宿主机和容器内数据库的 SHA-256 也应一致。只要挂载源不是项目中的 `data/`，就应停止操作并检查 `.env`。

再做一次完整权限验证：

```bash
# 数据目录可写
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -w /app/data && test -w /app/data/huluwa.db && echo data-writable'

# 挂载可写（必须以 RW=true 显示）
docker inspect huluwa-console \
  --format '{{range .Mounts}}{{if eq .Destination "/app/data"}}{{.Source}} RW={{.RW}}{{end}}{{end}}'

# 下载目录可写
sudo bash scripts/hls-download-dir.sh check huluwa-console

# frpc 目录可写
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -w /app/frp && echo frp-writable'

# 旧媒体目录必须只读，写入测试应失败
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -r /media && ! test -w /media && echo media-readonly'

# Docker socket 可读写
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -r /var/run/docker.sock && test -w /var/run/docker.sock && echo docker-socket-writable'

# socket 组配置
docker inspect huluwa-console \
  --format 'group_add={{json .HostConfig.GroupAdd}}'
```

任何一行失败都应先修复权限，再继续使用下载、frpc 或 Docker 管理功能。

## 5. 更新最新代码（保留数据）

更新只替换镜像和容器，不替换宿主机数据目录：

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

# 1. 记录当前版本和镜像，便于回滚
docker inspect huluwa-console > /tmp/huluwa-container-before-update.json
docker image inspect huluwa-console:latest > /tmp/huluwa-image-before-update.json
docker tag huluwa-console:latest huluwa-console:rollback

# 2. 构建新镜像；此阶段旧容器继续运行
#    如果直接使用 compose 也可以在本步骤改为 docker compose build。
docker build -t huluwa-console:latest .

# 3. 数据一致性备份（必须停掉正在写库的容器）
docker compose stop
BACKUP_ROOT=/vol1/1000/docker/huluwa-backups
mkdir -p "${BACKUP_ROOT}"
STAMP=$(date +%Y%m%d-%H%M%S)
docker cp -a huluwa-console:/app/data \
  "${BACKUP_ROOT}/${STAMP}-data"
tar -C "${BACKUP_ROOT}" \
  -czf "${BACKUP_ROOT}/${STAMP}-data.tar.gz" \
  "${STAMP}-data"

# 4. 重建容器
#    已经在上一步构建过镜像，这里不需要再次 --build；
#    但必须强制重建并确认新容器 ID 的镜像与 latest 一致。
#    如果改用 --build，也可以把第 2 步的 docker build 去掉。
docker compose up -d --force-recreate


# 5. 检查健康状态和挂载
docker compose ps
docker inspect huluwa-console \
  --format '{{range .Mounts}}{{println .Source " -> " .Destination " RW=" .RW}}{{end}}'

docker inspect huluwa-console --format 'image={{.Image}}'
docker image inspect huluwa-console:latest --format 'latest={{.Id}}'

# 6. 再次验证数据与关键权限
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -w /app/data && test -w /app/data/huluwa.db && echo data-writable'
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -w /app/frp && echo frp-writable'
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -r /var/run/docker.sock && test -w /var/run/docker.sock && echo socket-writable'
```

两行输出的镜像 ID 必须一致。更新前后不要删除 `data/` 目录。`docker compose build` / `docker build` 不会写入绑定挂载目录，`docker compose up -d --force-recreate` 也只会替换容器。

## 6. 备份

### 6.1 在线快速备份（不推荐作为唯一备份）

运行中的 SQLite 可能还有 WAL/SHM 文件或正在写入。在线文件级 `cp` 只能作为临时快照，不能替代停机一致性备份。

优先使用第 6.2 节的停机备份。

### 6.2 推荐：停机一致性备份

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa
BACKUP_ROOT=/vol1/1000/docker/huluwa-backups
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "${BACKUP_ROOT}"

docker compose stop
docker cp -a huluwa-console:/app/data "${BACKUP_ROOT}/${STAMP}-data"
tar -C "${BACKUP_ROOT}" -czf "${BACKUP_ROOT}/${STAMP}-data.tar.gz" "${STAMP}-data"

docker compose start
```

恢复前必须先停止容器，并同时恢复 `huluwa.db`、`huluwa.db-wal` 和 `huluwa.db-shm`（如果存在）。不能只恢复主数据库文件而留下旧 WAL/SHM。

备份时应保存整个 `/app/data` 目录，而不是只保存 `huluwa.db`。

## 7. 从备份恢复

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

# 先保留当前数据，避免恢复出错后无法回退
BACKUP_ROOT=/vol1/1000/docker/huluwa-backups
STAMP=$(date +%Y%m%d-%H%M%S)
docker compose stop
cp -a data "${BACKUP_ROOT}/${STAMP}-data-before-restore"
tar -xzf "${BACKUP_ROOT}/<备份时间>-data.tar.gz" -C /tmp

# 删除旧库及其 WAL/SHM，避免旧事务日志污染恢复后的数据库
rm -f data/huluwa.db data/huluwa.db-wal data/huluwa.db-shm

# 将解压目录名按实际备份调整，恢复整个目录
cp -a "/tmp/<解压目录>/." data/
sudo chown -R 10001:10001 data
sudo chmod 755 data

# 启动并检查
python3 - <<'PY'
import sqlite3
p='data/huluwa.db'
con=sqlite3.connect(f'file:{p}?mode=ro', uri=True)
print('integrity_check=' + con.execute('pragma integrity_check').fetchone()[0])
con.close()
PY
docker compose start
docker logs --tail 100 -f huluwa-console
```

恢复完成后核对：

```bash
docker exec huluwa-console \
  sh -c 'test -r /app/data/huluwa.db && echo db-readable || exit 1'
```

## 8. 回滚镜像

如果新版本应用启动失败，而数据目录仍然存在，可以回到更新前镜像：

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa
# 记录当前回滚标签的实际镜像 ID，避免 tag 被覆盖后无法确认
docker image inspect huluwa-console:rollback --format 'rollback={{.Id}}'
docker compose down
docker tag huluwa-console:rollback huluwa-console:latest
docker compose up -d --force-recreate --no-build
docker inspect huluwa-console --format 'container={{.Image}}'
docker image inspect huluwa-console:rollback --format 'rollback={{.Id}}'
```

`docker compose down` 只删除容器和网络，不删除绑定挂载的 `data/`。不要加 `-v`。最后两个镜像 ID 应一致；如果不一致，说明 Compose 没有采用回滚镜像，需要检查 `compose.yaml` 的 `image` 配置和当前目录。

## 9. 数据目录权限故障

如果日志出现：

```text
SQLITE_READONLY: Attempt to write a readonly database
```

说明容器无法写 `/app/data`，不是数据库损坏。检查：

```bash
DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data
stat -c '%n %u:%g %a' \
  "${DATA_DIR}" "${DATA_DIR}/huluwa.db" "${DATA_DIR}/media"

docker inspect huluwa-console \
  --format '{{range .Mounts}}{{println .Source " -> " .Destination " RW=" .RW}}{{end}}'
```

修正：

```bash
DATA_DIR=/vol1/1000/docker/terminal-buddy/workspace/huluwa/data
sudo chown -R 10001:10001 "${DATA_DIR}"
sudo chmod 755 "${DATA_DIR}"
sudo chmod -R u+rwX,g+rwX "${DATA_DIR}"
docker compose up -d --force-recreate --no-build
docker exec --user 10001:10001 huluwa-console \
  sh -c 'test -w /app/data && test -w /app/data/huluwa.db && echo data-writable'
```

其他常见权限问题的对应处理：

- `data-writable` / `data-readable` 检查失败：见第 3.5.1 节。
- 下载目录提示 `TrimACL`、`POSIX ACL` 或 UID `10001` 不可写：执行 `sudo bash scripts/hls-download-dir.sh fix huluwa-console`。
- frpc 保存失败或原子替换失败：检查 `/vol1/1000/docker/frpc` 是否为 `10001:1001`、目录权限 `2775`，并确认 frpc 容器能读取。
- Docker 管理页提示无法连接守护进程：检查 `/var/run/docker.sock` 的实际 GID、`.env` 的 `DOCKER_GID` 以及容器 `GroupAdd`。
- 媒体文件只能读不能改：`/media` 是刻意设置的只读兼容挂载，这是预期行为。

如果当前账号可以访问 Docker socket，不需要 `sudo`；脚本会自动使用临时 root 容器处理属主。

## 10. 日常维护命令

```bash
cd /vol1/1000/docker/terminal-buddy/workspace/huluwa

# 状态
docker compose ps
docker inspect huluwa-console \
  --format 'status={{.State.Status}} health={{.State.Health.Status}}'

# 日志
docker compose logs --tail 200 -f

# 更新代码后构建并重建（确保 latest 镜像 ID 被 Compose 重新解析）
docker compose up -d --build --force-recreate

# 只重启
docker compose restart

# 停止但保留数据
docker compose stop

# 删除容器但保留数据和镜像
docker compose down
```

## 11. 关键注意事项

1. `HULUWA_DATA_DIR` 必须是绝对路径。
2. `/app/data` 必须绑定到宿主机 `data/` 目录，而不是 anonymous volume。
3. 容器运行用户为 `10001:10001`；数据目录、下载目录和 frpc 目录都必须满足对应权限，frpc 共享目录按脚本使用 `10001:1001`。
4. 飞牛 `/vol1` 的下载授权应使用 `trimacl`，项目脚本是首选入口。
5. `.env` 的 `DOCKER_GID` 必须等于 `/var/run/docker.sock` 的实际组 ID，修改后需要重建容器。
6. 更新前先备份，更新后核对挂载源和数据库哈希。
7. 不要执行 `docker compose down -v`。
8. 不要在容器运行时直接复制只有主库文件而漏掉 WAL/SHM。
9. 不要把 `data/` 提交到 Git，也不要把数据复制进源码目录后再构建成镜像。
10. `deploy-fnos.sh` 已改为飞牛本机模式，默认使用脚本所在项目目录；不需要 `HULUWA_FNOS_APP_DIR`，也不通过 SSH/scp 上传。
