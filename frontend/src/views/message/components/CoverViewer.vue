<template>
    <n-modal v-model:show="visible" :mask-closable="true">
        <div class="cover-viewer" @click.self="close">
            <div class="viewer-bar">
                <button class="bar-btn" type="button" title="关闭" @click="close">
                    <i class="fa-solid fa-arrow-left"></i>
                </button>
                <span class="viewer-title">{{ title }}</span>
                <span class="bar-actions">
                    <button class="bar-btn" type="button" title="缩小" @click="zoomBy(-1)">
                        <i class="fa-solid fa-magnifying-glass-minus"></i>
                    </button>
                    <button class="bar-btn" type="button" title="放大" @click="zoomBy(1)">
                        <i class="fa-solid fa-magnifying-glass-plus"></i>
                    </button>
                    <button class="bar-btn" type="button" title="重置" @click="scale = 1">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </span>
            </div>
            <div class="viewer-body" @click.self="close">
                <div v-if="error" class="viewer-error">
                    <div>{{ error }}</div>
                    <button type="button" @click="openExternal">在新窗口打开</button>
                </div>
                <img v-else :src="url" :style="{ transform: 'scale(' + scale + ')' }" @dblclick="toggleZoom"
                    @error="error = '封面加载失败，链接可能已失效'" />
            </div>
        </div>
    </n-modal>
</template>

<script>
import { NModal } from 'naive-ui'
/**
 * 封面全屏查看器（对齐量子端 MediaViewer 的图片分支）：
 * 滚轮/按钮缩放（1–6x）、双击在 1x 与 2.5x 间切换、单击背景关闭。
 * 封面是站点外链，直接渲染，不需要 JWT blob 转换。
 */
export default {
    components: { NModal },
    name: 'CoverViewer',
    props: {
        show: {
            type: Boolean,
            default: false
        },
        url: {
            type: String,
            default: ''
        },
        title: {
            type: String,
            default: ''
        }
    },
    emits: ['update:show'],
    data() {
        return {
            scale: 1,
            error: ''
        }
    },
    computed: {
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
            }
        }
    },
    watch: {
        show(value) {
            if (value) {
                this.scale = 1
                this.error = ''
            }
        }
    },
    methods: {
        close() {
            this.visible = false
        },
        zoomBy(direction) {
            const next = direction > 0 ? this.scale * 1.4 : this.scale / 1.4
            this.scale = Math.min(6, Math.max(1, next))
        },
        toggleZoom() {
            this.scale = this.scale > 1 ? 1 : 2.5
        },
        openExternal() {
            if (this.url) {
                window.open(this.url, '_blank', 'noreferrer')
            }
        }
    }
}
</script>

<style scoped>
.cover-viewer {
    position: relative;
    width: 92vw;
    height: 88vh;
    display: flex;
    flex-direction: column;
    background: rgba(11, 15, 25, .96);
    border: 1px solid var(--line-2);
    border-radius: 14px;
    overflow: hidden;
}

.viewer-bar {
    flex: none;
    height: 48px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 12px;
    border-bottom: 1px solid var(--line);
}

.viewer-title {
    font-size: 14px;
    color: var(--text);
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.bar-actions {
    margin-left: auto;
    display: flex;
    gap: 6px;
}

.bar-btn {
    width: 32px;
    height: 32px;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--text-2);
    cursor: pointer;
    font-size: 13px;
}

.bar-btn:hover {
    background: rgba(36, 61, 50, .09);
    color: var(--text);
}

.viewer-body {
    flex: 1;
    min-height: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: auto;
    padding: 16px;
}

.viewer-body img {
    max-width: 100%;
    max-height: 100%;
    transition: transform .18s ease;
    transform-origin: center center;
}

.viewer-error {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    color: var(--text-2);
    font-size: 13px;
}

.viewer-error button {
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid var(--line-2);
    background: none;
    color: var(--accent);
    cursor: pointer;
    font-family: inherit;
    font-size: 13px;
}
</style>
