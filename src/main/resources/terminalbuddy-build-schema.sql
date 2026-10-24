PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS sys_config (
    cfg_key TEXT PRIMARY KEY,
    cfg_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS android_build_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    server_id TEXT NOT NULL DEFAULT 'local',
    type TEXT NOT NULL,
    status TEXT NOT NULL,
    stage TEXT NOT NULL DEFAULT 'QUEUED',
    source_commit TEXT,
    commit_subject TEXT,
    version_code INTEGER,
    version_name TEXT,
    artifact_name TEXT,
    apk_size INTEGER,
    sha256 TEXT,
    signature_sha256 TEXT,
    error_text TEXT,
    requested_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    deadline INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_android_single_active
    ON android_build_task ((1)) WHERE status IN ('QUEUED', 'RUNNING', 'CANCELLING');

CREATE TABLE IF NOT EXISTS android_build_server (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);
