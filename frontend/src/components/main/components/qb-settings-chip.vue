<template>
    <div class="qb-chip-wrap">
        <button class="qb-chip" type="button" :class="stateClass" :title="tooltip"
            @click="dialogVisible = true">
            <span class="dot"></span>
            <i class="fa-solid fa-magnet icon"></i>
            <span class="label">{{ label }}</span>
        </button>

        <QbSettingsDialog v-model:show="dialogVisible" @saved="onSaved" />
    </div>
</template>

<script>
/**
 * 顶栏右上角的 qBittorrent 设置入口（常驻，与用户头像同排）：
 *  - 状态灯 + 文字直接反映当前连接配置（未配置=橙，已配置=绿）；
 *  - 点击弹出连接配置表单（服务地址 / 账号密码 / 默认保存路径 / 测试连接）。
 *
 * 这样在任意页面（消息中心以外的页面也一样）都能一眼看到下载器状态并随时改配置，
 * 不必先切到「磁力下载」会话。
 */
import { GetQbSettings } from '@/api/qbittorrent'
import QbSettingsDialog from '@/views/message/components/QbSettingsDialog.vue'

export default {
    name: 'QbSettingsChip',
    components: { QbSettingsDialog },
    data() {
        return {
            dialogVisible: false,
            settings: {}
        }
    },
    computed: {
        configured() {
            return !!this.settings.configured
        },
        stateClass() {
            return this.configured ? 'ok' : 'warn'
        },
        label() {
            return this.configured ? 'qBittorrent' : '配置 qBittorrent'
        },
        tooltip() {
            if (!this.configured) {
                return '尚未配置 qBittorrent，点击填写服务地址与账号密码'
            }
            const url = this.settings.url || ''
            const auth = this.settings.useAuth ? '账号密码登录' : '免登录（IP 白名单）'
            return 'qBittorrent 已连接：' + url + '（' + auth + '）· 点击修改'
        }
    },
    mounted() {
        this.refresh()
    },
    methods: {
        async refresh() {
            try {
                this.settings = (await GetQbSettings()) || {}
            } catch (e) {
                // 未登录 / 网络异常由拦截器提示，按钮保持上一次快照
            }
        },
        onSaved(data) {
            // 保存成功的提示由弹窗内部发出，这里只更新顶栏状态灯
            this.settings = data || {}
        }
    }
}
</script>

<style scoped>
.qb-chip-wrap {
    display: flex;
    align-items: center;
}

.qb-chip {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    height: 32px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: rgba(148, 163, 184, .06);
    color: var(--text-2);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
    white-space: nowrap;
    transition: border-color .15s, background .15s, color .15s;
}

.qb-chip:hover {
    border-color: var(--line-2);
    color: var(--text);
}

.qb-chip .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex: none;
}

.qb-chip .icon {
    font-size: 12px;
}

.qb-chip.ok .dot {
    background: var(--ok);
    box-shadow: 0 0 8px var(--ok);
}

.qb-chip.ok .icon {
    color: var(--ok);
}

.qb-chip.warn {
    border-color: rgba(245, 158, 11, .35);
    background: rgba(245, 158, 11, .10);
    color: var(--warn);
}

.qb-chip.warn .dot {
    background: var(--warn);
    box-shadow: 0 0 8px var(--warn);
}

.qb-chip.warn:hover {
    color: #FCD34D;
    border-color: rgba(245, 158, 11, .55);
}

/* 移动端：顶栏宽度紧张，只留状态灯 + 磁铁图标，文字收进标题（按钮 title） */
@media (max-width: 900px) {
    .qb-chip {
        padding: 0 10px;
        height: 30px;
    }

    .qb-chip .label {
        display: none;
    }
}
</style>
