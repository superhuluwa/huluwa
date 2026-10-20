# 安卓构建服务

与 `android/` 同级的独立构建服务。网页左侧“安卓 App”提供更新代码、编译 Release APK、取消任务、增量日志和历史 APK 下载。

## 首次部署

在仓库根目录执行：

```bash
docker compose build android-builder
bash android-builder/seed-signing.sh
# 仓库需要认证时，将本机已保存的 Gitee 凭据导入只读凭据卷
bash android-builder/seed-git-credentials.sh
docker compose build huluwa
docker compose up -d --no-build android-builder huluwa
```

随后登录网页进入“安卓 App”，依次点击“更新代码”和“编译 APK”。构建成功后可下载 APK；也可在历史记录下载旧版本。首次构建需要下载 Gradle 和安卓依赖。

`seed-signing.sh` 将现有 `android/keystore.properties` 和其中指定的签名文件复制到签名卷，只在首次导入时写入，不替换已有密钥。默认使用 `android/release.keystore`。备份时应同时备份签名卷和原始签名配置。

## 结构

- `Dockerfile`：JDK 21、SDK 36、Build Tools 35/36、Platform Tools、Git 和 Python 标准库执行环境，支持 x86_64。
- `worker.py`：持久化文件队列、单任务执行、取消、超时、进程组清理、状态和日志归档。
- `android.init.gradle`：向任意选定源码版本注入版本代码及固定 Release 签名。
- `git-askpass.py`：可选的私有仓库凭据读取。

构建服务无监听端口，不挂载 Docker socket。网页服务和构建服务以 UID/GID `10001:10001` 共享任务卷。源码在独立的 bare Git 仓库中更新，每次编译使用固定提交的临时 worktree，不更改网页服务源码。编译结束清理 worktree，保留 Git/Gradle 缓存。Gradle 发行包使用带重试的下载流程，并按工程 Wrapper 固定的 SHA-256 校验，后续编译复用本地缓存。

## 持久化

| Docker 卷 | 内容 |
| --- | --- |
| `huluwa-android-build-data` | 请求、取消标记、任务状态、日志、APK 和 R8 mapping |
| `huluwa-android-workspace` | Git 仓库、Gradle 缓存、临时构建目录 |
| `huluwa-android-signing` | Release 密钥及配置，只读挂载到构建服务 |
| `huluwa-android-credentials` | Gitee 凭据或可选 `git-token` 文件，只读挂载到构建服务 |

任务记录与递增版本代码保存在已有 SQLite 数据库中。每次创建编译任务都会自动分配更大的 `versionCode`，并按 `major × 1000 + minor × 100 + patch` 生成对应的显示版本号，从 `1010 → 1.0.10` 开始，后续为 `1011 → 1.0.11`、`1012 → 1.0.12`。patch 范围为 0–99、minor 范围为 0–9，自动进位，例如 `1099 → 1.0.99`、`1100 → 1.1.0`，无需修改安卓源码中的版本号。即使计数配置缺失，也会参考历史任务的最大版本继续递增；失败或取消的任务不会复用已分配的版本号。常规升级使用 `docker compose up -d`，不要使用 `down -v` 删除上述卷。

## 配置

在根目录 `.env` 设置下列可选项后重新创建服务：

```dotenv
HULUWA_ANDROID_REPOSITORY=https://gitee.com/wangjing71/huluwa.git
HULUWA_ANDROID_BRANCH=master
HULUWA_ANDROID_TIMEOUT_SECONDS=1800
HULUWA_ANDROID_RETENTION=10
HULUWA_ANDROID_CPUS=2
HULUWA_ANDROID_MEMORY=4g
HULUWA_ANDROID_GIT_USERNAME=oauth2
```

Git 仓库须为不含账号密码的 HTTPS 地址。私有仓库将读取令牌写入凭据卷中的 `git-token`，并设置匹配的 Git 用户名。不要把令牌放进 URL。需要认证的 Gitee 仓库推荐执行 `seed-git-credentials.sh`，自动复用本机 Git 保存的账户；凭据仅向准确匹配的 HTTPS 主机提供。匿名可读的仓库无需配置凭据。

网页接口统一位于 `/api/android`，沿用现有 Bearer 登录认证，文件下载同样需要登录。日志每次最多读取 64KiB，前端只保留最近 20 万字符，完整日志可下载。

网页服务重启不会停止编译；构建服务重启后，将中断的任务标记为 `INTERRUPTED`，由用户重新提交。取消和超时会停止整个子进程组。只在 APK 的签名证书、包名、自动分配的显示版本号与版本代码全部符合要求后发布下载。每类任务默认保留最近 10 次，并额外保留最近一次成功安装包。


## 自动发布 App 更新

成功构建并完成签名、包名、版本及文件校验后，APK 自动成为 `/api/android/releases/latest` 的候选版本。服务端按版本代码选择最近可用的安装包，返回任务 ID、版本、文件大小、SHA-256、签名证书摘要、最低系统要求与发布时间；失败、取消和文件缺失的构建不会发布。

安卓 App 的“我的会话 → 在线升级”使用该接口对比手机当前版本，并通过带登录认证的固定任务下载接口获取 APK。服务器构建进程无需在线，已经发布的 APK 仍可用于升级。签名密钥与版本代码计数应随数据一起备份，确保后续版本可以覆盖安装。


## 外网下载

只需穿透葫芦娃的网页服务端口（默认 8000），构建容器不开放端口。网页通过当前访问域名向葫芦娃申请下载链接，再由葫芦娃直接输出 APK；不再先使用 JavaScript 下载整个 Blob 后触发保存。

`POST /api/android/tasks/{id}/download-ticket` 需要现有 Bearer 登录认证，返回固定文件的 10 分钟下载链接；浏览器通过 `/api/public/android-downloads/{ticket}` 直接下载。链接仅允许下载该文件，支持 HEAD、Range 与有效期内重试，不包含账号登录令牌。页面同时显示可点击、可复制的真实下载链接，便于在手机系统浏览器中打开。

手机 App 的在线升级也始终使用登录时填写的葫芦娃服务地址下载，构建容器地址不会发送给手机。
