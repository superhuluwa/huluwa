import http from '@/libs/http'

// ---------------- 环境与汇总 ----------------
export const getEnvironment = () => http.get('/docker/environment')
export const getSummary = () => http.get('/docker/summary')
export const getVersion = () => http.get('/docker/version')
export const getInfo = () => http.get('/docker/info')

// ---------------- 容器 ----------------
export const listContainers = (all = false) => http.get('/docker/containers', { all })
export const inspectContainer = (containerId) => http.get(`/docker/containers/${containerId}`)
export const getContainerStats = (containerId) => http.get(`/docker/containers/${containerId}/stats`, null, { skipAuth: false })
export const getContainersStats = (containerIds) => http.post('/docker/containers/stats', containerIds)
export const createContainer = (config) => http.post('/docker/containers', config)
export const startContainer = (containerId) => http.post(`/docker/containers/${containerId}/start`)
export const stopContainer = (containerId) => http.post(`/docker/containers/${containerId}/stop`)
export const restartContainer = (containerId) => http.post(`/docker/containers/${containerId}/restart`)
export const removeContainer = (containerId, force = false, removeVolumes = false) =>
    http.delete(`/docker/containers/${containerId}`, { params: { force, removeVolumes } })
export const getContainerLogs = (containerId, tail = 'all') =>
    http.get(`/docker/containers/${containerId}/logs`, { tail })
export const executeCommand = (containerId, command) =>
    http.post(`/docker/containers/${containerId}/execute`, { command })

// ---------------- 镜像 ----------------
export const listImages = (all = false) => http.get('/docker/images', { all })
export const pullImage = (image, tag = 'latest') => http.post('/docker/images/pull', null, { params: { image, tag } })
export const removeImage = (imageId, force = false) =>
    http.delete(`/docker/images/${imageId}`, { params: { force } })

// ---------------- 网络 ----------------
export const listNetworks = () => http.get('/docker/networks')
export const inspectNetwork = (networkId) => http.get(`/docker/networks/${networkId}`)
export const createNetwork = (name, driver = 'bridge') =>
    http.post('/docker/networks', null, { params: { name, driver } })
export const removeNetwork = (networkId) => http.delete(`/docker/networks/${networkId}`)
export const connectNetwork = (networkId, containerId) =>
    http.post(`/docker/networks/${networkId}/connect`, null, { params: { containerId } })
export const disconnectNetwork = (networkId, containerId) =>
    http.post(`/docker/networks/${networkId}/disconnect`, null, { params: { containerId } })

// ---------------- 卷 ----------------
export const listVolumes = () => http.get('/docker/volumes')
export const createVolume = (name, driver = 'local') =>
    http.post('/docker/volumes', null, { params: { name, driver } })
export const removeVolume = (volumeName) => http.delete(`/docker/volumes/${volumeName}`)

// ---------------- 主机配置 ----------------
export const listHosts = () => http.get('/docker/hosts')
export const createHost = (data) => http.post('/docker/hosts', data)
export const deleteHost = (id) => http.delete(`/docker/hosts/${id}`)
