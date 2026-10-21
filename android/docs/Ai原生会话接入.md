# Ai 原生会话接入

安卓底部导航为「功能 / 系统 / 下载 / Ai / 我的」。Ai 独立连接 TerminalBuddy 服务，使用原生 Compose `LazyColumn` 消息列表和底部多行输入框。支持 Claude 与 Codex，不使用 WebView 或终端字符网格。

## 服务准备与连接

1. 更新 TerminalBuddy 服务端，包含原生 AI 会话接口；桌面服务与独立服务器模式均可提供这些接口。
2. 在 TerminalBuddy 开启 Web API，设置账号及密码，并登记手机需要使用的项目目录。
3. 在服务运行账户下安装、登录或配置 Claude Code / Codex CLI。新会话使用该账户已有的模型供应商配置；手机不保存模型 API Key。
4. 葫芦娃安卓端进入「Ai」，填写手机可访问的 TerminalBuddy 服务地址、账号、密码。
5. 点击「新建对话」，选择项目、Claude / Codex 和可选模型，进入消息页面。

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

## 接口

以下均为 TerminalBuddy 接口，返回直接 JSON，不是葫芦娃的 `code/data` 响应包装。REST 使用 `Authorization: Bearer <token>`，WebSocket 使用现有服务的 token 查询参数机制。

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/auth/login` | 服务账号密码认证 |
| `GET /api/projects` | 读取已登记项目与启用的 Agent |
| `GET /api/ai/sessions` | 会话列表，不附带完整消息 |
| `POST /api/ai/sessions` | 创建会话：`provider`、`projectPath`、可选 `model` |
| `GET /api/ai/sessions/{id}` | 当前状态与完整消息 |
| `POST /api/ai/sessions/{id}/messages` | 提交消息：`text`、UUID `requestId` |
| `POST /api/ai/sessions/{id}/stop` | 中断本次执行 |
| `POST /api/ai/sessions/{id}/approvals` | 回答 Codex 操作确认或用户问题 |
| `DELETE /api/ai/sessions/{id}` | 删除空闲会话 |
| `/ws/ai/{id}?token=...` | 首帧完整会话，后续推送元数据与变化的消息 |

核心代码：`AiScreen.kt`、`AiViewModel.kt`、`data/AiRepository.kt`、`data/AiModels.kt`。所有新 UI、连接状态和消息生命周期均独立于原有家庭服务聊天功能。
