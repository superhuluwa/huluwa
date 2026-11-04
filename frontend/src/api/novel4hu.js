import http from '@/libs/http'

/** 4hu 小说：分类、总章数和章节预览；分类响应同时带下载根目录 */
export const getNovel4huCategories = () => http.get('/4hu/novels/categories')

export const getNovel4huCategory = categoryId => http.get(`/4hu/novels/categories/${categoryId}`, null, { timeout: 60000 })

export const getNovel4huChapters = (categoryId, page = 1) =>
    http.get(`/4hu/novels/categories/${categoryId}/chapters`, { page }, { timeout: 60000 })

/** 启动批量正文下载；已存在非空 TXT 的章节由后端跳过 */
export const startNovel4huDownload = (categoryId, outputDir = '') =>
    http.post(`/4hu/novels/categories/${categoryId}/download`, { outputDir }, { timeout: 60000 })

export const getNovel4huTask = taskId => http.get(`/4hu/novels/tasks/${taskId}`)

export const getNovel4huTasks = () => http.get('/4hu/novels/tasks')

export const cancelNovel4huTask = taskId => http.post(`/4hu/novels/tasks/${taskId}/cancel`)
