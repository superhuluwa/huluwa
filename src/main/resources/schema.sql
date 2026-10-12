-- ============================================================
-- 葫芦娃助手 数据库结构（SQLite）
-- 所有建表语句必须保持幂等（IF NOT EXISTS），应用启动时自动执行
-- ============================================================

-- 系统用户（当前为单用户模型：id 恒为 1）
CREATE TABLE IF NOT EXISTS sys_user (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT NOT NULL UNIQUE,
    password    TEXT NOT NULL,                -- BCrypt 哈希
    nickname    TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- 键值配置：初始化标记、JWT 密钥、令牌版本等
CREATE TABLE IF NOT EXISTS sys_config (
    cfg_key     TEXT PRIMARY KEY,
    cfg_value   TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- 消息中心
CREATE TABLE IF NOT EXISTS message (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT NOT NULL,
    content     TEXT,
    level       TEXT NOT NULL DEFAULT 'info',  -- info | success | warning | error
    source      TEXT,                          -- 来源标识：system / frp / docker / push ...
    read_flag   INTEGER NOT NULL DEFAULT 0,    -- 0 未读 1 已读
    created_at  TEXT NOT NULL,
    read_at     TEXT
);
CREATE INDEX IF NOT EXISTS idx_message_created ON message (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_read ON message (read_flag);

-- Frp 内网穿透：隧道配置
CREATE TABLE IF NOT EXISTS frp_config (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    proxy_type    TEXT NOT NULL DEFAULT 'tcp', -- tcp | udp | http | https
    local_ip      TEXT NOT NULL DEFAULT '127.0.0.1',
    local_port    INTEGER NOT NULL,
    remote_port   INTEGER,
    custom_domain TEXT,
    subdomain     TEXT,
    extra         TEXT,                        -- 透传附加参数（JSON 文本）
    enabled       INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

-- 推送设置：推送渠道
CREATE TABLE IF NOT EXISTS push_channel (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    type        TEXT NOT NULL,                 -- webhook | serverchan | bark | telegram | dingtalk | wecom
    enabled     INTEGER NOT NULL DEFAULT 1,
    config      TEXT,                          -- 渠道参数（JSON 文本）
    subscribe   TEXT,                          -- 订阅的事件类型，逗号分隔；为空表示全部
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- Docker 管理：主机连接配置
CREATE TABLE IF NOT EXISTS docker_host (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    host        TEXT NOT NULL DEFAULT 'local', -- local 表示本机 docker CLI
    enabled     INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- schema 版本，供后续升级脚本使用
CREATE TABLE IF NOT EXISTS sys_schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TEXT NOT NULL
);
INSERT OR IGNORE INTO sys_schema_version (version, applied_at)
VALUES (1, datetime('now', 'localtime'));

-- 消息中心会话（聊天对象）：当前内置「电影港」搜索、「磁力下载」、「视频解析」与「迅雷下载」会话；
-- 「系统通知」为虚拟会话，直接读 message 表，不落 chat_session。
CREATE TABLE IF NOT EXISTS chat_session (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    kind        TEXT NOT NULL DEFAULT 'chat',  -- chat | movie | magnet | video | xunlei | system
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- 会话消息：role=user 用户发送 / role=bot 机器人回复；
-- type=text 纯文本 / image 图片气泡（封面+配文）/ video 视频气泡（可内联播放）/ audio 音频气泡 / error 错误提示
CREATE TABLE IF NOT EXISTS chat_message (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id   TEXT NOT NULL,
    role         TEXT NOT NULL,
    type         TEXT NOT NULL DEFAULT 'text',
    content      TEXT,                          -- 正文；image 类型为图片地址
    content_text TEXT,                          -- 配文（image 类型为封面下方文案，可含链接）
    payload      TEXT,                          -- 结构化载荷（JSON 文本，如影视卡片字段）
    read_flag    INTEGER NOT NULL DEFAULT 0,    -- 0 未读 1 已读（仅 bot 行参与未读统计）
    created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message (session_id, id DESC);

-- WebSocket 发送幂等与恢复：request_id 由前端生成并持久化到本地 outbox；
-- 弱网重连或页面刷新后重放同一 request_id，服务端只落一条用户消息。
-- status=received 已入库 / processing 后台处理中 / done 已完成 / failed 已落错误回复。
CREATE TABLE IF NOT EXISTS chat_request (
    request_id      TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL,
    user_message_id INTEGER NOT NULL,
    status          TEXT NOT NULL DEFAULT 'received',
    error           TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_request_status ON chat_request (status, user_message_id);

-- 内置会话：电影港搜索（幂等，重启不覆盖用户数据）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('movie', '电影港', 'movie', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：磁力下载（把磁力链接提交给 qBittorrent；连接配置存 sys_config，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('magnet', '磁力下载', 'magnet', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：视频解析（把抖音等分享口令交给内网解析服务，回复视频 / 图集气泡；
-- 服务地址与账号存 sys_config：video.url / video.username / video.password，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('video', '视频解析', 'video', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：迅雷下载（把磁力链接提交给飞牛迅雷（迅雷 NAS 版）建下载任务；
-- 直连地址、Docker 版保存路径、手动 Cookie / 飞牛账号登录配置存 sys_config；飞牛原生版使用迅雷默认目录，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('xunlei', '迅雷下载', 'xunlei', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- ============================================================
-- 电影港订阅（对齐 quantum 的 dygangs_search.cs / dygangs.cs）：
--   movie_subscription        订阅清单：片名 + 电影港具体链接 + 巡检状态（默认每小时一轮）
--   movie_subscription_magnet 详情页下已采集的磁力（订阅基线 + 后续新增），用于对比更新
--   movie_download_task       订阅触发的磁力下载任务：qB 任务哈希 / 状态 / 两类通知标记
-- ============================================================
CREATE TABLE IF NOT EXISTS movie_subscription (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    res_id        TEXT,                          -- 电影港资源ID（详情页文件名，如 48089）
    title         TEXT NOT NULL,                 -- 片名（剧名，下载子目录与文件名补全都用它）
    year          TEXT,
    origin        TEXT,
    genre         TEXT,
    cover         TEXT,
    detail_url    TEXT NOT NULL,                 -- 电影港具体链接（剧集详情页）
    enabled       INTEGER NOT NULL DEFAULT 1,    -- 0 取消订阅（数据保留，可重新订阅）
    baseline_at   TEXT,                          -- 订阅基线采集完成时间
    last_check_at TEXT,                          -- 最近一次巡检时间
    next_check_at TEXT,                          -- 下次巡检时间（默认 +1 小时）
    last_error    TEXT,                          -- 最近一次巡检失败原因（成功后清空）
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_movie_subscription_url ON movie_subscription (detail_url);

CREATE TABLE IF NOT EXISTS movie_subscription_magnet (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    subscription_id INTEGER NOT NULL,
    title           TEXT,                        -- 详情页里的剧集标题（如「xxx 第5季212.2160p」）
    magnet          TEXT NOT NULL,               -- 磁力链接原文（已还原 &amp; 实体）
    magnet_key      TEXT NOT NULL,               -- 去重键：info hash，无 hash 时用磁力原文
    quality         TEXT,                        -- 清晰度：2160P / 1080P / 720P / 480P / OTHER
    episode         TEXT,                        -- 剧集键（集号，如 212 / 208-209）
    baseline        INTEGER NOT NULL DEFAULT 0,  -- 1 订阅基线（不触发下载）
    first_seen_at   TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_movie_magnet_key ON movie_subscription_magnet (subscription_id, magnet_key);
CREATE INDEX IF NOT EXISTS idx_movie_magnet_sub ON movie_subscription_magnet (subscription_id, id);

CREATE TABLE IF NOT EXISTS movie_download_task (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    subscription_id INTEGER,
    series_title    TEXT NOT NULL,               -- 剧名（qB 保存子目录名）
    episode         TEXT,                        -- 剧集键
    quality         TEXT,
    magnet          TEXT NOT NULL,
    torrent_hash    TEXT,                        -- info hash，用于跟踪 qB 任务
    save_path       TEXT,                        -- 提交给 qB 的保存目录
    status          TEXT NOT NULL DEFAULT 'pending', -- pending|added|downloading|completed|failed|removed
    message         TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    completed_at    TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_movie_task_magnet ON movie_download_task (magnet);
CREATE INDEX IF NOT EXISTS idx_movie_task_status ON movie_download_task (status, id);

INSERT OR IGNORE INTO sys_schema_version (version, applied_at)
VALUES (2, datetime('now', 'localtime'));

-- v3：WebSocket 聊天请求幂等 / 恢复表
INSERT OR IGNORE INTO sys_schema_version (version, applied_at)
VALUES (3, datetime('now', 'localtime'));
