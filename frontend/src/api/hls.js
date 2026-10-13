import http from '@/libs/http'

export const GetHlsSettings = () => http.get('/hls/settings')
export const SaveHlsSettings = body => http.post('/hls/settings', body)
export const GetHlsDetail = (resId, apiUrl) => http.get('/hls/detail', { resId, apiUrl }, { timeout: 60000 })
export const GetHlsSubscriptions = () => http.get('/hls/subscriptions')
export const SubscribeHls = body => http.post('/hls/subscriptions', body, { timeout: 60000 })
export const CheckHlsSubscription = id => http.post('/hls/subscriptions/' + id + '/check', {}, { timeout: 60000 })
export const EnableHlsSubscription = (id, enabled) => http.post('/hls/subscriptions/' + id + '/enabled', { enabled })
export const GetHlsDownloads = () => http.get('/hls/downloads')
export const GetHlsDownloadLog = id => http.get('/hls/downloads/' + id + '/log')
export const DownloadHls = body => http.post('/hls/downloads', body, { timeout: 60000 })
export const RetryHlsDownload = id => http.post('/hls/downloads/' + id + '/retry')
