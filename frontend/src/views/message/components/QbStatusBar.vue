<template>
    <div class="qb-bar" :class="configured ? 'ok' : 'warn'">
        <span class="dot"></span>
        <span class="label">{{ label }}</span>
        <span v-if="configured && url" class="addr mono">{{ url }}</span>
        <span v-if="configured && username" class="user">{{ username }}</span>
        <button class="action" type="button" @click="openSettings">
            <i class="fa-solid fa-gear"></i>
            {{ configured ? '连接设置' : '立即配置' }}
        </button>
    </div>

    <QbSettingsDialog v-model:show="dialogVisible" :intro="intro" @saved="$emit('saved', $event)" />
</template>

<script>
/**
 * 「磁力下载」会话的 qBittorrent 连接状态条：常驻在输入框上方，
 * 未配置时高亮提示，配置完成后显示服务地址与用户名，并提供随时修改的入口。
 */
import QbSettingsDialog from './QbSettingsDialog.vue'

export default {
    name: 'QbStatusBar',
    components: { QbSettingsDialog },
    props: {
        /** 配置快照：{ configured, url, username, passwordSet, useAuth } */
        settings: {
            type: Object,
            default: () => ({})
        }
    },
    emits: ['saved'],
    data() {
        return {
            dialogVisible: false
        }
    },
    computed: {
        /** 快照可能还没拉到（null），统一兜底成空对象 */
        config() {
            return this.settings || {}
        },
        configured() {
            return !!this.config.configured
        },
        url() {
            return this.config.url || ''
        },
        username() {
            return this.config.username || ''
        },
        label() {
            if (!this.configured) {
                return '未配置 qBittorrent，无法提交磁力任务'
            }
            return this.config.useAuth ? 'qBittorrent 已配置（账号密码登录）' : 'qBittorrent 已配置（免登录 / IP 白名单）'
        },
        intro() {
            return this.configured ? '' : '首次使用需要先填写 qBittorrent 的服务地址与账号密码，配置保存在本机。'
        }
    },
    methods: {
        openSettings() {
            this.dialogVisible = true
        }
    }
}
</script>

<style scoped>
.qb-bar {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 14px;
    border-top: 1px solid var(--line);
    font-size: 12px;
    background: rgba(20, 28, 46, .45);
}

.qb-bar .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex: none;
}

.qb-bar.ok {
    color: var(--text-2);
}

.qb-bar.ok .dot {
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
}

.qb-bar.warn {
    color: var(--warn);
    background: rgba(245, 158, 11, .10);
}

.qb-bar.warn .dot {
    background: var(--warn);
    box-shadow: 0 0 8px var(--warn);
}

.label {
    font-weight: 600;
}

.addr {
    color: var(--text-2);
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.mono {
    font-family: var(--mono, ui-monospace, SFMono-Regular, Menlo, monospace);
    font-size: 11.5px;
}

.user {
    color: var(--text-3);
}

.action {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 10px;
    border: 1px solid var(--line-2);
    border-radius: 999px;
    background: none;
    color: var(--accent);
    font-family: inherit;
    font-size: 12px;
    cursor: pointer;
    flex: none;
}

.action:hover {
    background: var(--accent-weak);
    border-color: rgba(14, 165, 233, .45);
}

/* 手机端：状态条文字只留关键信息，操作按钮放大到可点按尺寸 */
@media (max-width: 900px) {
    .qb-bar {
        padding: 8px max(12px, env(safe-area-inset-left)) 8px max(12px, env(safe-area-inset-right));
        font-size: 12.5px;
    }

    .addr,
    .user {
        display: none;
    }

    .action {
        padding: 6px 12px;
        font-size: 12.5px;
    }
}
</style>
