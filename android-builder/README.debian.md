# Debian Docker 打包服务

葫芦娃 Android App 的「App 构建管理」通过远程节点连接 Debian Docker 打包服务。主服务继续统一保存版本号、构建历史、日志和 APK；构建容器主动连接主服务，不开放端口，也不挂载 Docker socket。容器采用 Debian 12（bookworm）、JDK 21、SDK 36 和 Build Tools 35/36，支持 x86_64。

## 部署

Debian 主机需要 Docker Engine、Docker Compose 插件和 Python 3.10+。先准备葫芦娃主服务分配的节点 ID、密钥和原 Release 签名。已有 Windows 节点迁移时应保留节点 ID 与任务历史，撤销旧密钥，并将节点名称改为「Debian Docker 打包」；不要让 Windows 与 Debian 同时使用同一节点密钥。

在仓库根目录执行：

```bash
# 构建 Debian 执行环境，不编译 App。
docker compose -f android-builder/compose.debian.yaml build
# 首次在这台 Docker 主机部署时，导入原 Release 签名和已有 Git 凭据。
# 两个导入脚本默认使用共享构建服务镜像，因此指定 Debian 镜像。
HULUWA_ANDROID_BUILDER_IMAGE=huluwa-android-builder:debian bash android-builder/seed-signing.sh
HULUWA_ANDROID_BUILDER_IMAGE=huluwa-android-builder:debian bash android-builder/seed-git-credentials.sh
# 匿名仓库可省略凭据导入，但仍需创建 Compose 使用的凭据卷：
# docker volume create huluwa-android-credentials
python3 android-builder/configure-debian.py
# 按提示输入主服务地址、节点 ID、密钥；密钥不回显。
docker compose -f android-builder/compose.debian.yaml up -d --no-build
```

主服务同在本机且已发布 8000 端口时，地址可填 `http://host.docker.internal:8000`；其他主机填写可达的主服务地址，公网连接使用 HTTPS。主服务不能只绑定 `127.0.0.1`，否则容器无法通过宿主机网关连接。

配置保存在 Git 忽略的 `data/debian-builder/config.json`，只读挂载到容器。配置目录仅部署用户可访问，文件授权给容器 UID 10001 和部署用户所在组；不要复制密钥到仓库或日志。重复配置会复用同一主服务、同一节点的密钥；轮换密钥时先停止容器，将旧配置移到受保护的备份目录后重新配置。

已有主机签名卷会继续复用，不生成或替换签名。同一 Docker 主机上的默认构建服务与 Debian 节点共用只读签名、凭据卷，分别保存任务与编译缓存。不同 Docker 主机需要分别导入相同的 Release 签名。

## App 使用与运维

完成首次部署后，可在仓库根目录运行 `bash 启动Debian打包服务.sh`，启动或复用已配置的容器。

App 的「App 构建管理」选择「Debian Docker 打包」，依次点击「更新代码」「编译 APK」。节点名称由主服务提供，迁移时保留节点 ID，App 已保存的选择仍然有效。节点首次启动需更新本节点源码。

```bash
# 查看节点日志
docker compose -f android-builder/compose.debian.yaml logs -f --tail=100
# 重启节点；执行中的任务应先等待完成或在 App 中取消。
docker compose -f android-builder/compose.debian.yaml restart
# 更新构建服务代码并重建容器，不触发 APK 编译。
docker compose -f android-builder/compose.debian.yaml build
docker compose -f android-builder/compose.debian.yaml up -d --no-build
# 停止服务，保留全部卷。
docker compose -f android-builder/compose.debian.yaml down
```

容器随 Docker 自动启动，退出后自动重启，不依赖用户登录。停止时向 worker 转发终止信号，以便清理 Gradle 子进程。重启后原任务标记为中断，不会自动重跑；断网后恢复时按日志字节偏移继续同步，APK 回传主服务后可持续下载。

| 持久化位置 | 内容 |
| --- | --- |
| `huluwa-android-debian-data` | 按主服务和节点隔离的任务、日志、结果、心跳 |
| `huluwa-android-debian-workspace` | 按节点隔离的 Git 仓库、共享 Gradle 缓存 |
| `huluwa-android-signing` | 原 Release 签名与配置，只读 |
| `huluwa-android-credentials` | Git 凭据，只读 |
| `data/debian-builder/config.json` | 主服务地址、节点 ID 与密钥，只读 |

Compose 支持 `HULUWA_ANDROID_CPUS`、`HULUWA_ANDROID_MEMORY` 调整资源，支持 `HULUWA_ANDROID_AGENT_CONFIG` 指定配置文件绝对路径。修改后用相同变量重新创建容器。切换主服务或节点会隔离任务目录，避免任务 ID 碰撞；不要用 `down -v` 清除卷。
