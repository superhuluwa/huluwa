import http from '@/libs/http'

/** 扫描共享下载目录并返回可删除的视频文件清单 */
export const ScanEpisodeLibrary = () => http.get('/library/episodes', null, { timeout: 60000 })

/** 批量删除；后端仅接受本次扫描返回的短期 ID */
export const DeleteEpisodeLibrary = ids => http.delete('/library/episodes', {
    data: { ids },
    timeout: 60000
})
