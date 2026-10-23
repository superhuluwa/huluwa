# Windows Android 构建服务验收记录

验收日期：2026-10-23。工作区：`C:\Users\Jing\Desktop\huluwa`。

本文记录最初的本机隔离验收。随后已接入飞牛正式主服务，正式环境结果见 [飞牛与 Windows 共用构建服务验收](fnos-windows-shared-builder-validation.md)。

## 实现范围

- Android App 与网页的 App 构建管理支持选择服务器；更新源码、编译和状态查询使用所选节点，历史记录显示任务所属服务器。
- 原 Linux 构建节点保留为默认服务器；Windows 节点主动轮询主服务，无需向 Windows 开放入站端口。远程任务使用 `remote-tasks/`，与原容器队列隔离。
- 主服务统一管理版本号、任务、日志和产物。节点使用独立密钥；完成上传并校验 APK 后才发布，节点离线仍可下载已发布 APK。
- Windows 适配包含批处理启动、带空格路径、日志读取、签名、SDK 检查、进程树取消、超时、断线恢复和本地缓存。

## 本机部署状态

- 安装目录：`C:\Users\Jing\AppData\Local\HuluwaAndroidBuilder`。
- 后台计划任务：`HuluwaAndroidBuilder`，验收结束时为 `Running`。
- 当前用户登录后自动启动，异常退出一分钟后重试。这是当前用户后台计划任务，并非 Windows SCM 系统服务；用户注销期间不运行。
- Python 3.10，JDK 21.0.12，Android SDK 36、Build Tools 35.0.0。缺失 SDK 已实际安装；Gradle 8.13 发行包已校验并缓存。
- 当前连接隔离测试主服务 `http://127.0.0.1:18000`，管理页面为 `http://127.0.0.1:18000/#/android`。测试主服务仅监听本机，尚未配置自动启动。
- 配置、节点密钥及 Release 签名保存在安装目录，目录限制当前用户和 SYSTEM 访问。本文不包含凭据。

安装与运维步骤见 [android-builder/README.md](../android-builder/README.md)。

## 自动化验证

| 范围 | 结果 |
| --- | --- |
| Python Windows worker | 7 项通过，包含子进程取消、超时、带空格批处理路径和节点队列隔离 |
| Java 后端全量 | 共 154 项，0 失败、0 错误、1 项因 Windows 符号链接权限跳过 |
| 构建服务专项 | 6 项通过，覆盖路由、版本分配、鉴权、日志重传、产物校验、历史迁移；远程队列路径调整后已重跑 |
| 网页 | 13 个测试文件、76 项通过；Vite 生产构建成功，静态资源已更新 |
| Android Release 单元测试 | 38 项通过 |

后端测试使用 Mockito 显式 Java agent，避免本机 JDK 动态附加不稳定。日志保留在受 Git 忽略的 `data/windows-builder-test/`；Android 测试结果位于 `android/app/build/test-results/testReleaseUnitTest/`。

## 真实构建与故障恢复验证

1. Windows 节点真实拉取 Gitee 源码成功。随后使用包含本次 Android 改动的隔离 Git 快照构建，未提交或推送主工作区。
2. 真实 Release 编译约 3 分 17 秒完成，签名、包名和版本校验通过，APK 上传主服务成功。
3. 编译期间重启主服务，Windows 编译继续完成，恢复连接后同步日志和产物。
4. 停止 Windows 节点并等待主服务判定离线，APK 仍可下载。最新版本查询、认证下载、下载票据、无需登录的票据 HEAD、Range 请求均通过；`bytes=0-1023` 返回 206 且内容一致。
5. 未登录管理接口及错误节点密钥均返回 401。
6. 新任务分配版本代码 1012，取消后 3.78 秒内变为 `CANCELLED`，最新发布仍为已成功的 1011。远程任务未进入原 Linux `tasks/` 队列。
7. 浏览器验收：Windows 节点在线、源码可用、签名已配置、任务空闲，构建历史与 APK 下载正常。

下载和取消验证记录分别保留在 `data/windows-builder-test/download-validation.json`、`cancel-validation.json`。

## 验收 APK

| 项目 | 值 |
| --- | --- |
| 文件 | `output/windows-builder-validation/huluwa-1.0.11-1011-6f65b350.apk` |
| 包名 | `com.huluwa.android` |
| 版本 | `1.0.11` / `1011` |
| 最低 Android API | 26 |
| 大小 | 3,515,447 字节 |
| 源码快照 | `6f65b3504d88854502b55e1efcc08f74e21df40c` |
| APK SHA-256 | `cbeace718903ab7a96e006c531684a636b1b56ae37add2004b7157d7a9b89284` |
| 签名证书 SHA-256 | `c69d80918b2140585d1e21f83da96617e5bd09e9e84b94cfa57752a4c4033047` |

界面截图：`output/windows-builder-validation/build-management.jpg`。

## 正式接入与验收边界

- 现有葫芦娃主服务地址尚未提供，原服务器尚未部署本次后端改动。当前完整链路在本机隔离环境通过，不代表正式环境已经切换。
- 正式接入需将本次主服务版本部署到原服务，在网页注册 Windows 节点，再使用正式地址、节点 ID 和密钥重新运行安装脚本。
- 本次源码改动尚未提交或推送。验收 APK 来自隔离快照；当前远程仓库不包含这些改动，点击更新代码会重新获取远程分支。
- 没有连接的 ADB 手机或模拟器，未进行安装和启动的实机验收。
- 全局同时运行一个构建任务；各节点缓存独立。切换节点需要先更新该节点源码。
