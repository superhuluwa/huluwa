<template>
    <!-- 系统通知卡：独立左对齐卡片，不带头像 -->
    <div v-if="isNotify" class="message-row">
        <NotifyCard :meta="notifyMeta" deletable @delete="$emit('delete', message)" />
    </div>

    <!-- 普通气泡 -->
    <div v-else class="message-row" :class="{ mine: isMine }">
        <div v-if="!isMine" class="avatar bot"
            :class="{ movie: isMovie, magnet: isMagnet, video: isVideoSession, xunlei: isXunleiSession }">
            <i v-if="isMovie" class="fa-solid fa-film"></i>
            <i v-else-if="isMagnet" class="fa-solid fa-magnet"></i>
            <i v-else-if="isVideoSession" class="fa-solid fa-clapperboard"></i>
            <i v-else-if="isXunleiSession" class="fa-solid fa-bolt"></i>
            <template v-else>Q</template>
        </div>
        <div v-if="isMine" class="avatar mine">我</div>
        <div class="bubble" :class="{ 'is-cover': isImage, 'is-video': isVideo, 'is-error': isError, 'hold-open': holdOpen }"
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
                        <button class="magnet-btn" type="button" :disabled="magnetsLoading" @click="openMagnets">
                            <i :class="magnetsLoading ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-magnet'"></i>
                            查看磁力
                        </button>
                        <button class="magnet-btn" type="button" @click="subscriptionDialog = true">
                            <i class="fa-solid fa-sliders"></i>订阅设置
                        </button>
                        <span class="subscribe-tip">{{ subscribed ? '每小时巡检更新' : '监听新增剧集并自动下载' }}</span>
                    </div>
                </template>
                <!-- 视频气泡（视频解析会话）：封面 + 内联播放，点按在气泡内播放 -->
                <VideoBubble v-else-if="isVideo" :message="message" @open-cover="$emit('open-cover', $event)" />
                <template v-else>
                    <RichText :text="message.content" />
                    <div v-if="isMovie" class="movie-actions">
                        <button class="subscribe-btn" type="button" :class="{ subscribed: subscribed, busy }"
                            :disabled="subscribed || busy" @click="onSubscribe">
                            <i :class="subscribed ? 'fa-solid fa-bell' : 'fa-regular fa-bell'"></i>
                            {{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}
                        </button>
                        <button class="magnet-btn" type="button" :disabled="magnetsLoading" @click="openMagnets">
                            <i :class="magnetsLoading ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-magnet'"></i>
                            查看磁力
                        </button>
                        <button class="magnet-btn" type="button" @click="subscriptionDialog = true">
                            <i class="fa-solid fa-sliders"></i>订阅设置
                        </button>
                        <span class="subscribe-tip">{{ subscribed ? '每小时巡检更新' : '监听新增剧集并自动下载' }}</span>
                    </div>
                </template>
            </div>
        </div>
        <!-- 发送状态（mine 行 row-reverse，DOM 在 bubble 之后 = 视觉在气泡左侧）：
             pending 转圈等 ack；failed 感叹号点击重发 -->
        <span v-if="isPending" class="msg-status" title="发送中…"><i class="fa-solid fa-spinner fa-spin"></i></span>
        <button v-else-if="isFailed" class="msg-status failed" type="button" title="发送失败，点击重发"
            @click="$emit('resend', message)"><i class="fa-solid fa-circle-exclamation"></i></button>
    </div>

    <MovieSubscriptionDialog v-model:show="subscriptionDialog" :movie="subscribePayload"
        :resubscribe="!subscribed" @saved="onSubscriptionSaved" />

    <!-- 磁力列表弹窗：详情页实时抓取，行内「下载」使用本剧选择的下载工具 -->
    <MagnetListDialog v-model:show="magnetDialog.show" :url="magnetDialog.url" :title="magnetDialog.title"
        @downloaded="onMagnetDownloaded" />
</template>

<script>
/**
 * 消息气泡外壳（自 quantum-web 的 MessageBubble.vue 迁移）：
 * 头像 38px、机器人左/用户右、靠头像侧下角 4px 收尾、圆角 16、内边距 9/12、最大宽 min(68%,720px)。
 * 本项目消息类型：text（纯文本，详情链接自动可点）、image（封面 + 配文）、video（视频封面 + 内联播放）、
 * notify（系统通知卡）、error（错误提示）。
 * 头像按会话类型区分：电影港=胶片、磁力下载=磁铁、视频解析=场记板。
 */
import RichText from './RichText.vue'
import NotifyCard from './NotifyCard.vue'
import MagnetListDialog from './MagnetListDialog.vue'
import VideoBubble from './VideoBubble.vue'
import MovieSubscriptionDialog from './MovieSubscriptionDialog.vue'
import { PreviewMagnets } from '@/api/chat'
import { formatFullTime } from '@/utils/chatFormat'

export default {
    name: 'MessageBubble',
    components: { RichText, NotifyCard, MagnetListDialog, VideoBubble, MovieSubscriptionDialog },
    props: {
        message: {
            type: Object,
            required: true
        }
    },
    emits: ['open-cover', 'delete', 'subscribed', 'magnet-downloaded', 'resend'],
    data() {
        return {
            loadError: false,
            /** 手机端长按气泡：显示复制工具条（触屏没有 hover，工具条默认隐藏就永远点不到） */
            holdOpen: false,
            /** 本地订阅状态覆盖：服务端载荷里的 subscribed 只在渲染时读取，点按后立即切换 */
            subscribedLocal: null,
            busy: false,
            subscriptionDialog: false,
            /** 「查看磁力」按钮的加载态（抓详情页较慢，避免重复点击） */
            magnetsLoading: false,
            /** 磁力列表弹窗状态 */
            magnetDialog: { show: false, url: '', title: '' }
        }
    },
    computed: {
        isNotify() {
            return this.message.type === 'notify'
        },
        isImage() {
            return this.message.type === 'image' && !!this.message.content
        },
        /** 视频气泡：type=video 且正文是直链（视频解析会话的视频 / 实况照片） */
        isVideo() {
            return this.message.type === 'video' && !!this.message.content
        },
        isError() {
            return this.message.type === 'error'
        },
        isMine() {
            return this.message.role === 'user'
        },
        /** 本地乐观发送态：等待服务端 ack（WS chat.ack / HTTP 响应） */
        isPending() {
            return this.isMine && this.message.status === 'pending'
        },
        /** 发送失败：点击重发（resend 事件交给页面层换新 requestId 重发） */
        isFailed() {
            return this.isMine && this.message.status === 'failed'
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
        /** 机器人回复的电影条目（带影视卡片载荷）显示胶片头像；视频解析会话的载荷不算影视卡片 */
        isMovie() {
            const payload = this.payload
            return payload.kind !== 'video'
                && Object.prototype.hasOwnProperty.call(payload, 'url')
                && Object.prototype.hasOwnProperty.call(payload, 'title')
                && Object.prototype.hasOwnProperty.call(payload, 'cover')
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
        /** 视频解析会话的机器人回复显示场记板头像 */
        isVideoSession() {
            return this.payload.kind === 'video'
        },
        /** 迅雷下载会话的机器人回复显示闪电头像 */
        isXunleiSession() {
            return this.payload.kind === 'xunlei'
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
        /**
         * 打开「查看磁力」弹窗：弹窗内部自己去抓详情页（带 loading 与缓存状态），
         * 这里只做一次轻量预检，保证卡片上有可用链接。
         */
        async openMagnets() {
            const url = this.parsedPayload.url
            if (!url) {
                window.$message?.error('这条卡片缺少电影港链接，无法查看磁力')
                return
            }
            this.magnetDialog = {
                show: true,
                url,
                title: this.parsedPayload.title || ''
            }
        },
        /** 手动下载成功：通知父级（会话页可据此刷新下载态） */
        onMagnetDownloaded(result) {
            this.$emit('magnet-downloaded', result)
        },
        /** 订阅该剧：落库片名 + 电影港具体链接，并把详情页每集磁力存为对比基线 */
        onSubscribe() {
            if (this.subscribed || this.busy) return
            if (!this.parsedPayload.url) {
                window.$message?.error('这条卡片缺少电影港链接，无法订阅')
                return
            }
            this.subscriptionDialog = true
        },
        onSubscriptionSaved(result) {
            this.subscribedLocal = !!result.subscription?.enabled
            this.$emit('subscribed', { url: this.parsedPayload.url, subscription: result.subscription })
        }
    }
}
</script>

<style scoped>
.message-row {
    display: flex;
    /* 头像与气泡的水平间距由消息中心统一下发（左右两侧一致） */
    gap: var(--chat-avatar-gap, 10px);
    margin-bottom: 8px;
    align-items: flex-start;
    /* 行本身不许被子元素（超宽视频/封面）撑破，否则头像列与右侧留白会被挤掉 */
    min-width: 0;
    /* 极窄屏（如 320px）下长内容也不能把行撑出聊天区 */
    max-width: 100%;
    box-sizing: border-box;
    /* 左右头像列总占宽 = 2 × 头像(38px) + 2 × 间距(10px)；
       气泡可用宽度 = 100% − 这个值，保证长消息不会伸进对侧头像列。 */
    --chat-rail-both: 96px;
}

.message-row.mine {
    flex-direction: row-reverse;
}

/* 发送状态位（在气泡左侧）：pending 转圈 / failed 感叹号点击重发 */
.msg-status {
    flex: none;
    margin-top: 11px;
    font-size: 13px;
    color: var(--text-3);
}

.msg-status.failed {
    border: 0;
    padding: 0;
    background: none;
    color: #F43F5E;
    cursor: pointer;
    font-size: 15px;
}

.msg-status.failed:hover {
    opacity: .8;
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

.avatar.bot.video {
    background: linear-gradient(135deg, #06B6D4, #3B82F6);
    color: #F8FAFC;
    font-size: 15px;
}

.avatar.bot.xunlei {
    background: linear-gradient(135deg, #00C6FB, #005BEA);
    color: #04121B;
    font-size: 15px;
}

.avatar.mine {
    background: rgba(99, 102, 241, .20);
    color: #C7D2FE;
    font-weight: 600;
}

.bubble {
    position: relative;
    /* 项目没有全局 border-box：少了它 max-width 只算内容宽，
       左右内边距会额外多占 20~26px，长消息气泡就会越进对侧头像列。 */
    box-sizing: border-box;
    /* 兜底：老 WebView 不认 min()（表现为整条 max-width 失效、气泡按内容撑开）。
       calc + var 在该内核可用，扣除左右头像列后气泡在任何屏宽都不会越界。 */
    max-width: calc(100% - var(--chat-rail-both, 96px));
    /* 现代内核：再取「68% / 720px」的更严上限 */
    max-width: min(68%, calc(100% - var(--chat-rail-both, 96px)), 720px);
    padding: 9px 12px;
    border-radius: 16px;
    border-bottom-left-radius: 4px;
    background: var(--bubble-bot, #1E293B);
    /* 允许在 flex 行内收缩到 max-width 以下：
       没有这个约束时，无空格长串（磁力 / 长 URL）会把气泡按内容宽撑开，
       越过右侧留白甚至压到对侧头像列。 */
    min-width: 0;
}

/* 封面气泡：图片贴边、配文单独留白，视觉更接近海报卡（border-box 见 .bubble） */
.bubble.is-cover {
    padding: 6px;
    max-width: calc(100% - var(--chat-rail-both, 96px));
    max-width: min(46%, calc(100% - var(--chat-rail-both, 96px)), 320px);
}

/* 视频气泡：播放器要够宽（320px 播放区），但比整块消息流窄（border-box 见 .bubble） */
.bubble.is-video {
    padding: 6px;
    max-width: calc(100% - var(--chat-rail-both, 96px));
    max-width: min(62%, calc(100% - var(--chat-rail-both, 96px)), 360px);
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
    /* 正文容器同样允许收缩，把宽度约束传给 RichText */
    min-width: 0;
    max-width: 100%;
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

/* 「查看磁力」按钮：与订阅按钮同排，仅影视卡片出现 */
.magnet-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 12px;
    border-radius: 8px;
    border: 1px solid rgba(96, 165, 250, .4);
    background: rgba(96, 165, 250, .10);
    color: var(--accent);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
    transition: background .15s, border-color .15s;
}

.magnet-btn:hover:not(:disabled) {
    background: rgba(96, 165, 250, .18);
    border-color: rgba(96, 165, 250, .6);
}

.magnet-btn:disabled {
    opacity: .55;
    cursor: not-allowed;
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
        margin-bottom: 10px;
    }

    .avatar {
        width: 36px;
        height: 36px;
    }

    .message-row {
        /* 手机端头像 36px + 间距 10px，左右两列合计 92px */
        --chat-rail-both: 92px;
    }

    .bubble {
        /* 兜底：老 WebView 不认 min() 时用 calc 扣除左右头像列，任何屏宽都不越界 */
        max-width: calc(100% - var(--chat-rail-both, 92px));
        /* 现代内核：取「74% / 扣除头像列 / 720px」的最严值 */
        max-width: min(74%, calc(100% - var(--chat-rail-both, 92px)), 720px);
        padding: 10px 13px;
        border-radius: 17px;
    }

    .bubble.is-cover {
        max-width: calc(100% - var(--chat-rail-both, 92px));
        max-width: min(62%, calc(100% - var(--chat-rail-both, 92px)), 300px);
    }

    /* 视频气泡：与普通气泡同一套上限，卡片不能一路顶到对侧头像列 */
    .bubble.is-video {
        max-width: calc(100% - var(--chat-rail-both, 92px));
        max-width: min(74%, calc(100% - var(--chat-rail-both, 92px)), 360px);
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
