import http from '@/libs/http'

export const getAndroidStatus = (config) => http.get('/android/status', null, config)
export const getAndroidTasks = (config) => http.get('/android/tasks', { limit: 30 }, config)
export const updateAndroidCode = () => http.post('/android/update')
export const buildAndroidApk = () => http.post('/android/build')
export const cancelAndroidTask = (id) => http.post(`/android/tasks/${id}/cancel`)
export const getAndroidLogs = (id, cursor, config) => http.get(`/android/tasks/${id}/logs`, { cursor }, config)

export async function downloadAndroidFile(task, logFile = false, onProgress) {
    const blob = await http.get(`/android/tasks/${task.id}/${logFile ? 'log-file' : 'apk'}`, null, {
        responseType: 'blob', timeout: 0, onDownloadProgress: onProgress
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = logFile ? `android-task-${task.id}.log` : task.artifactName
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 60000)
}
