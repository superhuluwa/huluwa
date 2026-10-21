<template>
    <div class="video-bubble">
        <!-- 内联播放器：点封面 / 紧凑行后在气泡内原位展开（不弹窗，与 quantum 的 MediaBubble 一致） -->
        <div v-if="active" class="inline-video">
            <div v-if="error" class="video-fallback">
                <div class="fallback-text">{{ error }}</div>
                <div class="fallback-actions">
                    <button class="inline-btn" type="button" @click="play">重试</button>
                    <button class="inline-btn" type="button" @click="download">下载查看</button>
                </div>
            </div>
            <video v-else ref="videoEl" class="video-el" :src="videoUrl" :poster="posterUrl" controls autoplay
                playsinline @error="onVideoError" />
            <button class="collapse-btn" type="button" title="收起播放器" @click="close">
                <i class="fa-solid fa-chevron-up"></i>
            </button>
        </div>

        <!-- 未播放：有封面用封面卡，没有封面退化为紧凑行 -->
        <template v-else>
            <div v-if="posterUrl" class="video-cover" @click="play">
                <img :src="posterUrl" alt="视频封面" @error="posterError = true" />
                <span class="video-play"><i class="fa-solid fa-play"></i></span>
                <span v-if="durationLabel" class="video-duration">{{ durationLabel }}</span>
            </div>
            <div v-else class="video-compact" @click="play">
                <span class="compact-play"><i class="fa-solid fa-play"></i></span>
                <div class="compact-body">
                    <div class="compact-title">{{ compactTitle }}</div>
                    <div class="compact-hint">点按播放</div>
                </div>
            </div>
        </template>

        <div v-if="caption && posterUrl" class="video-caption">
            <RichText :text="caption" :support-fold="false" />
        </div>
    </div>
</template>

<script>
/**
 * 视频气泡（自 quantum-web 的 MediaBubble.vue 视频分支迁移，去掉了站内鉴权与音频/文件分支）：
 * - 有封面 → 封面卡（居中播放按钮）；无封面 → 紧凑行「点按播放」；
 * - 点按后在气泡内联播放（原生控件，autoplay + playsinline），不弹窗、不跳转；
 * - 解码失败给「重试 / 下载查看」两个兜底动作（抖音直链有时限，过期后需要重新解析）；
 * - 播放器在气泡内挂载/卸载：切走会话或收起播放器即停止播放，避免后台继续出声。
 *
 * 载荷：payload.poster / payload.cover 作为封面（实况照片用图片地址作封面），message.content 是视频直链。
 * 无封面时用紧凑行展示配文，此时不再在下方重复配文。
 */
import RichText from './RichText.vue'

export default {
    name: 'VideoBubble',
    components: { RichText },
    props: {
        message: {
            type: Object,
            required: true
        }
    },
    emits: ['open-cover'],
    data() {
        return {
            active: false,
            error: '',
            posterError: false
        }
    },
    computed: {
        videoUrl() {
            return (this.message.content || '').trim()
        },
        caption() {
            return (this.message.contentText || '').trim()
        },
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
        /** 封面：优先载荷里的 poster / cover（实况照片由服务端把图片地址写进 poster） */
        posterUrl() {
            if (this.posterError) {
                return ''
            }
            return this.payload.poster || this.payload.cover || ''
        },
        /** 无封面紧凑行的标题：配文首行（可能含换行，折成一行展示） */
        compactTitle() {
            const first = this.caption.split('\n')[0].trim()
            return first || '视频消息'
        },
        /** 视频时长：载荷里有就显示在封面右下角（抖音封面没有时长信息时留空） */
        durationLabel() {
            const seconds = Number(this.payload.duration || 0)
            if (!seconds || seconds <= 0) {
                return ''
            }
            const total = Math.floor(seconds)
            return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0')
        }
    },
    watch: {
        'message.id'() {
            this.close()
            this.posterError = false
        }
    },
    beforeUnmount() {
        this.close()
    },
    methods: {
        play() {
            if (!this.videoUrl) {
                window.$message?.error('这条消息没有视频直链，无法播放')
                return
            }
            this.error = ''
            this.active = true
            this.$nextTick(() => {
                const el = this.$refs.videoEl
                if (el) {
                    el.play().catch(() => {
                        // 浏览器要求用户手势：控件已显示，用户点播放即可，不视为错误
                    })
                }
            })
        },
        close() {
            const el = this.$refs.videoEl
            if (el) {
                el.pause()
            }
            this.active = false
        },
        onVideoError() {
            this.error = '视频无法播放（直链可能已过期）'
        },
        async download() {
            if (!this.videoUrl) {
                return
            }
            try {
                const link = document.createElement('a')
                link.href = this.videoUrl
                link.target = '_blank'
                link.rel = 'noreferrer'
                document.body.appendChild(link)
                link.click()
                link.remove()
            } catch (e) {
                window.$message?.error('下载失败，请重试')
            }
        },
        openPoster() {
            if (this.posterUrl) {
                this.$emit('open-cover', { url: this.posterUrl, title: this.caption || '视频封面' })
            }
        }
    }
}
</script>

<style scoped>
.video-bubble {
    /* 桌面端保留 180px 的观感基准；窄屏（<300px 可用宽）时跟随容器收缩，
       老内核不认识 min() 会退回上面的 0，同样不会顶破气泡。 */
    min-width: 0;
    min-width: min(180px, 100%);
}

/* 封面卡：固定宽高比让不同来源的封面视觉一致 */
.video-cover {
    position: relative;
    box-sizing: border-box;
    width: 320px;
    max-width: 100%;
    height: 180px;
    border-radius: 11px;
    overflow: hidden;
    cursor: pointer;
    background: #0d1626;
}

.video-cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

.video-play {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(180deg, rgba(0, 0, 0, .05) 0%, rgba(0, 0, 0, .35) 100%);
}

.video-play i {
    width: 52px;
    height: 52px;
    padding-left: 4px;
    border-radius: 50%;
    background: rgba(0, 0, 0, .52);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    transition: transform .16s, background .16s;
}

.video-cover:hover .video-play i {
    transform: scale(1.06);
    background: rgba(0, 0, 0, .7);
}

.video-duration {
    position: absolute;
    right: 8px;
    bottom: 8px;
    padding: 1px 6px;
    border-radius: 4px;
    background: rgba(0, 0, 0, .62);
    color: #fff;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
}

/* 无封面：紧凑行 */
.video-compact {
    display: flex;
    align-items: center;
    gap: 10px;
    /* 桌面端保留 220px 的观感基准；窄屏时跟随容器收缩，不会横向溢出 */
    min-width: 0;
    min-width: min(220px, 100%);
    cursor: pointer;
}

.compact-play {
    width: 40px;
    height: 40px;
    flex: none;
    border-radius: 50%;
    background: rgba(14, 165, 233, .16);
    color: var(--accent);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    padding-left: 3px;
}

.compact-body {
    min-width: 0;
}

.compact-title {
    font-size: 14px;
    color: var(--text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 260px;
    max-width: min(260px, 100%);
}

.compact-hint {
    margin-top: 2px;
    font-size: 11.5px;
    color: var(--text-2);
}

/* 内联播放器：与封面同宽，竖屏视频限高避免撑爆消息流 */
.inline-video {
    position: relative;
    box-sizing: border-box;
    width: 320px;
    max-width: 100%;
}

.video-el {
    display: block;
    width: 100%;
    max-height: 420px;
    border-radius: 11px;
    background: #000;
}

.collapse-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 28px;
    height: 28px;
    border: 0;
    border-radius: 50%;
    background: rgba(0, 0, 0, .48);
    color: #fff;
    cursor: pointer;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.collapse-btn:hover {
    background: rgba(0, 0, 0, .72);
}

.video-fallback {
    box-sizing: border-box;
    width: 100%;
    min-height: 150px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 18px;
    border-radius: 11px;
    background: rgba(36, 61, 50, .06);
    color: var(--text-2);
    font-size: 12.5px;
}

.fallback-text {
    text-align: center;
}

.fallback-actions {
    display: flex;
    gap: 10px;
}

.inline-btn {
    padding: 5px 13px;
    border-radius: 8px;
    border: 1px solid var(--line-2);
    background: var(--card);
    color: var(--text);
    cursor: pointer;
    font-family: inherit;
    font-size: 12px;
}

.inline-btn:hover {
    border-color: var(--accent);
    color: var(--accent);
}

.video-caption {
    margin-top: 8px;
    font-size: 13px;
}

/* 手机端：封面更高一些，点击区域更好按 */
@media (max-width: 900px) {
    .video-cover {
        height: 200px;
    }

    .video-el {
        max-height: 62vh;
    }

    .collapse-btn {
        width: 32px;
        height: 32px;
    }
}
</style>
