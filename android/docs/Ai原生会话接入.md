# Ai 原生会话接入

安卓底部导航为「功能 / 系统 / 下载 / Ai / 我的」。Ai 首页用于选择服务端项目、模型供应商和模型，并显示最近对话；点击「开始对话」或最近对话后进入独立二级页面。对话页使用原生 Compose `LazyColumn` 消息列表与底部多行输入框，不显示一级底部标签栏。

## 服务准备与连接

1. 更新 TerminalBuddy 服务端，包含原生 AI 会话接口；桌面服务与独立服务器模式均可提供这些接口。
2. 在 TerminalBuddy 开启 Web API，设置账号及密码，并登记手机需要使用的项目目录。
3. 在服务运行账户下安装、登录或配置 Claude Code / Codex CLI。新会话使用该账户已有的模型供应商配置；手机不保存模型 API Key。
4. 葫芦娃安卓端进入「Ai」，填写手机可访问的 TerminalBuddy 服务地址、账号、密码。
5. 在 Ai 首页选择项目，再选择供应商及其可用模型，点击「开始对话」进入二级消息页面。

项目读取服务端已登记目录；供应商与模型读取 `/api/ai/catalog`，覆盖 Claude 通用供应商、容器 Claude 配置以及 Codex 供应商。项目未启用的 Agent 不出现在供应商选项中。服务端默认配置也提供独立选项。

首页选择会按当前服务地址保存在手机上；刷新、返回首页后保留仍有效的选择。供应商或模型被删除、停用时重新选择可用项，模型列表为空时无法创建对话。启动会话同时提交供应商标识与模型，后端为该会话应用对应配置；API 密钥始终留在服务端。

返回首页不会中断服务端正在执行的任务；可从「最近对话」继续进入。应用重新打开后先进入选择首页，不自动跳转到旧会话。

例如服务地址为 `http://192.168.1.10:9600`；也支持 HTTPS 和反向代理子目录。手机的 `127.0.0.1` 指向手机自身。若服务版本缺少 AI API，登录页会提示更新 TerminalBuddy。

Ai 登录与葫芦娃登录独立；用户名、地址与登录令牌单独保存。密码不落盘，令牌由独立 Android Keystore 密钥加密，401 只清除 Ai 登录状态。切换连接前会先完成新服务认证及 API 能力确认。

## 消息与任务

- 列表区分用户消息、AI 回复、工具调用和执行日志；文本可以选择复制，工具日志默认折叠。
- 输入框支持多行，发送成功后才清空；失败保留原文，重试复用请求标识，避免重复发送。
- 生成过程中可继续编辑下一条消息，也可以点击中断按钮；同一会话同时执行一个回合。
- Codex 的命令/文件修改确认、用户问题会显示为原生操作卡片。Claude print 模式沿用服务端工具权限，权限不足会显示在执行结果中。
- 手机退到后台会释放实时连接；AI 任务继续在服务端执行，返回后重新连接并补取消息。
- WebSocket 自动重连，REST 轮询补偿，确保代理不支持 WebSocket 时仍可收发消息。
- 服务重启会将未完成任务标记为中断，之后可继续发送消息；任务不跨服务重启继续执行。
- 删除会话删除的是 Ai 保存的消息记录，不删除项目文件。正在执行的会话须先中断再删除。

这里创建的是 Ai 管理的独立会话；服务器恢复同一 Claude session / Codex thread 以保持上下文。现有桌面 PTY 会话不自动接管进 Ai 消息列表。

## 模型、思考强度与 Token 用量

输入框上方采用一体化轻卡片，保留输入框的圆角、边框、输入高度及发送 / 中断按钮。卡片上层以 11～12sp 显示品牌图标、模型与思考强度；下层直接显示「上下文长度 / 水平进度条 / 26%(250K/950K)」及输入、输出用量。统计统一使用十进制 K（1K = 1000 Token），小屏或系统字体放大时自动分行。输入、输出优先显示本轮用量，尚未开始回合时显示已知的会话用量；当前上下文独立读取 `usage.context`，不使用累计消耗计算占用率。估算来源保留 `≈` 标记，压缩后等待更新时不显示旧占用率。

点击卡片的模型区域或“对话操作 → 上下文与 Token”打开详情，保留本轮与累计用量、缓存、推理输出及统计状态。未上报的卡片数值显示 `—K`，不使用固定容量代替。顶部标题栏保持单行对话标题。

模型与思考强度通过当前会话的 `provider`、`providerId`、`model` 匹配 `/api/ai/catalog` 的模型项。模型采用目录中的展示名称，目录未包含该项时采用会话模型名称；思考强度读取 `models[].effort`，支持关闭、极低、低、中、高、很高、最高、极高等中文显示，未提供时显示“跟随服务端”。这是当前供应商模型的配置值，卡片仅作展示，配置选择仍由 Ai 首页负责。

直接消费 TerminalBuddy 在会话 JSON 中新增的可选 `usage` 字段，无需通过葫芦娃后端转发，也不新增用量轮询：

| 字段 | 含义 |
| --- | --- |
| `usage.context.usedTokens` / `windowTokens` | 最近一次模型调用的上下文占用与容量，均有效时计算百分比 |
| `usage.context.basis` | Claude 为 `input`，Codex 估算为 `inputAndOutput`；当前上下文不使用整段会话累计消耗计算 |
| `usage.context.usedSource` / `windowSource` | 用量来源及容量来源；`calculated` 或 `configuration` 对应 `≈` 标注 |
| `usage.context.updating` | 压缩后等待下一次调用更新，期间不显示旧占用率 |
| `usage.lastTurn` | 最近一次用户发送触发的整轮用量；`state` 为 `provisional`、`final` 或 `partial` |
| `usage.sessionTotal` | 会话累计用量；`coverage` 为 `complete`、`sinceUpgrade` 或 `partial` |
| `inputTokens` / `outputTokens` | 输入（已含缓存）与输出总量，使用可空 `Long`，未知为 `null` |
| `cacheReadInputTokens` / `cacheCreationInputTokens` | 总输入中对应的缓存明细，不重复叠加 |
| `reasoningOutputTokens` | 总输出中的推理部分，不重复叠加 |

WebSocket 更新仍以新会话元数据为准，仅按消息 ID 合并历史正文，因此 `usage` 可随元数据保留；REST 补偿及 `revision` 防倒退机制继续适用。新会话首次发送前使用本地会话壳时，卡片显示已选择的 Agent、模型与思考强度；详情中的未知用量使用 `—`，创建与发送成功后跟随服务端快照更新。

旧服务端 / 旧会话没有 `usage` 时，客户端仍可解码和聊天。未知数字显示 `—`，窗口未知时不捏造百分比；旧会话从升级后统计、停止或异常导致的部分用量均在详情中说明。

## 接口

以下均为 TerminalBuddy 接口，返回直接 JSON，不是葫芦娃的 `code/data` 响应包装。REST 使用 `Authorization: Bearer <token>`，WebSocket 使用现有服务的 token 查询参数机制。

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/auth/login` | 服务账号密码认证 |
| `GET /api/projects` | 读取已登记项目与启用的 Agent |
| `GET /api/ai/catalog` | 读取可选供应商及其可用模型，不包含密钥 |
| `GET /api/ai/sessions` | 会话列表，不附带完整消息 |
| `POST /api/ai/sessions` | 创建会话：`provider`、`providerId`、`projectPath`、所选 `model` |
| `GET /api/ai/sessions/{id}` | 当前状态与完整消息 |
| `POST /api/ai/sessions/{id}/messages` | 提交消息：`text`、UUID `requestId` |
| `POST /api/ai/sessions/{id}/stop` | 中断本次执行 |
| `POST /api/ai/sessions/{id}/approvals` | 回答 Codex 操作确认或用户问题 |
| `DELETE /api/ai/sessions/{id}` | 删除空闲会话 |
| `/ws/ai/{id}?token=...` | 首帧完整会话，后续推送元数据与变化的消息 |

核心代码：`ui/AiScreen.kt`（首页）、`ui/AiConversationScreen.kt`（二级会话页）、`ui/AiUsageBar.kt`（模型、思考强度与用量悬浮卡片 / 详情）、`ui/HuluwaRoot.kt`（根导航与转场）、`AiViewModel.kt`、`data/AiRepository.kt`、`data/AiModels.kt`。所有新 UI、连接状态和消息生命周期均独立于原有家庭服务聊天功能。
