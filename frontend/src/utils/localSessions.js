import http from '@/libs/http'
import { getSummary, getEnvironment, listContainers } from '@/api/docker'
import { getAndroidStatus } from '@/api/android'
import { ScanEpisodeLibrary } from '@/api/library'
import { GetSubscriptionCheckLogs, GetSubscriptions } from '@/api/chat'
import { GetHlsSubscriptions } from '@/api/hls'

/**
 * 本地会话（对齐安卓 HuluwaViewModel.withAppSessions）：
 * 服务端只下发聊天会话（电影港 / 电影天堂 / 磁力 / 视频解析 / 迅雷 / 系统通知），
 * Frp、Docker、App 构建是客户端本地会话，摘要由各接口聚合后实时计算；
 * 「在线升级」只在安卓 App 内提供（网页端不显示安装自己），
 * 而安卓没有的「巡检记录」「剧集管理」在网页端作为系统会话补进来；
 * 「我的订阅」由原底部订阅标签迁入系统会话，与其它系统会话一样整屏打开。
 *
 * 会话行格式与 /chat/sessions 一致：{ id, title, kind, unread, last, updatedAt }。
 */

/** 本地会话 ID：与安卓保持一致，避免同一用户两端看到的名字 / 顺序不同 */
export const LOCAL_SESSIONS = {
    frp: { id: 'local-frp-management', kind: 'frp', title: 'Frp 管理', route: 'frp' },
    docker: { id: 'local-docker-management', kind: 'docker', title: 'Docker 管理', route: 'docker' },
    build: { id: 'local-android-build', kind: 'android-build', title: 'App 构建管理', route: 'android-build' },
    checkLogs: { id: 'local-check-logs', kind: 'check-logs', title: '巡检记录', route: 'check-logs' },
    library: { id: 'local-library', kind: 'library', title: '剧集管理', route: 'library' },
    subscriptions: { id: 'local-subscriptions', kind: 'subscriptions', title: '我的订阅', route: 'my-subscriptions' }
}

/** 系统标签下的本地会话顺序（与安卓 ManagementService.entries 顺序一致；不含在线升级） */
export const MANAGEMENT_SESSIONS = [
    LOCAL_SESSIONS.frp,
    LOCAL_SESSIONS.docker,
    LOCAL_SESSIONS.build,
    LOCAL_SESSIONS.checkLogs,
    LOCAL_SESSIONS.library,
    LOCAL_SESSIONS.subscriptions
]

export const LOCAL_KINDS = new Set([
    'frp', 'docker', 'android-build', 'check-logs', 'library', 'subscriptions'
])

export function isLocalSession(session) {
    return Boolean(session && LOCAL_KINDS.has(session.kind))
}

export function isSystemSession(session) {
    return isLocalSession(session)
}

/** 本地会话 → 路由名（会话列表点击时跳转） */
export function routeOfSession(session) {
    if (!session) {
        return 'functions'
    }
    if (session.kind === 'system') {
        return 'chat'
    }
    const matched = Object.values(LOCAL_SESSIONS).find((item) => item.kind === session.kind)
    return matched ? matched.route : 'chat'
}

function localSession(meta, preview, updatedAt = '') {
    return {
        id: meta.id,
        title: meta.title,
        kind: meta.kind,
        unread: 0,
        last: null,
        previewOverride: preview,
        updatedAt
    }
}

/**
 * 拉取并组装会话列表：
 *  1. 聊天会话来自 /chat/sessions；
 *  2. 本地会话的摘要并行请求各自接口，单个失败只影响自己那一行；
 *  3. 排序与安卓一致：按更新时间倒序，空时间按最旧处理，同时间按 id 排序。
 */
export async function loadSessions() {
    const [chatSessions, frp, docker, build, checkLogs, library, subscriptions] = await Promise.allSettled([
        http.get('/chat/sessions'),
        loadFrp(),
        loadDocker(),
        loadBuild(),
        loadCheckLogs(),
        loadLibrary(),
        loadSubscriptions()
    ])

    const pick = (result) => (result.status === 'fulfilled' ? result.value : null)
    const locals = [pick(frp), pick(docker), pick(build), pick(checkLogs), pick(library), pick(subscriptions)]
        .filter(Boolean)
    // 聊天会话是列表的主体：拉不到时向上抛错，由 store 保留旧列表并给出重试提示；
    // 本地会话则各自降级为一行说明，不影响其它会话。
    if (chatSessions.status !== 'fulfilled') {
        throw chatSessions.reason
    }
    const remote = Array.isArray(chatSessions.value) ? chatSessions.value : []
    const rows = remote
        .filter((item) => !isLocalSession(item))
        .concat(locals)

    return rows.sort((left, right) => {
        const rightTime = String(right.updatedAt || (right.last && right.last.createdAt) || '0000-00-00 00:00:00')
        const leftTime = String(left.updatedAt || (left.last && left.last.createdAt) || '0000-00-00 00:00:00')
        if (rightTime !== leftTime) {
            return rightTime.localeCompare(leftTime)
        }
        return String(left.id).localeCompare(String(right.id))
    })
}

/** Frp 管理摘要：与安卓一致，优先反映连接异常与运行状态 */
async function loadFrp() {
    try {
        const [tunnels, runtime] = await Promise.all([
            http.get('/frp/tunnels', null, { silent: true }),
            http.get('/frp/runtime', null, { silent: true })
        ])
        const list = Array.isArray(tunnels) ? tunnels : []
        const running = list.filter((item) => item.enabled && item.status === 'running').length
        const preview = !(runtime && runtime.containerRunning)
            ? `frpc 未运行 · 共 ${list.length} 条隧道`
            : `${running}/${list.length} 条隧道运行中 · 点击管理`
        return localSession(LOCAL_SESSIONS.frp, preview)
    } catch (error) {
        return localSession(LOCAL_SESSIONS.frp, '暂时无法连接 Frp，点击查看详情')
    }
}

/** Docker 管理摘要：容器总数 / 运行数 / 镜像数 */
async function loadDocker() {
    try {
        const [containers, summary, environment] = await Promise.all([
            listContainers(true).catch(() => []),
            getSummary().catch(() => null),
            getEnvironment().catch(() => null)
        ])
        if (environment && environment.daemonOnline === false) {
            return localSession(LOCAL_SESSIONS.docker, 'Docker 连接异常，点击查看详情')
        }
        const total = (summary && summary.total) ?? (Array.isArray(containers) ? containers.length : 0)
        const running = (summary && summary.running)
            ?? (Array.isArray(containers) ? containers.filter((item) => item.State === 'running').length : 0)
        const images = (summary && summary.images) ?? 0
        return localSession(LOCAL_SESSIONS.docker, `${running}/${total} 个容器运行中 · ${images} 个镜像`)
    } catch (error) {
        return localSession(LOCAL_SESSIONS.docker, '暂时无法连接 Docker，点击查看详情')
    }
}

/** App 构建管理摘要：优先展示进行中的任务（与安卓 androidBuildPreview 一致） */
async function loadBuild() {
    try {
        const status = await getAndroidStatus({ silent: true })
        if (!status.enabled) {
            return localSession(LOCAL_SESSIONS.build, '安卓构建服务尚未启用，点击查看详情')
        }
        const task = status.activeTask
        if (task) {
            const stage = {
                QUEUED: '等待执行', FETCHING: '更新源码', PREPARING: '准备编译',
                COMPILING: '正在编译', VERIFYING: '校验安装包', ARCHIVING: '归档安装包', DONE: '完成'
            }[task.stage] || task.status
            return localSession(LOCAL_SESSIONS.build, `${task.type === 'BUILD' ? '编译 APK' : '更新代码'} #${task.id} · ${stage}`)
        }
        if (status.latestApk) {
            return localSession(LOCAL_SESSIONS.build, `最近安装包 v${status.latestApk.versionName} · ${status.latestApk.versionCode}，点击管理`)
        }
        return localSession(LOCAL_SESSIONS.build, '更新源码、编译安装包，查看执行日志')
    } catch (error) {
        return localSession(LOCAL_SESSIONS.build, '暂时无法连接构建服务，点击重试')
    }
}

/** 巡检记录摘要：最近一条巡检结果 */
async function loadCheckLogs() {
    try {
        const rows = await GetSubscriptionCheckLogs(1)
        const latest = Array.isArray(rows) && rows.length ? rows[0] : null
        if (!latest) {
            return localSession(LOCAL_SESSIONS.checkLogs, '电影港与电影天堂的订阅巡检历史')
        }
        const source = latest.source === 'dytt' ? '电影天堂' : '电影港'
        const tone = {
            updated: `发现 ${latest.foundCount} 条新资源`,
            ok: '无更新',
            baseline: '已补建基线',
            failed: '巡检失败'
        }[latest.status] || '巡检完成'
        return localSession(LOCAL_SESSIONS.checkLogs, `${source} · ${latest.seriesTitle || '未知订阅'} · ${tone}`, latest.createdAt || '')
    } catch (error) {
        return localSession(LOCAL_SESSIONS.checkLogs, '巡检历史加载失败，点击重试')
    }
}

/** 剧集管理摘要：已扫描的视频数量与占用空间 */
async function loadLibrary() {
    try {
        const scan = await ScanEpisodeLibrary()
        const entries = Array.isArray(scan && scan.entries) ? scan.entries : []
        if (!entries.length) {
            return localSession(LOCAL_SESSIONS.library, '下载目录里还没有可管理的剧集')
        }
        const series = scan.seriesCount || new Set(entries.map((item) => item.seriesKey)).size
        return localSession(LOCAL_SESSIONS.library, `${series} 部作品 · ${entries.length} 个视频 · ${formatBytes(scan.totalBytes)}`)
    } catch (error) {
        return localSession(LOCAL_SESSIONS.library, '下载目录扫描失败，点击重试')
    }
}

/**
 * 我的订阅摘要：电影港与电影天堂两个来源合并统计。
 * 单个来源失败只标注该来源，避免整行退化成「加载失败」。
 */
async function loadSubscriptions() {
    try {
        const [movie, hls] = await Promise.allSettled([GetSubscriptions(true), GetHlsSubscriptions()])
        const failed = []
        let total = 0
        if (movie.status === 'fulfilled') {
            for (const item of Array.isArray(movie.value) ? movie.value : []) {
                total += 1
            }
        } else {
            failed.push('电影港')
        }
        if (hls.status === 'fulfilled') {
            for (const item of Array.isArray(hls.value) ? hls.value : []) {
                total += 1
            }
        } else {
            failed.push('电影天堂')
        }
        if (failed.length === 2) {
            return localSession(LOCAL_SESSIONS.subscriptions, '订阅加载失败，点击重试')
        }
        const preview = total
            ? `${total} 个订阅${failed.length ? ` · ${failed.join('、')}加载失败` : ''}`
            : '电影港与电影天堂的订阅，追更状态与资源列表'
        return localSession(LOCAL_SESSIONS.subscriptions, preview)
    } catch (error) {
        return localSession(LOCAL_SESSIONS.subscriptions, '订阅加载失败，点击重试')
    }
}

function formatBytes(value) {
    const bytes = Number(value || 0)
    if (!Number.isFinite(bytes) || bytes <= 0) {
        return '0 B'
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB']
    let size = bytes
    let index = 0
    while (size >= 1024 && index < units.length - 1) {
        size /= 1024
        index += 1
    }
    return `${index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`
}
