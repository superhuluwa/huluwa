<template>
    <!-- 系统通知卡：独立左对齐卡片，不带头像 -->
    <div v-if="isNotify" class="message-row">
        <NotifyCard :meta="notifyMeta" deletable @delete="$emit('delete', message)" />
    </div>

    <!-- 普通气泡 -->
    <div v-else class="message-row" :class="{ mine: isMine }">
        <div v-if="!isMine" class="avatar bot"
            :class="{ movie: isMovie || isMovieSearchWatch, dytt: isDytt, magnet: isMagnet, video: isVideoSession, xunlei: isXunleiSession, 'xunlei-win': isXunleiWinSession }">
            <SessionIcon :kind="botIconKind" fallback-text="Q" />
        </div>
        <div v-if="isMine" class="avatar mine">我</div>
        <div class="bubble"
            :class="{ 'is-cover': isImage, 'is-cover-wide': isImage && hasCoverCaption, 'is-gallery': isGallery, 'is-video': isVideo, 'is-error': isError, 'has-corner-action': (isMovie || isDytt) && isImage, 'hold-open': holdOpen }"
            :title="fullTime" @touchstart.passive="onHoldStart" @touchend="endHold" @touchcancel="endHold"
            @touchmove.passive="endHold">
            <div class="bubble-tools">
                <span class="tool" title="复制" @click="copy"><i class="fa-regular fa-copy"></i></span>
            </div>
            <!-- 电影港卡片：订阅按钮固定在卡片右上角（极简文字，浮在封面之上） -->
            <button v-if="isMovie && isImage" class="subscribe-corner" type="button"
                :class="{ subscribed, busy }" :disabled="subscribed || busy"
                :title="subscribed ? '已订阅' : '订阅该剧'"
                @click.stop="onSubscribe">
                <i :class="busy ? 'fa-solid fa-spinner fa-spin' : (subscribed ? 'fa-solid fa-bell' : 'fa-solid fa-plus')"></i>
                <span>{{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}</span>
            </button>
            <!-- 电影天堂卡片：订阅按钮同样固定在卡片右上角（极简文字，浮在封面之上） -->
            <button v-if="isDytt && isImage" class="subscribe-corner" type="button"
                :class="{ subscribed, busy }" :disabled="subscribed || busy"
                :title="subscribed ? '已订阅' : '订阅该剧，新剧集自动下载'"
                @click.stop="onHlsSubscribe">
                <i :class="busy ? 'fa-solid fa-spinner fa-spin' : (subscribed ? 'fa-solid fa-bell' : 'fa-solid fa-plus')"></i>
                <span>{{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}</span>
            </button>
            <div class="bubble-content">
                <!-- 图片气泡（影视封面 / 图片消息）：封面 + 配文，点击封面看大图 -->
                <ImageGallery v-if="isGallery" :key="message.id" :images="galleryImages" :caption="message.contentText"
                    @open-cover="$emit('open-cover', $event)" />
                <template v-else-if="isImage">
                    <div v-if="loadError" class="cover-fallback">
                        <i class="fa-solid fa-image"></i>
                        <span>封面加载失败</span>
                    </div>
                    <img v-else class="cover" :src="message.content" alt="封面" @error="loadError = true"
                        @click="openCover" />
                    <RichText class="cover-caption" :text="message.contentText" :support-fold="false" />
                    <!-- 电影港卡片：订阅按钮在卡片右上角，操作条只留资源列表 / 订阅设置 -->
                    <div v-if="isMovie" class="movie-actions">
                        <button class="magnet-btn" type="button" :disabled="magnetsLoading" @click="openMagnets">
                            <i :class="magnetsLoading ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-magnet'"></i>
                            资源列表
                        </button>
                        <button class="magnet-btn" type="button" @click="subscriptionDialog = true">
                            <i class="fa-solid fa-sliders"></i>订阅设置
                        </button>
                    </div>
                    <!-- 电影天堂卡片：在线播放线路（点击查看完整资源列表 / 复制直链） -->
                    <div v-else-if="isDytt" class="movie-actions">
                        <button class="magnet-btn" type="button" @click="openPlays">
                            <i class="fa-solid fa-circle-play"></i>
                            资源列表<template v-if="playCount">（{{ playCount }}）</template>
                        </button>
                        <a v-if="parsedPayload.url" class="magnet-btn" :href="parsedPayload.url" target="_blank"
                            rel="noopener">
                            <i class="fa-solid fa-up-right-from-square"></i>详情页
                        </a>
                    </div>
                </template>
                <!-- 视频气泡（视频解析会话）：封面 + 内联播放，点按在气泡内播放 -->
                <VideoBubble v-else-if="isVideo" :message="message" @open-cover="$emit('open-cover', $event)" />
                <template v-else>
                    <RichText :text="message.content" />
                    <MovieSearchWatchAction v-if="isMovieSearchWatch" :keyword="movieWatchKeyword" />
                    <!-- 电影港卡片：订阅 + 磁力 + 订阅设置 -->
                    <div v-if="isMovie" class="movie-actions">
                        <button class="subscribe-btn" type="button" :class="{ subscribed: subscribed, busy }"
                            :disabled="subscribed || busy" @click="onSubscribe">
                            <i :class="busy ? 'fa-solid fa-spinner fa-spin' : (subscribed ? 'fa-solid fa-bell' : 'fa-solid fa-plus')"></i>
                            {{ subscribed ? '已订阅' : (busy ? '订阅中…' : '订阅') }}
                        </button>
                        <button class="magnet-btn" type="button" :disabled="magnetsLoading" @click="openMagnets">
                            <i :class="magnetsLoading ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-magnet'"></i>
                            资源列表
                        </button>
                        <button class="magnet-btn" type="button" @click="subscriptionDialog = true">
                            <i class="fa-solid fa-sliders"></i>订阅设置
                        </button>
                    </div>
                    <!-- 电影天堂卡片：在线播放线路（点击查看完整资源列表 / 复制直链） -->
                    <div v-else-if="isDytt" class="movie-actions">
                        <button class="magnet-btn" type="button" @click="openPlays">
                            <i class="fa-solid fa-circle-play"></i>
                            资源列表<template v-if="playCount">（{{ playCount }}）</template>
                        </button>
                        <a v-if="parsedPayload.url" class="magnet-btn" :href="parsedPayload.url" target="_blank"
                            rel="noopener">
                            <i class="fa-solid fa-up-right-from-square"></i>详情页
                        </a>
                        <button class="subscribe-btn" type="button" :class="{ subscribed, busy }"
                            :disabled="subscribed || busy" @click="onHlsSubscribe">
                            <i :class="busy ? 'fa-solid fa-spinner fa-spin' : (subscribed ? 'fa-solid fa-bell' : 'fa-solid fa-plus')"></i>
                            {{ subscribed ? '已订阅' : busy ? '订阅中…' : '订阅' }}
                        </button>
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

    <!-- 电影天堂资源列表弹窗：卡片载荷里的在线线路，点击复制 m3u8 直链 -->
    <PlaySourceDialog v-model:show="playDialog" :title="parsedPayload.title || ''"
        :sources="parsedPayload.playSources || []" :movie="parsedPayload" />

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
import SessionIcon from './SessionIcon.vue'
import MagnetListDialog from './MagnetListDialog.vue'
import VideoBubble from './VideoBubble.vue'
import ImageGallery from './ImageGallery.vue'
import MovieSubscriptionDialog from './MovieSubscriptionDialog.vue'
import PlaySourceDialog from './PlaySourceDialog.vue'
import MovieSearchWatchAction from './MovieSearchWatchAction.vue'
import { PreviewMagnets } from '@/api/chat'
import { QuickSubscribeHls } from '@/api/hls'
import { useHlsSubscriptionsStore } from '@/stores/hlsSubscriptions'
import { formatFullTime } from '@/utils/chatFormat'

export default {
    name: 'MessageBubble',
    setup() {
        return { hlsSubscriptions: useHlsSubscriptionsStore() }
    },
    components: { RichText, SessionIcon, NotifyCard, MagnetListDialog, VideoBubble, ImageGallery, MovieSubscriptionDialog, PlaySourceDialog, MovieSearchWatchAction },
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
            /** 「资源列表」按钮的加载态（抓详情页较慢，避免重复点击） */
            magnetsLoading: false,
            /** 磁力列表弹窗状态 */
            magnetDialog: { show: false, url: '', title: '' },
            /** 电影天堂资源列表弹窗状态 */
            playDialog: false
        }
    },
    computed: {
        isNotify() {
            return this.message.type === 'notify'
        },
        isImage() {
            return this.message.type === 'image' && !!this.message.content
        },
        galleryImages() {
            if (this.payload.kind !== 'video' || !Array.isArray(this.payload.images)) return []
            return this.payload.images.map(image => ({
                url: typeof image === 'string' ? image : image?.url,
                livePhotoUrl: typeof image === 'object' ? image?.livePhotoUrl || image?.live_photo_url || '' : ''
            })).filter(image => typeof image.url === 'string' && image.url.trim())
        },
        isGallery() {
            return this.isImage && this.galleryImages.length > 1
        },
        /** 封面卡是否有配文：有配文的卡片铺到右侧头像栏前，纯图（图集后续张）仍保持窄图卡 */
        hasCoverCaption() {
            return String(this.message.contentText || '').trim().length > 0
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
        /** 机器人头像的会话类型：决定用哪个官方品牌图标（无品牌时返回空，退回 Q） */
        botIconKind() {
            if (this.isMovie || this.isMovieSearchWatch) {
                return 'movie'
            }
            if (this.isDytt) {
                return 'dytt'
            }
            if (this.isMagnet) {
                return 'magnet'
            }
            if (this.isVideoSession) {
                return 'video'
            }
            if (this.isXunleiSession) {
                return 'xunlei'
            }
            if (this.isXunleiWinSession) {
                return 'xunlei-win'
            }
            return ''
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
        /** 只在电影港搜索无结果时展示监听；兼容旧版没有结构化载荷的无结果回复。 */
        movieWatchKeyword() {
            if (this.message.sessionId !== 'movie' || this.message.role !== 'bot') return ''
            if (this.payload.kind === 'movie-search-watch' && this.payload.noResults === true) {
                return String(this.payload.keyword || '').trim()
            }
            const match = String(this.message.content || '').match(/^未搜索到「([\s\S]+)」相关资源/)
            return match ? match[1].trim() : ''
        },
        isMovieSearchWatch() {
            return !!this.movieWatchKeyword
        },
        /** 载荷是否是影视卡片（电影港 / 电影天堂共用 url + title + cover 三件套） */
        isMovieCard() {
            const payload = this.payload
            return payload.kind !== 'video'
                && Object.prototype.hasOwnProperty.call(payload, 'url')
                && Object.prototype.hasOwnProperty.call(payload, 'title')
                && Object.prototype.hasOwnProperty.call(payload, 'cover')
        },
        /** 电影港条目（可订阅、有磁力）：显示胶片头像与订阅 / 磁力按钮 */
        isMovie() {
            return this.isMovieCard && this.payload.source !== 'dytt'
        },
        /** 电影天堂条目（在线 m3u8，无磁力）：显示播放头像与资源列表按钮 */
        isDytt() {
            return this.isMovieCard && this.payload.source === 'dytt'
        },
        /** 电影天堂卡片的播放条目总数（用于按钮角标） */
        playCount() {
            const sources = this.payload.playSources
            if (!Array.isArray(sources)) {
                return 0
            }
            return sources.reduce((sum, s) => sum + ((s && s.items) ? s.items.length : 0), 0)
        },
        /**
         * 订阅状态：电影天堂卡片以实时订阅集合为准（在「我的订阅」里删除 / 恢复后立即一致），
         * 集合尚未加载或不是电影天堂卡片时，退回本地乐观值 / 载荷快照。
         */
        subscribed() {
            if (this.isDytt && this.hlsSubscriptions.loaded) {
                const resId = String(this.parsedPayload.id || '')
                if (resId) {
                    return this.hlsSubscriptions.has(resId)
                }
            }
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
        /** 迅雷下载【飞牛】会话的机器人回复显示闪电头像 */
        isXunleiSession() {
            return this.payload.kind === 'xunlei'
        },
        /** 迅雷下载【Win】会话的机器人回复显示迅雷头像 */
        isXunleiWinSession() {
            return this.payload.kind === 'xunlei-win'
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
    mounted() {
        // 电影天堂卡片的订阅状态以实时集合为准：这样在「我的订阅」里删除 / 恢复后，
        // 历史搜索卡片不会再停留在「已订阅」。请求失败只留空集合，退回载荷快照。
        if (this.isDytt) {
            this.hlsSubscriptions.ensureLoaded().catch(() => {})
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
         * 打开「资源列表」弹窗：弹窗内部自己去抓详情页（带 loading 与缓存状态），
         * 这里只做一次轻量预检，保证卡片上有可用链接。
         */
        async openMagnets() {
            const url = this.parsedPayload.url
            if (!url) {
                window.$message?.error('这条卡片缺少电影港链接，无法打开资源列表')
                return
            }
            this.magnetDialog = {
                show: true,
                url,
                title: this.parsedPayload.title || ''
            }
        },
        /** 打开电影天堂资源列表弹窗（数据已在卡片载荷里，无需请求） */
        openPlays() {
            this.playDialog = true
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
        async onHlsSubscribe() {
            if (this.subscribed || this.busy) return
            this.busy = true
            try {
                const result = await QuickSubscribeHls({ resId: String(this.parsedPayload.id || ''), apiUrl: this.parsedPayload.apiUrl })
                this.onSubscriptionSaved(result)
                window.$message?.success(result.message || '已订阅，只下载后续新增剧集')
            } catch (error) {
                // 请求拦截器已提示，失败后仍可再次点击订阅。
            } finally {
                this.busy = false
            }
        },
        onSubscriptionSaved(result) {
            this.subscribedLocal = !!result.subscription?.enabled
            // 同步实时集合：同一会话里同作品的其它卡片立即跟随，无需等下次进入会话
            if (this.isDytt) {
                this.hlsSubscriptions.mark(this.parsedPayload.id, !!result.subscription?.enabled)
            }
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
    --chat-cover-width-ratio: 0.46;
    --chat-cover-width-limit: 320px;
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
    color: var(--danger);
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

/* 内置会话的机器人头像改用各服务官方图标（SessionIcon）：
   品牌图标是方形应用图标，容器用圆角方形；无品牌图标的会话（默认机器人 / 系统）
   保留圆形渐变底 + Font Awesome 图标。 */
.avatar.bot {
    background: linear-gradient(135deg, var(--pine), #557C46);
    color: #fff;
    font-size: 16px;
    overflow: hidden;
}

.avatar.bot.movie,
.avatar.bot.dytt,
.avatar.bot.magnet,
.avatar.bot.video,
.avatar.bot.xunlei,
.avatar.bot.xunlei\-win {
    border-radius: 10px;
    background: transparent;
}

.avatar.mine {
    background: rgba(32, 108, 84, .14);
    color: var(--pine-dark);
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

/* 封面气泡：纯图（无配文）仍是一张窄图卡，宽度跟着图片走（border-box 见 .bubble） */
.bubble.is-cover {
    padding: 6px;
    max-width: calc(100% - var(--chat-rail-both, 96px));
    max-width: min(calc(100% * var(--chat-cover-width-ratio)), calc(100% - var(--chat-rail-both, 96px)), var(--chat-cover-width-limit));
}

/* 带配文的卡片：正文与操作条铺满两侧头像栏之间的可用宽度。 */
.bubble.is-cover.is-cover-wide {
    width: calc(100% - var(--chat-rail-both, 96px));
    max-width: calc(100% - var(--chat-rail-both, 96px));
}

.bubble.is-cover.is-gallery {
    width: 320px;
    max-width: calc(100% - var(--chat-rail-both, 96px));
}

/* 视频气泡：播放器要够宽（320px 播放区），但比整块消息流窄（border-box 见 .bubble） */
.bubble.is-video {
    padding: 6px;
    max-width: calc(100% - var(--chat-rail-both, 96px));
    max-width: min(62%, calc(100% - var(--chat-rail-both, 96px)), 360px);
}

.message-row.mine .bubble {
    background: var(--bubble-me, #DFEBD4);
    border-color: #D3E2C6;
    border-bottom-left-radius: 16px;
    border-bottom-right-radius: 4px;
}

.bubble.is-error {
    border: 1px solid rgba(180, 68, 82, .45);
}

.bubble-tools {
    position: absolute;
    top: -12px;
    right: 8px;
    display: none;
    gap: 2px;
    padding: 2px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid var(--line-2);
    box-shadow: 0 6px 20px rgba(36, 61, 50, .16);
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

/* 卡片正文宽度 + 两侧头像栏 + 左右 6px 内边距 = 消息行宽度；
   按原封面卡片的比例与上限还原图片宽度，再将宽高同步缩到 2/3。 */
.bubble.is-cover-wide .cover,
.bubble.is-cover-wide .cover-fallback {
    width: calc(((100% + var(--chat-rail-both) + 12px) * var(--chat-cover-width-ratio) - 12px) * 2 / 3);
    max-width: calc((var(--chat-cover-width-limit) - 12px) * 2 / 3);
    max-width: min(calc(100% * 2 / 3), calc((var(--chat-cover-width-limit) - 12px) * 2 / 3));
}

.bubble.is-cover-wide .cover {
    height: auto;
    max-height: 280px;
}

.bubble.is-cover-wide .cover-fallback {
    height: 100px;
}

.cover-caption {
    box-sizing: border-box;
    width: 100%;
    padding: 8px 6px 4px;
    font-size: 13px;
}

/* 「资源列表」按钮：与订阅按钮同排，仅影视卡片出现 */
.magnet-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 12px;
    border-radius: 8px;
    border: 1px solid rgba(32, 108, 84, .28);
    background: rgba(32, 108, 84, .07);
    color: var(--pine);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
    transition: background .15s, border-color .15s;
}

.magnet-btn:hover:not(:disabled) {
    background: var(--soft-green);
    border-color: rgba(32, 108, 84, .45);
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

/* 无封面回退卡片：订阅按钮与右上角同一套极简文字（无底无框，悬浮才浮出浅底） */
.subscribe-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 10px;
    border: 0;
    border-radius: 9px;
    background: none;
    color: var(--pine);
    font-family: inherit;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: background .15s, color .15s;
}

.subscribe-btn:hover:not(:disabled) {
    background: var(--soft-green);
    color: var(--pine-dark);
}

.subscribe-btn:disabled {
    cursor: default;
}

.subscribe-btn.subscribed {
    color: var(--muted);
}

.subscribe-btn.busy {
    opacity: .82;
}


/* 电影港 / 电影天堂卡片：订阅按钮固定在卡片右上角（极简文字，无底无框，悬浮才浮出浅底） */
.subscribe-corner {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 3;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 10px;
    border: 0;
    border-radius: 9px;
    background: rgba(255, 255, 255, .82);
    color: var(--pine);
    font-family: inherit;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    backdrop-filter: blur(6px);
    box-shadow: 0 2px 10px rgba(36, 61, 50, .20);
    transition: background .15s, color .15s;
}

.subscribe-corner:hover:not(:disabled) {
    background: #fff;
    color: var(--pine-dark);
}

.subscribe-corner:disabled {
    cursor: default;
}

.subscribe-corner.subscribed {
    color: var(--muted);
}

.subscribe-corner.busy {
    opacity: .82;
}

/* 卡片右上角有订阅按钮时，「复制」工具条让位到左上角，避免两个操作挤在一起 */
.bubble.has-corner-action .bubble-tools {
    right: auto;
    left: 8px;
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
        --chat-cover-width-ratio: 0.62;
        --chat-cover-width-limit: 300px;
    }

    .bubble {
        /* 兜底：老 WebView 不认 min() 时用 calc 扣除左右头像列，任何屏宽都不越界 */
        max-width: calc(100% - var(--chat-rail-both, 92px));
        /* 现代内核：取「74% / 扣除头像列 / 720px」的最严值 */
        max-width: min(74%, calc(100% - var(--chat-rail-both, 92px)), 720px);
        padding: 10px 13px;
        border-radius: 17px;
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

    .subscribe-corner {
        top: 8px;
        right: 8px;
        min-height: 40px;
        padding: 9px 12px;
        font-size: 14px;
    }
}
</style>
