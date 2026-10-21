import { expect, it, vi } from 'vitest'
import { LOCAL_SESSIONS, LOCAL_KINDS, MANAGEMENT_SESSIONS, loadSessions, isLocalSession, isSystemSession } from '@/utils/localSessions'
import http from '@/libs/http'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/docker', () => ({ getSummary: vi.fn(), getEnvironment: vi.fn(), listContainers: vi.fn() }))
vi.mock('@/api/android', () => ({ getAndroidStatus: vi.fn() }))
vi.mock('@/api/library', () => ({ ScanEpisodeLibrary: vi.fn() }))
vi.mock('@/api/chat', () => ({ GetSubscriptionCheckLogs: vi.fn(), GetSubscriptions: vi.fn() }))
vi.mock('@/api/hls', () => ({ GetHlsSubscriptions: vi.fn() }))

it('本地会话不包含在线升级，系统会话 = Frp / Docker / 构建 / 巡检 / 剧集 / 我的订阅', async () => {
    http.get.mockImplementation((url) => url === '/chat/sessions'
        ? Promise.resolve([{ id: 'movie', kind: 'movie', title: '电影港', unread: 0 }])
        : Promise.reject(new Error('offline')))
    const rows = await loadSessions()
    const kinds = rows.map(r => r.kind)
    expect(kinds).not.toContain('upgrade')
    expect(rows.some(r => r.title === '在线升级')).toBe(false)
    for (const kind of ['frp', 'docker', 'android-build', 'check-logs', 'library', 'subscriptions']) {
        expect(kinds).toContain(kind)
    }
    expect(Object.keys(LOCAL_SESSIONS)).toEqual(['frp', 'docker', 'build', 'checkLogs', 'library', 'subscriptions'])
    expect(MANAGEMENT_SESSIONS.map(s => s.kind))
        .toEqual(['frp', 'docker', 'android-build', 'check-logs', 'library', 'subscriptions'])
    expect(LOCAL_SESSIONS.subscriptions.title).toBe('我的订阅')
    expect(LOCAL_KINDS.has('upgrade')).toBe(false)
    expect(isLocalSession({ kind: 'upgrade' })).toBe(false)
    expect(isSystemSession({ kind: 'subscriptions' })).toBe(true)
    expect(isSystemSession({ kind: 'check-logs' })).toBe(true)
    expect(isSystemSession({ kind: 'movie' })).toBe(false)
})
