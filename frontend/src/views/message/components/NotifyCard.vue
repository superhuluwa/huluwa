<template>
    <div class="notify-card">
        <div class="notify-head">
            <span class="notify-dot" :style="{ background: levelColor }"></span>
            <span class="notify-category" :style="{ color: levelColor }">{{ levelLabel }}</span>
            <span v-if="source" class="notify-source">{{ source }}</span>
            <span class="notify-time">{{ timeText }}</span>
        </div>
        <div v-if="title" class="notify-title">{{ title }}</div>
        <RichText class="notify-body" :text="body" :support-fold="false" />
        <button v-if="deletable" class="notify-delete" type="button" title="删除该通知" @click.stop="$emit('delete')">
            <i class="fa-solid fa-trash-can"></i>
        </button>
    </div>
</template>

<script>
/**
 * 系统通知卡（对齐量子端 NotifyCard 的视觉，数据换成 message 表的 level/source）：
 * 级别色点 + 级别标签 + 来源 + 标题 + 正文 3 行截断。
 */
import RichText from './RichText.vue'
import { formatTimeLabel } from '@/utils/chatFormat'

export default {
    name: 'NotifyCard',
    components: { RichText },
    props: {
        meta: {
            type: Object,
            default: () => ({})
        },
        /** 系统通知允许删除（清除入口在消息中心原有的消息管理接口） */
        deletable: {
            type: Boolean,
            default: false
        }
    },
    emits: ['delete'],
    computed: {
        level() {
            return this.meta.level || 'info'
        },
        levelLabel() {
            return { info: '信息', success: '成功', warning: '警告', error: '错误' }[this.level] || '信息'
        },
        levelColor() {
            return {
                info: 'var(--accent)',
                success: 'var(--ok)',
                warning: 'var(--warn)',
                error: 'var(--danger)'
            }[this.level] || 'var(--accent)'
        },
        source() {
            return this.meta.source || ''
        },
        title() {
            return this.meta.title || ''
        },
        body() {
            return this.meta.body || ''
        },
        timeText() {
            return this.meta.time || ''
        }
    },
    methods: {
        formatTimeLabel
    }
}
</script>

<style scoped>
.notify-card {
    position: relative;
    max-width: min(86%, 720px);
    border-radius: 16px;
    background: var(--bubble-bot, #1E293B);
    border: 1px solid var(--line-2);
    padding: 12px;
    cursor: default;
}

.notify-head {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 500;
}

.notify-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
}

.notify-source {
    color: var(--text-3);
    font-weight: 400;
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: .3px;
}

.notify-time {
    margin-left: auto;
    color: var(--text-2);
    font-weight: 400;
    font-size: 11px;
}

.notify-title {
    margin-top: 6px;
    font-size: 14px;
    font-weight: 700;
}

/* .notify-body 是传给子组件 RichText 根节点的类（多根 fragment 组件不携带父级 scopeId），
   故必须用 :deep() 让本组件的 scoped 规则命中，否则正文 3 行截断与字号全部失效。 */
/* 删除入口：悬浮才出现，避免干扰阅读 */
.notify-delete {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 24px;
    height: 24px;
    border: 0;
    border-radius: 7px;
    background: none;
    color: var(--text-3);
    cursor: pointer;
    font-size: 12px;
    opacity: 0;
    transition: opacity .15s ease;
}

.notify-card:hover .notify-delete {
    opacity: 1;
}

.notify-delete:hover {
    background: rgba(244, 63, 94, .14);
    color: var(--danger);
}

:deep(.notify-body) {
    margin-top: 4px;
    font-size: 13.5px;
    color: var(--text);
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    white-space: pre-wrap;
    word-break: break-word;
}
</style>
