-- ============================================================
-- Huluwa Console 数据库结构（SQLite）
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