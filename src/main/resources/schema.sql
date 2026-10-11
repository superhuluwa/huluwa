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

-- 消息中心会话（聊天对象）：当前内置「电影港」搜索会话与「磁力下载」会话；
-- 「系统通知」为虚拟会话，直接读 message 表，不落 chat_session。
CREATE TABLE IF NOT EXISTS chat_session (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    kind        TEXT NOT NULL DEFAULT 'chat',  -- chat | movie | magnet | system
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

-- 会话消息：role=user 用户发送 / role=bot 机器人回复；type=text 纯文本 / image 图片气泡（封面+配文）/ error 错误提示
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

-- 内置会话：电影港搜索（幂等，重启不覆盖用户数据）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('movie', '电影港', 'movie', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：磁力下载（把磁力链接提交给 qBittorrent；连接配置存 sys_config，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('magnet', '磁力下载', 'magnet', datetime('now', 'localtime'), datetime('now', 'localtime'));
