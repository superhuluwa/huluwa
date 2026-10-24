# 安卓构建服务

当前 Debian Docker 打包节点的部署和迁移见 [Debian Docker 打包服务](README.debian.md)。以下 Windows 安装说明保留供旧环境参考。

与 `android/` 同级的独立构建服务。网页左侧“安卓 App”提供更新代码、编译 Release APK、取消任务、增量日志和历史 APK 下载。

## Windows 本机节点与多服务器选择

原来的 Linux 共享卷构建服务保留为“默认服务器”。网页和 Android App 的“App 构建管理”可选择 Windows 或其他远程节点。每个节点独立维护源码、Git 和 Gradle 缓存；任务记录、版本分配、日志、APK 下载和在线升级统一保存在葫芦娃主服务。全局同时执行一个任务，切换节点不会复用版本号。

飞牛 + Windows 的部署方式是一个飞牛主服务连接两个执行节点，而不是在 Windows 另外部署一个正式主服务。两端使用同一仓库和分支；任务、日志、发布和版本号共用飞牛的数据库及产物目录，无需在两台机器间复制数据库。例如飞牛已有 `1.0.43 / 1043`，下一次 Windows 构建分配 `1.0.44 / 1044`，随后飞牛构建分配 `1.0.45 / 1045`。失败、取消也保留已经分配的编号。主服务从现有配置和历史最高版本接续，不导入独立测试主服务的计数器。

主服务在同一持久卷的 `remote-tasks/` 归档远程任务，`servers/{id}/` 保存节点心跳和源码快照；原容器继续使用 `tasks/`，支持滚动升级时的队列隔离。

先更新并重启主服务，保持 `HULUWA_ANDROID_BUILD_ENABLED=true`，构建目录必须可写。旧数据库启动时自动为历史任务补上 `server_id=local`。

1. 登录网页进入“App 构建管理 → 管理构建服务器”，填写名称并添加节点。
2. 保存页面仅显示一次的节点 ID 和密钥。
3. 在这台 Windows 电脑的仓库根目录运行 **Windows PowerShell 5.1**：

```powershell
& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File .\android-builder\install-windows.ps1
```

按提示输入主服务地址（例如 `http://192.168.3.77:8000`）、节点 ID、密钥。也可以通过 `-ServerUrl`、`-NodeId`、`-JavaHome`、`-AndroidHome`、`-SigningDirectory`、`-InstallDirectory` 指定环境。密钥交互输入不回显。默认使用当前用户的 JDK、`%LOCALAPPDATA%\Android\Sdk` 和仓库 `android/` 中已有的 Release 签名；不会生成或替换已有签名。私有 Git 仓库复用当前 Windows 用户的 Git 凭据管理器。

环境要求：Python 3.10+、Git、curl、JDK 21、Android SDK，以及已接受的 Android SDK 许可证。安装程序自动检查并从官方 HTTPS 清单下载缺失的 SDK 36、Build Tools 35.0.0，按清单校验归档后安装，因此首次部署需要联网。若系统 DNS 将 Google 下载域名解析到不可达线路，可用 `-SdkDownloadAddress` 指定自行确认的可达下载地址；域名、TLS 证书校验和官方归档校验继续生效，不修改系统 DNS。Windows 和原服务必须使用同一份 Release 密钥，才能覆盖安装原 App。

安装程序将服务代码、配置和签名复制到 `%LOCALAPPDATA%\HuluwaAndroidBuilder`，限制目录只允许当前用户和 SYSTEM 访问，注册名为 `HuluwaAndroidBuilder` 的后台计划任务。安装后立即启动，当前用户每次登录后自动启动，异常退出一分钟后重试。**这是当前用户的后台计划任务，不是 SCM 系统服务；用户注销期间不会运行。** 不依赖当前源码目录或终端窗口；电脑应保持唤醒。

节点主动访问主服务的 HTTP/HTTPS API，电脑不监听入站端口。公网连接应使用 HTTPS。节点密钥只允许同步本节点任务和上传产物，不能访问其他节点或管理员 API；主服务仅保存密钥摘要。删除节点立即撤销密钥，保留已归档 APK 和任务历史。密钥遗失时删除并重新添加空闲节点，然后重新运行安装脚本；有任务执行时安装程序拒绝覆盖服务。

重新安装并切换节点或主服务地址时，安装程序为新节点使用独立任务目录，保留旧环境的数据；仅在节点 ID 和主服务地址都相同时复用原目录。这可以避免测试任务 ID 与正式环境任务 ID 碰撞。Git 和 Gradle 缓存继续复用。

4. 等节点显示在线，在网页或 App 中选择它，先“更新代码”，再“编译 APK”。更新和编译都使用当前选择的节点；切换节点后需要先更新该节点的源码。
5. APK 完成签名、包名、版本和 SHA-256 校验后回传主服务，完整回传校验通过才发布。下载始终通过主服务；Windows 节点离线后仍可下载已经回传的 APK。

安装后可在仓库根目录双击 `启动Windows打包服务.cmd`，通过现有计划任务在后台启动节点；窗口关闭后服务继续运行。重复点击会检查已有实例，不会重复启动。脚本会验证构建进程心跳，主服务连接状态以 App 或网页显示为准；失败时窗口会显示原因。脚本使用 Windows PowerShell 5.1，复用已安装的配置、签名和缓存，不重新安装服务。现有登录自启设置保持不变。

默认安装目录为 `%LOCALAPPDATA%\HuluwaAndroidBuilder`。使用其他安装目录时执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android-builder\start-windows.ps1 -InstallDirectory "D:\Your Builder"`。

常用运维命令：

```powershell
Get-ScheduledTask -TaskName HuluwaAndroidBuilder
Get-ScheduledTaskInfo -TaskName HuluwaAndroidBuilder
Get-Content "$env:LOCALAPPDATA\HuluwaAndroidBuilder\agent.log" -Tail 50
Start-ScheduledTask -TaskName HuluwaAndroidBuilder
# 停止会中断当前构建；请先在 App 中取消或等待完成。
Stop-ScheduledTask -TaskName HuluwaAndroidBuilder
# 卸载后台启动，保留签名、配置、缓存和历史文件。
Unregister-ScheduledTask -TaskName HuluwaAndroidBuilder -Confirm:$false
```

任务中途停止后，下次启动标记为 `INTERRUPTED`，不会自动重新编译。断网时继续执行本地任务，恢复后按日志字节偏移重传，不重复追加日志。服务端确认 APK 完整且 SHA-256 相符后才接收成功状态。每个 Windows 节点本地保留最近十次完成任务；Git、Gradle 和发行包缓存持续复用。

相关接口（管理接口沿用登录认证）：

- `GET/POST /api/android/servers`：查询 / 新增节点；新增仅返回一次密钥。
- `DELETE /api/android/servers/{id}`：撤销空闲节点。
- `GET /api/android/status?serverId={id}`：选择节点的源码和状态，并返回全部节点列表。
- `POST /api/android/update?serverId={id}`、`POST /api/android/build?serverId={id}`：在指定节点执行；省略参数兼容原来的默认服务器。
- `/api/android/agents/{id}/...`：独立使用 `X-Build-Token` 认证的节点同步接口。

验证命令：

```powershell
.\mvnw.cmd -Dtest=AndroidBuildServiceTest test
python -m unittest discover -s android-builder -p test_worker.py -v
Push-Location frontend
npm.cmd test -- src/api/android.test.js
npm.cmd run build
Pop-Location
```

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
