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

-- 消息中心会话（聊天对象）：当前内置「电影港」「电影天堂」搜索、「qBittorrent 下载」、「视频解析」、
-- 「迅雷下载【飞牛】」与「迅雷下载【Win】」会话；「系统通知」为虚拟会话，直接读 message 表，不落 chat_session。
CREATE TABLE IF NOT EXISTS chat_session (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    kind        TEXT NOT NULL DEFAULT 'chat',  -- chat | movie | dytt | magnet | video | xunlei | xunlei-win | system
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

-- 内置会话：电影天堂搜索（苹果 CMS 采集接口；在线 m3u8 资源，不参与磁力订阅链路，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('dytt', '电影天堂', 'dytt', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：qBittorrent 下载（把磁力链接提交给 qBittorrent；连接配置存 sys_config，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('magnet', 'qBittorrent 下载', 'magnet', datetime('now', 'localtime'), datetime('now', 'localtime'));
-- 老库标题同步：历史版本该会话标题为「磁力下载」，按 id 幂等更新，避免仅新部署生效
UPDATE chat_session SET title = 'qBittorrent 下载' WHERE id = 'magnet' AND title = '磁力下载';

-- 内置会话：视频解析（把抖音等分享口令交给内网解析服务，回复视频 / 图集气泡；
-- 服务地址与账号存 sys_config：video.url / video.username / video.password，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('video', '视频解析', 'video', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 内置会话：迅雷下载【飞牛】（把磁力链接提交给飞牛迅雷（迅雷 NAS 版）建下载任务；
-- 直连地址、Docker 版保存路径、手动 Cookie / 飞牛账号登录配置存 sys_config；飞牛原生版使用迅雷默认目录，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('xunlei', '迅雷下载【飞牛】', 'xunlei', datetime('now', 'localtime'), datetime('now', 'localtime'));
-- 老库标题同步：历史版本该会话标题为「迅雷下载」，按 id 幂等更新，避免仅新部署生效
UPDATE chat_session SET title = '迅雷下载【飞牛】' WHERE id = 'xunlei' AND title = '迅雷下载';

-- 内置会话：迅雷下载【Win】（把磁力链接提交给 Windows 机器上的 xunlei-win-api 服务建下载任务；
-- 服务地址与 Windows 保存根目录存 sys_config：xlwin.url / xlwin.savepath，幂等）
INSERT OR IGNORE INTO chat_session (id, title, kind, created_at, updated_at)
VALUES ('xunlei-win', '迅雷下载【Win】', 'xunlei-win', datetime('now', 'localtime'), datetime('now', 'localtime'));

-- 电影港搜索监听：暂无资源时按关键字巡检，命中后通知一次并结束；重启后继续。
CREATE TABLE IF NOT EXISTS movie_search_watch (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword          TEXT NOT NULL,
    keyword_key      TEXT NOT NULL UNIQUE,       -- 统一大小写的关键字，防止重复监听
    status           TEXT NOT NULL DEFAULT 'watching', -- watching|found|cancelled
    interval_minutes INTEGER NOT NULL DEFAULT 30,
    created_at       TEXT NOT NULL,
    updated_at       TEXT NOT NULL,
    last_check_at    TEXT,
    next_check_at    TEXT,
    found_at         TEXT,
    result_count     INTEGER NOT NULL DEFAULT 0,
    check_error      TEXT,
    version          INTEGER NOT NULL DEFAULT 1  -- 拦截取消或重启监听前的旧查询结果
);
CREATE INDEX IF NOT EXISTS idx_movie_search_watch_due ON movie_search_watch (status, next_check_at, id);

-- ============================================================
-- 电影港订阅（对齐 quantum 的 dygangs_search.cs / dygangs.cs）：
--   movie_subscription        订阅清单：片名 + 电影港具体链接 + 巡检状态（默认每小时一轮）
--   movie_subscription_magnet 详情页下已采集的磁力（订阅基线 + 后续新增），用于对比更新
--   movie_download_task       订阅触发的 qBittorrent 下载任务：qB 任务哈希 / 状态 / 两类通知标记
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
    downloader    TEXT NOT NULL DEFAULT 'qb',    -- 本剧绑定下载工具 qb|xunlei|xunlei-win；旧订阅在启动时补齐
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
    save_path       TEXT,                        -- 保存目录：统一根目录 / 剧名
    downloader      TEXT NOT NULL DEFAULT 'qb', -- qb|xunlei|xunlei-win，历史任务继续用原工具跟踪
    external_task_id TEXT,                       -- 迅雷返回的任务 ID（飞牛版任务ID / Win 版流水号）
    progress        INTEGER,                     -- 下载进度（0-100；各下载器跟踪时回填）
    total_bytes     INTEGER,                     -- 资源总大小（字节；未知为 NULL）
    received_bytes  INTEGER,                     -- 已下载 / 校验大小（字节）
    download_speed  INTEGER,                     -- 下载速度（字节 / 秒）
    speedup_speed   INTEGER,                     -- 迅雷加速速度（字节 / 秒）
    checked_at      TEXT,                        -- 最近一次查询时间
    query_error     TEXT,                        -- 查询异常；保留上次下载信息
    status          TEXT NOT NULL DEFAULT 'pending', -- pending|added|downloading|completed|failed|removed
    message         TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    completed_at    TEXT,
    hidden_at       TEXT                         -- 清空磁力下载列表：隐藏全部记录，去重历史仍保留
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_movie_task_magnet ON movie_download_task (magnet);
CREATE INDEX IF NOT EXISTS idx_movie_task_status ON movie_download_task (status, id);

INSERT OR IGNORE INTO sys_schema_version (version, applied_at)
VALUES (2, datetime('now', 'localtime'));

-- v3：WebSocket 聊天请求幂等 / 恢复表
INSERT OR IGNORE INTO sys_schema_version (version, applied_at)
VALUES (3, datetime('now', 'localtime'));

-- HLS 订阅与剧集任务：剧集发现、首次跳过和下载状态共用一条稳定记录。
CREATE TABLE IF NOT EXISTS hls_subscription (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    series_key TEXT NOT NULL,
    api_url TEXT NOT NULL,
    res_id TEXT NOT NULL,
    title TEXT NOT NULL,
    year TEXT,
    cover TEXT,                                  -- 海报地址，用于「我的订阅」左侧封面
    season INTEGER NOT NULL,
    source_name TEXT NOT NULL,
    interval_minutes INTEGER NOT NULL DEFAULT 60,
    from_episode INTEGER NOT NULL DEFAULT 1,
    headers TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    initialized INTEGER NOT NULL DEFAULT 0,
    last_check_at TEXT,
    next_check_at TEXT,
    last_error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(series_key, season)
);
CREATE INDEX IF NOT EXISTS idx_hls_subscription_due ON hls_subscription(enabled, next_check_at);

CREATE TABLE IF NOT EXISTS hls_download_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    subscription_id INTEGER REFERENCES hls_subscription(id),
    series_key TEXT NOT NULL,
    api_url TEXT,
    res_id TEXT,
    series_title TEXT NOT NULL,
    year TEXT,
    season INTEGER NOT NULL,
    episode INTEGER NOT NULL,
    episode_title TEXT,
    source_name TEXT,
    url TEXT NOT NULL,
    headers TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending', -- pending|downloading|verifying|completed|failed|skipped|cancelled
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT,
    save_path TEXT,
    log_path TEXT,
    message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    hidden_at TEXT,
    UNIQUE(series_key, season, episode)
);
CREATE INDEX IF NOT EXISTS idx_hls_task_due ON hls_download_task(status, next_attempt_at, id);
INSERT OR IGNORE INTO sys_schema_version(version, applied_at)
VALUES (4, datetime('now', 'localtime'));

-- v5：清空下载记录。隐藏记录而保留历史数据：订阅巡检与重复提交判定仍读取这些历史行。
INSERT OR IGNORE INTO sys_schema_version(version, applied_at)
VALUES (5, datetime('now', 'localtime'));

-- v6：订阅巡检记录。「消息中心 → 巡检记录」按时间倒序展示电影港 / 电影天堂每次巡检的
-- 结果快照；剧名与来源冗余存储，订阅被删除后历史记录仍然可读。
CREATE TABLE IF NOT EXISTS subscription_check_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source          TEXT NOT NULL,                      -- dygangs（电影港）| dytt（电影天堂）
    subscription_id INTEGER,                            -- 原订阅 id，仅作溯源参考
    series_title    TEXT NOT NULL,                      -- 剧名快照
    trigger_type    TEXT NOT NULL DEFAULT 'scheduled',  -- scheduled（定时）| manual（立即检查）
    status          TEXT NOT NULL,                      -- updated（有更新）| ok（无更新）| baseline（补建基线）| failed（失败）
    found_count     INTEGER NOT NULL DEFAULT 0,         -- 本轮新发现条目数
    submitted_count INTEGER NOT NULL DEFAULT 0,         -- 本轮提交下载条数
    message         TEXT,                               -- 结果摘要
    detail          TEXT,                               -- 新增条目明细，每行一条
    duration_ms     INTEGER NOT NULL DEFAULT 0,         -- 本轮巡检耗时（毫秒）
    created_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_check_log_created ON subscription_check_log (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_check_log_source ON subscription_check_log (source, id DESC);

INSERT OR IGNORE INTO sys_schema_version(version, applied_at)
VALUES (6, datetime('now', 'localtime'));

-- v7：离线下载任务支持取消；status 增加 cancelled 终态。取消后的剧集在订阅巡检中
-- 保持取消状态，只有用户显式「重试 / 重新下载」才会重新入队。
INSERT OR IGNORE INTO sys_schema_version(version, applied_at)
VALUES (7, datetime('now', 'localtime'));


-- v8：迅雷【Win】独立下载记录，所有提交入口按服务地址 + 流水号统一跟踪。
CREATE TABLE IF NOT EXISTS xunlei_win_download_task (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    api_url         TEXT NOT NULL,
    serial          TEXT,                         -- 缺流水号时仍记录提交结果，无法查询进度
    name            TEXT NOT NULL,
    magnet          TEXT,
    save_path       TEXT,
    status          TEXT NOT NULL DEFAULT 'pending', -- pending|creating|created|downloading|success|failed|untracked
    progress        REAL,
    received_bytes  INTEGER,
    total_bytes     INTEGER,
    message         TEXT,
    query_error     TEXT,                         -- 查询错误与下载失败分开保存，保留上次进度
    hidden_at       TEXT,                         -- 清空后隐藏，保留流水号避免启动时重复补录
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    checked_at      TEXT,
    completed_at    TEXT,
    UNIQUE(api_url, serial)
);
CREATE INDEX IF NOT EXISTS idx_xunlei_win_task_status ON xunlei_win_download_task (status, checked_at, id);
INSERT OR IGNORE INTO sys_schema_version(version, applied_at)
VALUES (8, datetime('now', 'localtime'));
