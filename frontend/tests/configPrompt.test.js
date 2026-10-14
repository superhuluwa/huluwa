import { describe, expect, it } from 'vitest'
import { configPromptKind } from '@/utils/configPrompt'

/** 与后端 ChatService.magnetMeta / MovieSubscriptionService.magnetNotice 同形的载荷 */
function message(id, status, kind = 'magnet', configured = true) {
    return {
        id,
        payload: JSON.stringify({ kind, status, settings: { url: 'http://192.168.3.77:8085', configured } })
    }
}

describe('会话配置引导判定', () => {
    it('真的未配置时，新到达的机器人消息弹出对应配置表单', () => {
        expect(configPromptKind(message(101, 'config', 'magnet', false), { baseline: 100, armed: true })).toBe('magnet')
        expect(configPromptKind(message(101, 'config', 'video', false), { baseline: 100, armed: true })).toBe('video')
        expect(configPromptKind(message(101, 'config', 'xunlei', false), { baseline: 100, armed: true })).toBe('xunlei')
    })

    it('「下载失败」通知带 config 状态但配置仍然有效时不弹（本次回归的问题）', () => {
        const failed = message(80, 'config', 'magnet', true)
        expect(configPromptKind(failed, { baseline: 79, armed: true })).toBeNull()
    })

    it('进入会话 / 页面的历史回放不弹：基线之前的消息一律忽略', () => {
        const history = message(80, 'config', 'magnet', false)
        expect(configPromptKind(history, { baseline: 80, armed: true })).toBeNull()
        expect(configPromptKind(history, { baseline: 120, armed: true })).toBeNull()
        // 历史里未配置的真实引导同样只在「新到达」时才弹
        expect(configPromptKind(history, { baseline: 79, armed: true })).toBe('magnet')
    })

    it('整页加载 / 手动刷新期间不弹，避免每次进会话都打扰', () => {
        expect(configPromptKind(message(101, 'config', 'magnet', false), { baseline: 0, armed: false })).toBeNull()
    })

    it('ready / 损坏载荷 / 缺少 id 的旧消息不弹', () => {
        expect(configPromptKind(message(101, 'ready', 'magnet', true), { baseline: 0, armed: true })).toBeNull()
        expect(configPromptKind({ id: 101, payload: '{ 损坏' }, { baseline: 0, armed: true })).toBeNull()
        expect(configPromptKind({ id: 'tmp-1', payload: message(101, 'config', 'magnet', false).payload },
            { baseline: 0, armed: true })).toBeNull()
        expect(configPromptKind(null, { baseline: 0, armed: true })).toBeNull()
        expect(configPromptKind({ id: 101 }, { baseline: 0, armed: true })).toBeNull()
    })

    it('缺少 settings 的旧载荷按未配置处理，保持原有引导能力', () => {
        const legacy = { id: 101, payload: JSON.stringify({ kind: 'magnet', status: 'config' }) }
        expect(configPromptKind(legacy, { baseline: 100, armed: true })).toBe('magnet')
    })
})
