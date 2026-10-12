<template>
    <div class="rich-text" :class="{ 'is-clamped': clamped }" v-html="html" v-bind="$attrs"></div>
    <button v-if="foldable" class="fold-btn" type="button" @click="toggleFold">
        {{ clamped ? '展开全文' : '收起' }}
    </button>
</template>

<script>
/**
 * 富文本渲染（自 quantum-web 的 RichText.vue 迁移）：
 * 彩色文字 / 胶囊标签 / 命名链接（只显示文字）/ URL 自动链接 / 超 10 行折叠。
 * 电影的详情页地址是裸 URL，靠 linkify 转成可点链接。
 */
import { parseRich, needFold, SPAN_PLAIN, SPAN_LINK, RICH_COLORS } from '@/utils/richText'

// 覆盖 http(s) 裸链接；结尾刻意不吃中文标点，避免把「。」「）」粘进地址
const URL_REGEX = /(https?:\/\/[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)/g

function escapeHtml(text) {
    return String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
}

export default {
    name: 'RichText',
    // 根节点是 fragment（正文 + 折叠按钮），Vue 无法自动继承 class 等属性：
    // 显式关掉自动继承，并把 $attrs 绑到正文根节点上，保证父级样式类（如 .notify-body）生效。
    inheritAttrs: false,
    props: {
        text: {
            type: String,
            default: ''
        },
        // 配文等紧凑场景可关掉折叠
        supportFold: {
            type: Boolean,
            default: true
        }
    },
    data() {
        return {
            folded: true
        }
    },
    computed: {
        foldable() {
            return this.supportFold && needFold(this.text)
        },
        clamped() {
            return this.foldable && this.folded
        },
        html() {
            return parseRich(this.text)
                .map((span) => {
                    if (span.type === SPAN_PLAIN) {
                        return this.linkify(span.text)
                    }
                    if (span.type === SPAN_LINK) {
                        // 命名链接：只显示文字（如「详情」），链接地址不占版面
                        return '<a href="' + escapeHtml(span.url) + '" target="_blank" rel="noreferrer">'
                            + escapeHtml(span.text) + '</a>'
                    }
                    const color = RICH_COLORS[span.color]
                    if (span.pill) {
                        return '<span class="rt-pill" style="color:' + color + ';background:' + color
                            + '1f;border-color:' + color + '59">'
                            + '<i style="background:' + color + '"></i>' + escapeHtml(span.text) + '</span>'
                    }
                    return '<span class="rt-mark" style="color:' + color + '">' + escapeHtml(span.text) + '</span>'
                })
                .join('')
        }
    },
    methods: {
        linkify(text) {
            return escapeHtml(text)
                .split(URL_REGEX)
                .map((part, index) =>
                    index % 2
                        ? '<a href="' + part + '" target="_blank" rel="noreferrer">' + part + '</a>'
                        : part
                )
                .join('')
        },
        toggleFold() {
            this.folded = !this.folded
        }
    }
}
</script>

<style scoped>
.rich-text {
    font-size: 14px;
    line-height: 1.6;
    white-space: pre-wrap;
    /* 气泡宽度由上一层 flex 决定：这里必须允许收缩，
       否则无空格长串（磁力 / 长 URL / 连续英文）会把气泡撑出容器、压到对侧头像列。 */
    min-width: 0;
    max-width: 100%;
    /* 无空格长串（磁力 / 长 URL / 连续英文）必须能在任意位置断行：
       break-word 是兼容老 WebView 的写法，anywhere 是新内核下更彻底的兜底。 */
    word-break: break-word;
    overflow-wrap: break-word;
    overflow-wrap: anywhere;
}

.rich-text.is-clamped {
    display: -webkit-box;
    -webkit-line-clamp: 10;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.rich-text :deep(.rt-mark) {
    font-weight: 600;
}

.rich-text :deep(.rt-pill) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 6px;
    margin: 0 1px;
    border-radius: 999px;
    border: 1px solid;
    font-size: 11px;
    font-weight: 500;
    line-height: 1.5;
}

.rich-text :deep(.rt-pill i) {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    display: block;
}

.rich-text :deep(a) {
    color: var(--accent);
    text-decoration: underline;
    text-underline-offset: 2px;
    word-break: break-all;
}

.fold-btn {
    margin-top: 4px;
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent);
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;
}

.fold-btn:hover {
    color: var(--accent-2);
}

/* 手机端：正文与链接加大到 16px 级，折叠按钮给足点按高度 */
@media (max-width: 900px) {
    .rich-text {
        font-size: 15px;
        line-height: 1.58;
    }

    .rich-text :deep(a) {
        padding: 2px 0;
        display: inline-block;
    }

    .fold-btn {
        padding: 4px 0;
        font-size: 13px;
    }
}
</style>
