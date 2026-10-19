import http from '@/libs/http'

export const getAndroidStatus = (config) => http.get('/android/status', null, config)
export const getAndroidTasks = (config) => http.get('/android/tasks', { limit: 30 }, config)
export const updateAndroidCode = () => http.post('/android/update')
export const buildAndroidApk = () => http.post('/android/build')
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
