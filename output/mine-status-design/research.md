# 葫芦娃 App「我的」服务状态设计调研

调研日期：2026-10-22。调研范围：当前仓库的 Android Compose 客户端、Vue 网页端、Java 后端及 Docker / FRP / Linux 官方资料。设计图使用本地示例数据，不代表线上服务器的实际状态。

## 推荐方案

把「我的」升级为“账号 + 服务器状态 + FRP / Docker 摘要 + 连接与账户”。延续当前松柏绿、雾白背景、圆角卡片和五标签导航，保持现有「功能 / 系统 / AI / 下载 / 我的」顺序。

「我的」承载查看与快速入口；FRP / Docker 的完整管理沿用「系统」里的现有页面和操作逻辑。服务器状态新增独立二级页。点击摘要卡片进入详情，返回后保留「我的」原滚动位置。二级页面隐藏主导航，复用现有横向转场与 Android 返回行为。

本次交付为设计方案和独立交互原型，业务源码不在本次改动范围。

## 当前实现和缺口

| 模块 | 当前实现 | 对设计的影响 |
| --- | --- | --- |
| Android「我的」 | `android/app/src/main/java/com/huluwa/android/ui/MineScreen.kt` 只有账号卡、连接配置、退出 / 切换服务器 | 可加入资源卡与服务卡，无需挤占原来的功能入口 |
| 导航与主题 | `ui/HuluwaRoot.kt`、`ui/Theme.kt`；五标签为功能、系统、AI、下载、我的 | 本方案使用当前导航，不沿用旧设计里的“订阅”标签 |
| 服务器指标 | `DashboardController.server()`、`ServerMetricsService` 已提供 CPU、内存、平均负载 | 后端能力已存在，Android 需要新增模型与调用 |
| 网页服务器概览 | `frontend/src/views/dashboard/index.vue` 已使用服务器资源接口 | 可参考既有字段和空值处理，手机采用更紧凑的卡片 |
| FRP | `ui/FrpScreen.kt`、`FrpService` 已有进程、隧道列表、运行状态、本地端口探测、错误详情 | Android `Tunnel` 尚未保留 `probe` / `probeText` / `error`；`FrpRuntime` 尚未保留全部统计和配置可用性 |
| Docker | `ui/DockerScreen.kt`、`HuluwaRepository.docker()` 已有容器、环境、统计及资源管理 | 首屏可复用基础信息；总览不必采集每一个容器的资源用量 |
| 我的刷新 | `HuluwaViewModel.refreshCurrent()` 在 Mine 分支仅刷新会话 | 应新增 Mine 可见时的独立状态刷新，避免误用系统页的重型刷新 |
| 指标时效 | 后端每 2 秒采样；失败后保留旧快照，响应没有采集时间和过期标识 | 请求成功不代表采样新鲜，落地应补充采集时间与采样状态 |

## 同类工具的设计参考

| 参考 | 官方资料显示的做法 | 对葫芦娃的取舍 |
| --- | --- | --- |
| Beszel | 轻量服务器监控，将主机资源、Docker 统计、历史数据和告警作为主要能力 | 借鉴资源概览的清晰层级；当前只展示已有即时指标，历史与告警留给后续有数据支持时加入 |
| Portainer | 容器列表进入日志、统计、控制台等具体页面；统计页独立展示 CPU、内存、网络、I/O 与刷新频率 | 借鉴“列表查看 → 详情操作”，将停止、重启等动作留在容器详情 |
| FRP 官方管理界面 | 客户端 Admin UI 提供状态、配置、热加载等能力 | 沿用项目已有后端管理；卡片聚焦进程与隧道状态，不在「我的」铺开配置表单 |

本方案借鉴这些工具的信息分层，继续使用葫芦娃现有主题、底部导航和管理入口。桌面监控台里的密集表格不适合直接缩小到手机首屏。

## 四张主设计图

| 页面 | 展示内容 | 主要操作 |
| --- | --- | --- |
| 我的总览 | 账号、消息同步、服务器地址与可达状态；CPU / 内存使用率；运行隧道数；运行容器数；更新时间 | 点卡片进入详情，连接配置，退出或切换服务器 |
| 服务器状态 | CPU 使用率与核数；内存已用 / 总量 / 可用；1 / 5 / 15 分钟平均负载 | 刷新，返回总览 |
| FRP 隧道 | frpc 容器状态；全部 / 运行 / 异常数量；主动停用数量；每条隧道本地地址、云端端口或域名、本地探测 | 全部 / 运行 / 异常 / 停用筛选；隧道详情；新增；日志；更多菜单中的热加载、配置、重启 |
| Docker | 守护进程连接与版本；全部 / 运行 / 未运行容器；运行容器 CPU / 内存合计；容器列表；镜像 / 网络 / 存储卷 | 搜索、筛选、资源切换、容器详情、日志、重启与启停 |

日常示例：CPU 23.6%，内存 42.8%（6.8 / 15.9 GiB）；FRP 4 条，其中 3 条运行、1 条停用；Docker 12 个容器，其中 10 个运行、1 个暂停、1 个停止。详情页与总览使用同一组示例。

局部异常示例：远程终端启动失败，FRP 变为 2 条运行、1 条异常、1 条停用；Docker 和服务器信息继续正常展示。

## 数据如何接入

以下路径均为完整业务路径，需沿用现有 Bearer 认证；示例时间、数值均不能直接写入生产代码。

| 展示字段 | 当前数据来源 | 落地处理 |
| --- | --- | --- |
| 服务器是否可达 | 当前服务器的认证 REST 请求结果与现有重连机制 | 认证失败进入登录处理；网络错误显示“暂时无法连接”，不推断为服务器已关机 |
| 实时同步 / 轮询同步 | 当前 WebSocket 连接和 REST 轮询状态 | 与服务器资源、Docker、FRP 状态分别展示 |
| CPU | `GET /api/dashboard/server` → `available`、`cpu.percent`、`cpu.cores` | 空值显示 `—`；CPU 使用率为整机 0–100% 口径 |
| 内存 | 同上 → `memory.percent`、`used`、`total`、`available` | 字节统一换算为 GiB；已用 = 总量 − MemAvailable；不能混用 GB 标签 |
| 平均负载 | 同上 → `cpu.load1`、`load5`、`load15` | 可选字段，缺失显示 `—`；负载不是百分比，不使用 CPU 百分比告警阈值 |
| FRP 进程 / 汇总 | `GET /api/frp/runtime` → `containerRunning`、`containerStatus`、`configAvailable`、`total`、`running`、`error`、`disabled`、`message` | 配置读取失败时，不把默认的 0 当作“没有隧道”；进程运行不等于隧道全部运行 |
| FRP 隧道详情 | `GET /api/frp/tunnels` → `enabled`、`status`、`failed`、`localAddress`、`remoteAddress`、`probe`、`probeText`、`error` | 详情页再取列表；为 Android Tunnel 补齐本地探测与错误字段 |
| Docker 连接与版本 | `GET /api/docker/environment` → `daemonOnline`、`serverVersion`、`message` | 根据连接状态门控版本及汇总；连接失败时不能展示成 0 个容器 |
| Docker 概览 | `GET /api/docker/summary` → `available`、`total`、`running`、`stopped` | `stopped` 当前实现是 total − running，UI 必须标为“未运行” |
| 容器状态细分 | `GET /api/docker/containers?all=true` → 每条容器的 `State` / `Status` | 从列表细分 paused、exited、restarting、dead 等；总览尚未获取列表时只显示“2 个未运行”，不编造细分 |
| 容器资源用量 | `POST /api/docker/containers/stats`，沿用 Repository 计算 | 进入 Docker 页再采样；明确“运行中容器 CPU / 内存合计”；部分采样成功应显示覆盖数量，不能伪装完整合计 |
| 镜像 / 网络 / 存储卷 | `/api/docker/images`、`/networks`、`/volumes` | 点击对应标签后再加载，沿用当前资源管理 |

服务器 CPU 数据来自应用可见的 `/proc`。常见飞牛 Docker 部署可看到宿主机指标，但其他部署、隔离视图以及远程 Docker 主机可能改变资源范围。服务器状态对应葫芦娃后端所在环境，Docker 页面对应所配置的 Docker Engine，不能假定两者永远是同一台机器。

设计图中显示暂停 / 停止细分时，前提是容器列表已经成功加载。优先显示轻量摘要，细分数据加载后再补充。

## 状态设计规则

| 情况 | 页面表现 | 恢复方式 |
| --- | --- | --- |
| 首次加载 | 卡片骨架；数量、比例保持未知 | 首次数据返回后显示真实值 |
| 后续刷新 | 保留现有数据，标题显示刷新进度 | 更新成功后替换数据并更新时间 |
| 单模块失败 | 只在该卡片提示“无法获取”并提供重试；其他模块继续展示 | 独立重试 |
| FRP 启动失败 | 总览出现琥珀色提示，隧道状态使用明确文字；本地端口探测单独保留 | 进入详情查错误和日志 |
| FRP 主动停用 | 中性“已停用” | 进入管理页按需启用，不归入故障数量 |
| frpc 未运行 | 标记“未运行”；不继续使用旧的绿色隧道状态 | 查看容器与日志 |
| FRP 状态未知 | frpc 可以运行，但隧道状态显示“待查询” | 恢复状态接口后更新；不能把空状态推断为成功 |
| Docker 未运行容器 | 区分暂停、停止、重启中等；不把所有非 running 容器标红 | 进入具体容器详情判断 |
| Docker 连接失败 | “Docker 无法连接”，数量显示 `—`；有缓存则注明“上次数据” | 检查运行环境或重试 |
| 服务器断连且有缓存 | 顶部提示“暂时连不上服务器”；标出“上次获取的数据”和具体时间 | 恢复连接；禁用新增、启停、重启等写操作 |
| 服务器断连且无缓存 | 未知值使用 `—`，不出现示例数据或默认 0 | 重试或修改连接配置 |
| 真实空列表 | 连接成功后显示“还没有隧道 / 容器” | 隧道提供新增入口；Docker 保留部署提示 |
| 指标暂不可用 | 服务器仍可在线，资源值显示 `—`，提示等待采样或无法获取 | 恢复采样，不影响 FRP / Docker |
| 采样过期 | 旧值降强调，标出最后采集时间与异常 | 后端提供真实采样时间，恢复后更新 |

进程状态、隧道状态、本地端口探测是三个独立维度。FRP 的 `running` 代表代理的运行状态，本地 TCP 端口可达不代表公网端到端一定可用。当前探测实现不能说明 UDP 服务健康，不应给 UDP 隧道展示“服务健康”。

Docker `running` 也不代表容器内业务健康；若后续接入 Docker Healthcheck，需区分“健康 / 不健康 / 启动中 / 未配置”，没有健康检查不能默认为健康。

## 官方资料调研结论

1. **Docker 状态需要拆开看。** 官方列出 created、restarting、running、removing、paused、exited、dead；Healthcheck 是另一组状态。这支持使用“运行中 / 未运行”做概要，再在详情中细分，而不是把停止数量等同于故障。
2. **Docker 内存有口径差异。** Docker CLI 在 Linux 上会从内存使用量中扣除部分缓存，API 返回原始用量和缓存数据。当前客户端使用 API 的 memory usage，设计使用“容器内存合计”，不承诺与 `docker stats` 的显示完全相同。
3. **容器 CPU 与整机 CPU 不能画成同一刻度。** 当前客户端计算采用 Docker 多核口径，单容器可超过 100%；主机卡片则使用整机 0–100%。Docker 详情使用数值，不画会在 100% 封顶的仪表盘。
4. **FRP 自带客户端管理界面。** 官方说明客户端 Admin UI 用于状态、配置、热加载等管理；本项目已通过后端接入，不需要让 App 直接连接管理端口。设计沿用当前后端支持的能力。
5. **FRP 配置能力以当前后端为准。** 官方 README 说明 v0.52 起支持 TOML / YAML / JSON，INI 已弃用；本仓库仍通过 `FrpIniFile` 管理 INI。本轮复用现有配置流程，未来支持新格式需要独立适配。
6. **MemAvailable 比 MemFree 更适合描述可用内存。** Linux 内核文档说明它估算了不发生交换时可供新应用使用的内存。当前后端用 total − available 计算已用，设计保持一致。

## 刷新和技术落地建议

建议新增 `MineOverviewState`，其中 server、frp、docker 各自使用独立 `LoadState`，记录 `lastFetchedAt`，不能以一个请求失败清空整页。通过 `supervisorScope` 隔离普通模块错误；401 仍交给统一认证流程处理。

- 进入「我的」时立即获取资源、FRP 汇总和 Docker 汇总；先显示轻量信息。
- 服务器资源可见时每 5 秒读一次缓存快照；服务器二级页可沿用后端 2 秒采样节奏。
- FRP / Docker 汇总建议 15–30 秒刷新一次；进入详情和完成操作后立即刷新对应模块。
- 当前 FRP runtime 会读配置与状态，Docker summary 会查询容器、镜像、网络。总览不要每 2 秒调用它们，也不要每次都调用聚合 `/api/dashboard/overview`。
- 建议把 FRP / Docker 摘要放入后端独立缓存；轻量接口复用缓存，详情页再加载完整列表和采样数据。
- 离开可见页面、App 进入后台后停止轮询；请求取消、服务器切换时清理旧请求与旧服务器缓存。旧服务器数据不能出现在新服务器卡片。
- Docker 详情中的批量 stats 只针对需要显示的运行容器，防止每次总览刷新都启动全量采样。
- 后端服务器指标补充 `sampledAt`、`lastAttemptAt`、`stale` / 采样状态；采样失败仍可保留旧快照，但必须能让客户端识别。客户端“刚刚获取”仅表示收到响应，不等同于刚完成采样。

当前 `ServerMetricsService` 的注释提到运行时长，但实际 `build()` 只返回 CPU 与内存。本方案以实际返回字段为准，不展示运行时长。

## 视觉与交互规范

| 项目 | 方案 |
| --- | --- |
| 主色 | Pine `#206C54`；深色卡片 `#194F3E` |
| 背景 / 文字 | Paper `#F6F8F5`；Ink `#243D32`；弱文字应在原生落地时满足对比度要求 |
| 状态色 | 正常用绿；待关注用琥珀；失败用红；主动停用用灰，并始终配状态文字 |
| 卡片与间距 | 16–20dp 圆角，16–22dp 页面边距，12–16dp 卡片间距，细分隔线 |
| 数字 | 等宽数字优先，主要指标 28–36sp；未知值使用 `—` |
| 触控 | 原生实现按 Material 控件保持至少 48dp 触控区域；小图标及小字动作需要扩大点击区域 |
| 无障碍 | 支持系统字体缩放；200% 字体时优先换行或改为单列，不固定内容高度 |
| 页面安全区 | 使用真实系统状态栏、底部手势区；设计图中的时间、挖孔、电池均为示意 |
| 详情页操作 | 总览不给一键重启。容器停止 / 重启、frpc 重启使用现有确认流程并说明影响 |

## 本轮与后续范围

本轮优先支持现有 CPU / 内存 / 负载、FRP、Docker 能力。以下适合作为后续扩展，当前设计不把它们伪装为已可用字段：

| 后续内容 | 需要补齐 |
| --- | --- |
| 磁盘容量、分区与 SMART | 明确宿主机磁盘采集范围和接口；现有剧集目录扫描不能代替整盘容量 |
| 温度与风扇 | 传感器采集、设备差异与权限处理 |
| 上传 / 下载速率 | 网络接口选择、连续采样与带宽计算 |
| 系统运行时长 | 后端实际读取并返回 uptime |
| 历史曲线 | 带时间戳的采样序列与持久化或缓存；目前仅有单帧快照 |
| 公网可达性、FRP 流量与延迟 | 额外探测或服务端统计；frpc running / 本地端口探测不足以推导这些数值 |
| 告警通知与自定义阈值 | 告警策略、持续时长、去重与通知配置 |

## 来源

- Beszel 官方：轻量监控、容器统计、历史与告警：<https://beszel.dev/>
- Portainer 官方：容器列表与管理：<https://docs.portainer.io/user/docker/containers>
- Portainer 官方：容器统计详情与刷新频率：<https://docs.portainer.io/user/docker/containers/stats>
- FRP 官方 README：健康检查与配置格式：<https://github.com/fatedier/frp/blob/dev/README.md>
- Docker 官方：容器状态与健康状态筛选：<https://docs.docker.com/reference/cli/docker/container/ls/>
- Docker 官方：容器资源统计、API 与 CLI 内存差异：<https://docs.docker.com/reference/cli/docker/container/stats/>
- Docker Engine API v1.44：<https://docs.docker.com/reference/api/engine/version/v1.44/>
- FRP 官方：服务端 Dashboard 与客户端 Admin UI：<https://gofrp.org/en/docs/features/common/ui/>
- Linux 内核官方：`/proc`、`meminfo` 与 `MemAvailable`：<https://docs.kernel.org/filesystems/proc.html>
- 本仓库：`MineScreen.kt`、`HuluwaRoot.kt`、`Models.kt`、`HuluwaRepository.kt`、`HuluwaViewModel.kt`、`ServerMetricsService.java`、`FrpService.java`、`DockerService.java` 及相应 Controller。
