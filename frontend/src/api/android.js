import http from '@/libs/http'

export const getAndroidStatus = (config, serverId = 'local') => http.get('/android/status', { serverId }, config)
export const createAndroidServer = (name) => http.post('/android/servers', { name })
export const deleteAndroidServer = (id) => http.delete(`/android/servers/${id}`)
export const getAndroidTasks = (config) => http.get('/android/tasks', { limit: 30 }, config)
export const updateAndroidCode = (serverId = 'local') => http.post('/android/update', null, { params: { serverId } })
export const buildAndroidApk = (serverId = 'local') => http.post('/android/build', null, { params: { serverId } })
export const cancelAndroidTask = (id) => http.post(`/android/tasks/${id}/cancel`)
export const getAndroidLogs = (id, cursor, config) => http.get(`/android/tasks/${id}/logs`, { cursor }, config)

export async function prepareAndroidDownload(task, logFile = false) {
    const result = await http.post(`/android/tasks/${task.id}/download-ticket`, { logFile }, { silent: true })
    const url = new URL(result.path, window.location.origin)
    if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/public/android-downloads/')) {
        throw new Error('下载地址无效，请刷新页面后重试')
    }
    return { ...result, url: url.href }
}

export function startAndroidDownload(download) {
    // A normal HTTP navigation works without blob support or a separate builder address.
    window.location.assign(download.url)
}
