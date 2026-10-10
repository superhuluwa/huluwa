import http from '@/libs/http'

// ---------------- 环境与汇总 ----------------
export const getEnvironment = () => http.get('/docker/environment')
export const getSummary = () => http.get('/docker/summary')
export const getVersion = () => http.get('/docker/version')
export const getInfo = () => http.get('/docker/info')

// ---------------- 容器 ----------------
export const listContainers = (all = false) => http.get('/docker/containers', { all })
export const inspectContainer = (containerId, config) => http.get(`/docker/containers/${encodeURIComponent(containerId)}`, null, config)
export const getContainerStats = (containerId) => http.get(`/docker/containers/${encodeURIComponent(containerId)}/stats`, null, { silent: true, timeout: 35000 })
export const getContainersStats = async (containerIds) => {
    const chunks = []
    for (let index = 0; index < containerIds.length; index += 200) {
        chunks.push(http.post('/docker/containers/stats', containerIds.slice(index, index + 200), { silent: true, timeout: 35000 }))
    }
    return Object.assign({}, ...await Promise.all(chunks))
}
export const createContainer = (config) => http.post('/docker/containers', config)
export const startContainer = (containerId) => http.post(`/docker/containers/${encodeURIComponent(containerId)}/start`)
export const stopContainer = (containerId) => http.post(`/docker/containers/${encodeURIComponent(containerId)}/stop`)
export const restartContainer = (containerId) => http.post(`/docker/containers/${encodeURIComponent(containerId)}/restart`)
export const removeContainer = (containerId, force = false, removeVolumes = false) =>
    http.delete(`/docker/containers/${encodeURIComponent(containerId)}`, { params: { force, removeVolumes } })
// 兼容旧版 (id, tail) 与 Quantum 的 (id, follow, stdout, stderr, tail) 调用。
export const getContainerLogs = (containerId, follow = false, stdout = true, stderr = true, tail = 'all', config) => {
    if (typeof follow === 'string' || typeof follow === 'number') { tail = String(follow); follow = false }
    return http.get(`/docker/containers/${encodeURIComponent(containerId)}/logs`, { follow, stdout, stderr, tail }, config)
}
export const executeCommand = (containerId, command) =>
    http.post(`/docker/containers/${encodeURIComponent(containerId)}/execute`, { command })

// ---------------- 镜像 ----------------
export const listImages = (all = false) => http.get('/docker/images', { all })
export const pullImage = (image, tag = 'latest') => http.post('/docker/images/pull', null, { params: { image, tag }, timeout: 920000 })
export const removeImage = (imageId, force = false, config) =>
    http.delete(`/docker/images/${encodeURIComponent(imageId)}`, { params: { force }, ...config })

// ---------------- 网络 ----------------
export const listNetworks = () => http.get('/docker/networks')
export const inspectNetwork = (networkId) => http.get(`/docker/networks/${encodeURIComponent(networkId)}`)
export const createNetwork = (name, driver = 'bridge') =>
    http.post('/docker/networks', null, { params: { name, driver } })
export const removeNetwork = (networkId) => http.delete(`/docker/networks/${encodeURIComponent(networkId)}`)
export const connectNetwork = (networkId, containerId) =>
    http.post(`/docker/networks/${encodeURIComponent(networkId)}/connect`, null, { params: { containerId } })
export const disconnectNetwork = (networkId, containerId) =>
    http.post(`/docker/networks/${encodeURIComponent(networkId)}/disconnect`, null, { params: { containerId } })

// ---------------- 卷 ----------------
export const listVolumes = () => http.get('/docker/volumes')
export const createVolume = (name, driver = 'local') =>
    http.post('/docker/volumes', null, { params: { name, driver } })
export const removeVolume = (volumeName) => http.delete(`/docker/volumes/${encodeURIComponent(volumeName)}`)

// ---------------- 主机配置 ----------------
export const listHosts = () => http.get('/docker/hosts')
export const createHost = (data) => http.post('/docker/hosts', data)
export const deleteHost = (id) => http.delete(`/docker/hosts/${id}`)
