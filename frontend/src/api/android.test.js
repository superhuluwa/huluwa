import { beforeEach, expect, test, vi } from 'vitest'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('@/libs/http', () => ({ default: http }))
import { getAndroidStatus, updateAndroidCode, buildAndroidApk, createAndroidServer, deleteAndroidServer } from './android'

beforeEach(() => vi.clearAllMocks())

test('既有客户端默认使用原构建服务器', () => {
    getAndroidStatus()
    updateAndroidCode()
    expect(http.get).toHaveBeenCalledWith('/android/status', { serverId: 'local' }, undefined)
    expect(http.post).toHaveBeenCalledWith('/android/update', null, { params: { serverId: 'local' } })
})

test('状态、更新和编译使用同一个选中节点', () => {
    getAndroidStatus(undefined, 'windows-node')
    updateAndroidCode('windows-node')
    buildAndroidApk('windows-node')
    expect(http.get).toHaveBeenCalledWith('/android/status', { serverId: 'windows-node' }, undefined)
    expect(http.post).toHaveBeenCalledWith('/android/update', null, { params: { serverId: 'windows-node' } })
    expect(http.post).toHaveBeenCalledWith('/android/build', null, { params: { serverId: 'windows-node' } })
})

test('节点注册和撤销走登录保护接口', () => {
    createAndroidServer('我的电脑')
    deleteAndroidServer('windows-node')
    expect(http.post).toHaveBeenCalledWith('/android/servers', { name: '我的电脑' })
    expect(http.delete).toHaveBeenCalledWith('/android/servers/windows-node')
})
