<template>
    <!-- 系统通知卡：独立左对齐卡片，不带头像 -->
    <div v-if="isNotify" class="message-row">
        <NotifyCard :meta="notifyMeta" deletable @delete="$emit('delete', message)" />
    </div>

    <!-- 普通气泡 -->
    <div v-else class="message-row" :class="{ mine: isMine }">
        <div v-if="!isMine" class="avatar bot" :class="{ movie: isMovie, magnet: isMagnet }">
            <i v-if="isMovie" class="fa-solid fa-film"></i>
            <i v-else-if="isMagnet" class="fa-solid fa-magnet"></i>
            <template v-else>Q</template>
        </div>
        <div v-if="isMine" class="avatar mine">我</div>
        <div class="bubble" :class="{ 'is-cover': isImage, 'is-error': isError, 'hold-open': holdOpen }"
            :title="fullTime" @touchstart.passive="onHoldStart" @touchend="endHold" @touchcancel="endHold"
            @touchmove.passive="endHold">
            <div class="bubble-tools">
                <span class="tool" title="复制" @click="copy"><i class="fa-regular fa-copy"></i></span>
            </div>
            <div class="bubble-content">
                <!-- 图片气泡（影视封面 / 图片消息）：封面 + 配文，点击封面看大图 -->
                <template v-if="isImage">
                    <div v-if="loadError" class="cover-fallback">
                        <i class="fa-solid fa-image"></i>
                        <span>封面加载失败</span>
                    </div>
                    <img v-else class="cover" :src="message.content" alt="封面" @error="loadError = true"
                        @click="openCover" />
                    <RichText class="cover-caption" :text="message.contentText" :support-fold="false" />
                    <div v-if="isMovie" class="movie-actions">
                        <button class="subscribe-btn" type="button" :class="{ subscribed: subscribed, busy }"
                            :disabled="subscribed || busy" @click="onSubscribe">
                            <i :class="subscribed ? 'fa-solid fa-bell' : 'fa-regular fa-bell'"></i>
                            {{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}
                        </button>
                        <span class="subscribe-tip">{{ subscribed ? '每小时巡检更新' : '监听新增剧集并自动下载' }}</span>
                    </div>
                </template>
                <template v-else>
                    <RichText :text="message.content" />
                    <div v-if="isMovie" class="movie-actions">
                        <button class="subscribe-btn" type="button" :class="{ subscribed: subscribed, busy }"
                            :disabled="subscribed || busy" @click="onSubscribe">
                            <i :class="subscribed ? 'fa-solid fa-bell' : 'fa-regular fa-bell'"></i>
                            {{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}
                        </button>
                        <span class="subscribe-tip">{{ subscribed ? '每小时巡检更新' : '监听新增剧集并自动下载' }}</span>
                    </div>
                </template>
            </div>
        </div>
    </div>
</template>

<script>
/**
 * 消息气泡外壳（自 quantum-web 的 MessageBubble.vue 迁移）：
 * 头像 38px、机器人左/用户右、靠头像侧下角 4px 收尾、圆角 16、内边距 9/12、最大宽 min(68%,720px)。
 * 本项目消息类型：text（纯文本，详情链接自动可点）、image（封面 + 配文）、notify（系统通知卡）、error（错误提示）。
 * 头像按会话类型区分：电影港=胶片、磁力下载=磁铁。
 */
import RichText from './RichText.vue'
import NotifyCard from './NotifyCard.vue'
import { SubscribeMovie } from '@/api/chat'
import { formatFullTime } from '@/utils/chatFormat'

export default {
    name: 'MessageBubble',
    components: { RichText, NotifyCard },
    props: {
        message: {
            type: Object,
            required: true
        }
    },
    emits: ['open-cover', 'delete', 'subscribed'],
    data() {
        return {
            loadError: false,
            /** 手机端长按气泡：显示复制工具条（触屏没有 hover，工具条默认隐藏就永远点不到） */
            holdOpen: false,
            /** 本地订阅状态覆盖：服务端载荷里的 subscribed 只在渲染时读取，点按后立即切换 */
            subscribedLocal: null,
            busy: false
        }
    },
    computed: {
        isNotify() {
            return this.message.type === 'notify'
        },
        isImage() {
            return this.message.type === 'image' && !!this.message.content
        },
        isError() {
            return this.message.type === 'error'
        },
        isMine() {
            return this.message.role === 'user'
        },
        /** 载荷解析结果（影视卡片 / 磁力指令元信息），损坏时退化为空对象 */
        payload() {
            if (!this.message.payload) {
                return {}
            }
            try {
                const parsed = JSON.parse(this.message.payload)
                return parsed && typeof parsed === 'object' ? parsed : {}
            } catch (e) {
                return {}
            }
        },
        /** 机器人回复的电影条目（带影视卡片载荷）显示胶片头像 */
        isMovie() {
            return Object.prototype.hasOwnProperty.call(this.payload, 'url')
                && Object.prototype.hasOwnProperty.call(this.payload, 'title')
                && Object.prototype.hasOwnProperty.call(this.payload, 'cover')
        },
        /** 订阅状态：本地操作优先，其次是服务端下发的标记 */
        subscribed() {
            if (this.subscribedLocal !== null) {
                return this.subscribedLocal
            }
            return !!this.payload.subscribed
        },
        /** 磁力下载会话的机器人回复显示磁铁头像 */
        isMagnet() {
            return this.payload.kind === 'magnet'
        },
        /** 大图标题：优先用载荷里的片名 */
        coverTitle() {
            const payload = this.parsedPayload
            return payload.title || '影视封面'
        },
        /** 载荷解析（影视卡片字段 / 通知分类），供气泡内部使用 */
        parsedPayload() {
            return this.payload
        },
        fullTime() {
            return formatFullTime(this.message.createdAt)
        },
        /** 订阅请求体：与卡片载荷同形，片名/链接都由服务端再校验 */
        subscribePayload() {
            const payload = this.parsedPayload
            return {
                id: payload.id || null,
                title: payload.title || null,
                year: payload.year || null,
                origin: payload.origin || null,
                genre: payload.genre || null,
                cover: payload.cover || null,
                url: payload.url
            }
        },
        notifyMeta() {
            return {
                ...this.parsedPayload,
                time: formatFullTime(this.message.createdAt),
                body: this.message.content
            }
        }
    },
    watch: {
        'message.id'() {
            this.loadError = false
            this.subscribedLocal = null
            this.busy = false
        },
        'message.payload'() {
            this.subscribedLocal = null
        }
    },
    methods: {
        /** 长按 450ms 弹出工具栏（期间滑动/松手即取消，避免误触） */
        onHoldStart() {
            clearTimeout(this._holdTimer)
            this._holdTimer = setTimeout(() => {
                this.holdOpen = true
            }, 450)
        },
        endHold() {
            clearTimeout(this._holdTimer)
        },
        beforeUnmount() {
            clearTimeout(this._holdTimer)
        },
        async copy() {
            this.holdOpen = false
            const text = this.message.content || ''
            try {
                if (navigator.clipboard && window.isSecureContext) {
                    await navigator.clipboard.writeText(text)
                } else {
                    const el = document.createElement('textarea')
                    el.value = text
                    el.setAttribute('readonly', '')
                    el.style.cssText = 'position:fixed;top:-1000px;opacity:0'
                    document.body.appendChild(el)
                    el.select()
                    document.execCommand('copy')
                    document.body.removeChild(el)
                }
                window.$message?.success('已复制消息内容')
            } catch (e) {
                window.$message?.error('复制失败，请手动选择文本')
            }
        },
        openCover() {
            this.$emit('open-cover', { url: this.message.content, title: this.coverTitle })
        },
        /** 订阅该剧：落库片名 + 电影港具体链接，并把详情页每集磁力存为对比基线 */
        async onSubscribe() {
            if (this.subscribed || this.busy) {
                return
            }
            const url = this.parsedPayload.url
            if (!url) {
                window.$message?.error('这条卡片缺少电影港链接，无法订阅')
                return
            }
            this.busy = true
            try {
                const result = await SubscribeMovie(this.subscribePayload)
                this.subscribedLocal = true
                const message = result && result.message ? result.message : '订阅成功'
                window.$message?.success(message)
                this.$emit('subscribed', { url, subscription: result && result.subscription })
            } catch (e) {
                // 失败文案由 axios 拦截器提示
            } finally {
                this.busy = false
            }
        }
    }
}
</script>

<style scoped>
.message-row {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    align-items: flex-start;
}

.message-row.mine {
    flex-direction: row-reverse;
}

.avatar {
    width: 38px;
    height: 38px;
    flex: none;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
}

.avatar.bot {
    background: linear-gradient(135deg, var(--accent), #6366F1);
    color: #04121B;
    font-size: 16px;
}

.avatar.bot.movie {
    background: linear-gradient(135deg, #6366F1, #A855F7);
    color: #F8FAFC;
    font-size: 15px;
}

.avatar.bot.magnet {
    background: linear-gradient(135deg, #F59E0B, #EF4444);
    color: #1B1206;
    font-size: 14px;
}

.avatar.mine {
    background: rgba(99, 102, 241, .20);
    color: #C7D2FE;
    font-weight: 600;
}

.bubble {
    position: relative;
    max-width: min(68%, 720px);
    padding: 9px 12px;
    border-radius: 16px;
    border-bottom-left-radius: 4px;
    background: var(--bubble-bot, #1E293B);
}

/* 封面气泡：图片贴边、配文单独留白，视觉更接近海报卡 */
.bubble.is-cover {
    padding: 6px;
    max-width: min(46%, 320px);
}

.message-row.mine .bubble {
    background: var(--bubble-me, #0C4A6E);
    border-bottom-left-radius: 16px;
    border-bottom-right-radius: 4px;
}

.bubble.is-error {
    border: 1px solid rgba(244, 63, 94, .45);
}

.bubble-tools {
    position: absolute;
    top: -12px;
    right: 8px;
    display: none;
    gap: 2px;
    padding: 2px;
    border-radius: 8px;
    background: var(--card);
    border: 1px solid var(--line-2);
    box-shadow: 0 6px 20px rgba(0, 0, 0, .45);
    z-index: 2;
}

.message-row.mine .bubble-tools {
    right: auto;
    left: 8px;
}

.bubble:hover .bubble-tools {
    display: flex;
}

.tool {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-2);
    cursor: pointer;
    font-size: 12px;
}

.tool:hover {
    background: rgba(148, 163, 184, .14);
    color: var(--text);
}

.bubble-content {
    font-size: 14px;
}

.cover {
    display: block;
    width: 100%;
    max-height: 420px;
    object-fit: cover;
    border-radius: 11px;
    cursor: zoom-in;
    background: rgba(148, 163, 184, .08);
}

.cover-fallback {
    width: 100%;
    height: 150px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    border-radius: 11px;
    background: rgba(148, 163, 184, .08);
    color: var(--text-3);
    font-size: 12.5px;
}

.cover-caption {
    padding: 8px 6px 4px;
    font-size: 13px;
}

/* 影视卡片的订阅操作条：与配文同色系，点按后切换成「已订阅」静止态 */
.movie-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 6px 4px;
    flex-wrap: wrap;
}

.subscribe-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border-radius: 999px;
    border: 1px solid rgba(99, 102, 241, .55);
    background: rgba(99, 102, 241, .16);
    color: #C7D2FE;
    font-family: inherit;
    font-size: 12.5px;
    font-weight: 600;
    cursor: pointer;
    transition: background .15s, border-color .15s, color .15s;
}

.subscribe-btn:hover:not(:disabled) {
    background: rgba(99, 102, 241, .28);
    border-color: rgba(129, 140, 248, .8);
    color: #E0E7FF;
}

.subscribe-btn.subscribed,
.subscribe-btn:disabled {
    cursor: default;
    border-color: rgba(74, 222, 128, .45);
    background: rgba(34, 197, 94, .14);
    color: #86EFAC;
}

.subscribe-btn.busy {
    opacity: .75;
}

.subscribe-tip {
    font-size: 11.5px;
    color: var(--text-3);
}

/* ==========================================================================
   手机端：气泡更宽、字号更大，复制工具条由「长按」唤出（触屏无 hover）
   ========================================================================== */
@media (max-width: 900px) {
    .message-row {
        gap: 7px;
        margin-bottom: 10px;
    }

    .avatar {
        width: 36px;
        height: 36px;
    }

    .bubble {
        max-width: min(76%, 720px);
        padding: 10px 13px;
        border-radius: 17px;
    }

    .bubble.is-cover {
        max-width: min(62%, 300px);
    }

    .bubble-content {
        font-size: 15px;
    }

    .bubble.hold-open .bubble-tools {
        display: flex;
    }

    .tool {
        width: 30px;
        height: 30px;
        font-size: 13px;
    }

    .bubble-tools {
        top: -14px;
        box-shadow: 0 10px 26px rgba(0, 0, 0, .55);
    }

    .subscribe-btn {
        padding: 7px 14px;
        font-size: 13px;
    }
}
</style>
