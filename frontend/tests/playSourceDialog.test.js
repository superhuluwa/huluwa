import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import PlaySourceDialog from '@/views/message/components/PlaySourceDialog.vue'
import { defaultTitle, episodeNumber, isExtra, seasonNumber, sortPlayItems, sortPlaySources } from '@/utils/hlsPlay'
import * as api from '@/api/hls'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

vi.mock('@/api/hls', () => ({ DownloadHls: vi.fn(), GetHlsDetail: vi.fn() }))

const SERIES = [
    { name: 'dytt', total: 3, items: [
        { title: '第01集', url: 'https://vip.dytt-live.com/1.m3u8' },
        { title: '第02集', url: 'https://vip.dytt-live.com/2.m3u8' },
        { title: '第03集', url: 'https://vip.dytt-live.com/3.m3u8' }
    ] },
    { name: 'dyttm3u8', total: 3, items: [
        { title: '第01集', url: 'https://vip.dytt-tvs.com/a.m3u8' },
        { title: '第02集', url: 'https://vip.dytt-tvs.com/b.m3u8' },
        { title: '第03集', url: 'https://vip.dytt-tvs.com/c.m3u8' }
    ] }
]

const MOVIE = { id: 84511, title: '捡钱换宝', year: '2026', season: 1 }

function dialog(props) {
    const vm = { ...PlaySourceDialog.data(), ...PlaySourceDialog.methods, ...props,
        $message: window.$message, $emit: vi.fn() }
    Object.defineProperty(vm, 'sortedSources', { get: () => PlaySourceDialog.computed.sortedSources.call(vm) })
    Object.defineProperty(vm, 'current', { get: () => PlaySourceDialog.computed.current.call(vm) })
    Object.defineProperty(vm, 'currentItems', { get: () => PlaySourceDialog.computed.currentItems.call(vm) })
    Object.defineProperty(vm, 'total', { get: () => PlaySourceDialog.computed.total.call(vm) })
    return vm
}

beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('window', { $message: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } })
    api.DownloadHls.mockResolvedValue({ id: 9, message: '下载任务已记录' })
    api.GetHlsDetail.mockResolvedValue({ playSources: SERIES })
})

describe('电影天堂资源列表线路整理', () => {
    it('dyttm3u8 线路排在第一条，打开弹窗默认选中它', () => {
        const vm = dialog({ sources: SERIES, movie: MOVIE })
        expect(vm.sortedSources.map(source => source.name)).toEqual(['dyttm3u8', 'dytt'])
        expect(vm.current.name).toBe('dyttm3u8')
    })

    it('剧集按集号倒序，识别不出集号的条目排在最后', () => {
        const items = [
            { title: '第01集', url: 'https://a/1.m3u8' },
            { title: '第03集', url: 'https://a/3.m3u8' },
            { title: '预告', url: 'https://a/t.m3u8' },
            { title: '第02集', url: 'https://a/2.m3u8' }
        ]
        expect(sortPlayItems(items).map(item => item.title)).toEqual(['第03集', '第02集', '第01集', '预告'])
        expect(dialog({ sources: SERIES, movie: MOVIE }).currentItems.map(item => item.title))
            .toEqual(['第03集', '第02集', '第01集'])
    })

    it('正片等非剧集条目保持原有顺序，排序不修改原始数组', () => {
        const sources = [{ name: 'dytt', items: [{ title: 'HD国语', url: 'https://a/hd.m3u8' }] }]
        expect(sortPlaySources(sources)[0].name).toBe('dytt')
        expect(sources[0].name).toBe('dytt')
        expect(sortPlayItems([{ title: '正片', url: 'https://a/a.m3u8' }])[0].title).toBe('正片')
    })

    it('集号、季号与标准剧名识别与后端规则一致', () => {
        expect(episodeNumber('第03集')).toBe(3)
        expect(episodeNumber('第一百二十三集')).toBe(123)
        expect(episodeNumber('S02E12')).toBe(12)
        expect(episodeNumber('EP06')).toBe(6)
        expect(episodeNumber('01集')).toBe(1)
        expect(episodeNumber('正片')).toBe(null)
        expect(episodeNumber('第01集预告')).toBe(null)
        expect(episodeNumber('第1-2集')).toBe(null)
        expect(episodeNumber('第一〇一集')).toBe(101)
        expect(episodeNumber('剧名 S02E12')).toBe(12)
        expect(episodeNumber('008')).toBe(8)
        expect(episodeNumber('特别篇 第01集')).toBe(null)
        expect(episodeNumber('S01E01-E02')).toBe(null)
        expect(episodeNumber('S01E01-02')).toBe(null)
        expect(episodeNumber('1080P')).toBe(null)
        expect(seasonNumber('第2季')).toBe(2)
        expect(seasonNumber('第二季')).toBe(2)
        expect(seasonNumber('剧名 S02E12')).toBe(2)
        expect(seasonNumber('捡钱换宝')).toBe(null)
        expect(defaultTitle('剧名（第二季）更新至第65集')).toBe('剧名')
        expect(defaultTitle('捡钱换宝')).toBe('捡钱换宝')
        expect(isExtra('预告')).toBe(true)
        expect(isExtra('第01集预告')).toBe(true)
        expect(isExtra('第01集')).toBe(false)
        expect(isExtra('HD国语')).toBe(false)
    })

    it('模板接线：标签与列表使用排序结果，且不再渲染下载设置对话框', () => {
        const file = fileURLToPath(new URL('../src/views/message/components/PlaySourceDialog.vue', import.meta.url))
        const source = readFileSync(file, 'utf8')
        const template = source.slice(0, source.lastIndexOf('</template>'))

        expect(template).toContain('v-for="(s, i) in sortedSources"')
        expect(template).toContain('v-for="(item, index) in currentItems"')
        expect(template).toContain("title || '资源列表'")
        expect(template).not.toContain('播放地址')
        expect(template).not.toContain('HlsResourceDialog')
        expect(source).not.toContain('HlsResourceDialog')
        expect(source).not.toContain('downloadDialog')
    })
})

describe('打开弹窗时拉取完整剧集', () => {
    it('接口返回完整线路时替换卡片载荷，长剧集的最新集可见', async () => {
        const long = [
            { name: 'dyttm3u8', total: 100, items: Array.from({ length: 100 }, (unused, index) => ({
                title: `第${String(index + 1).padStart(2, '0')}集`,
                url: `https://vip.dytt-tvs.com/${index + 1}.m3u8`
            })) }
        ]
        api.GetHlsDetail.mockResolvedValue({ playSources: long })
        const vm = dialog({ sources: SERIES, movie: MOVIE, show: true })
        await vm.loadDetail()

        expect(api.GetHlsDetail).toHaveBeenCalledWith('84511', undefined)
        expect(vm.currentItems[0].title).toBe('第100集')
        expect(vm.currentItems).toHaveLength(100)
    })

    it('详情接口失败时保留卡片载荷，仍可复制与下载已展示剧集', async () => {
        api.GetHlsDetail.mockRejectedValue(new Error('网络异常'))
        const vm = dialog({ sources: SERIES, movie: MOVIE, show: true })
        await vm.loadDetail()

        expect(vm.sortedSources.map(source => source.name)).toEqual(['dyttm3u8', 'dytt'])
        expect(vm.currentItems.map(item => item.title)).toEqual(['第03集', '第02集', '第01集'])
        expect(vm.loadingDetail).toBe(false)
    })

    it('缺少作品 ID 时跳过详情请求', async () => {
        const vm = dialog({ sources: SERIES, movie: { title: '未知' }, show: true })
        await vm.loadDetail()

        expect(api.GetHlsDetail).not.toHaveBeenCalled()
    })
})

describe('电影天堂单集离线下载', () => {
    it('点击下载直接用默认参数入队，不再打开设置对话框', async () => {
        const vm = dialog({ sources: SERIES, movie: MOVIE })
        await vm.download(vm.currentItems[0])

        expect(api.DownloadHls).toHaveBeenCalledWith({
            apiUrl: 'https://dyttzyw.com/api.php/provide/vod/',
            resId: '84511',
            title: '捡钱换宝',
            year: '2026',
            season: 1,
            episode: 3,
            episodeTitle: '第03集',
            url: 'https://vip.dytt-tvs.com/c.m3u8',
            sourceName: 'dyttm3u8'
        })
        expect(vm.$message.success).toHaveBeenCalledWith('下载任务已记录')
        expect(vm.$emit).toHaveBeenCalledWith('queued', expect.objectContaining({ id: 9 }))
        expect(vm.downloadingKey).toBe('')
    })

    it('下载标题带季号备注时按标准剧名入队', async () => {
        const vm = dialog({ sources: SERIES, movie: { ...MOVIE, title: '剧名（第二季）更新至第65集' } })
        await vm.download(vm.currentItems[0])

        expect(api.DownloadHls).toHaveBeenCalledWith(expect.objectContaining({
            title: '剧名', season: 2, episode: 3
        }))
    })

    it('电影无集号正片按默认第 1 集直接入队（多清晰度也可选行）', async () => {
        const film = [{ name: 'dyttm3u8', total: 2, items: [
            { title: 'HD国语', url: 'https://vip.dytt-tvs.com/movie/hd.m3u8' },
            { title: 'BD国语', url: 'https://vip.dytt-tvs.com/movie/bd.m3u8' }
        ] }]
        api.GetHlsDetail.mockResolvedValue({ playSources: film })
        const vm = dialog({ sources: film, movie: { id: 2354, title: '流浪地球', year: '2019' }, show: true })
        await vm.loadDetail()
        await vm.download(vm.currentItems[0])

        expect(api.DownloadHls).toHaveBeenCalledWith(expect.objectContaining({
            resId: '2354', episode: 1, episodeTitle: 'HD国语',
            url: 'https://vip.dytt-tvs.com/movie/hd.m3u8', sourceName: 'dyttm3u8'
        }))
    })

    it('单条预告不按默认第 1 集入队', async () => {
        const trailer = [{ name: 'dyttm3u8', total: 1, items: [
            { title: '预告', url: 'https://vip.dytt-tvs.com/trailer/index.m3u8' }
        ] }]
        const vm = dialog({ sources: trailer, movie: MOVIE })
        await vm.download(vm.currentItems[0])

        expect(api.DownloadHls).not.toHaveBeenCalled()
        expect(vm.$message.warning).toHaveBeenCalled()
    })

    it('剧集线路中识别不出集号的条目不入队', async () => {
        const vm = dialog({ sources: SERIES, movie: MOVIE })
        await vm.download({ title: '正片', url: 'https://vip.dytt-tvs.com/movie.m3u8' })

        expect(api.DownloadHls).not.toHaveBeenCalled()
        expect(vm.$message.warning).toHaveBeenCalled()
    })

    it('缺少作品 ID 时提示错误，不发起请求', async () => {
        const vm = dialog({ sources: SERIES, movie: { title: '未知' } })
        await vm.download(vm.currentItems[0])

        expect(api.DownloadHls).not.toHaveBeenCalled()
        expect(vm.$message.error).toHaveBeenCalled()
    })
})

