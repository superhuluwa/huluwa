import { describe, expect, it, vi } from 'vitest'

// 首页引了 http（会顺带加载离散提示 API，依赖浏览器 window）；这里只测展示逻辑，桩掉即可
vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
vi.mock('@/composables/useIsMobile', () => ({ useIsMobile: () => ({ value: false }) }))

const { default: Dashboard } = await import('@/views/dashboard/index.vue')

/**
 * 不挂载组件，只取首页的展示方法与计算属性：验证「后端没给数值时显示占位符而不是 0」，
 * 以及占用条的配色阈值。0% 会被误读成机器很空闲，是本次要守住的边界。
 */
function page(server) {
    const vm = { ...Dashboard.methods }
    const computed = {
        server: () => server,
        cpuPercentText: Dashboard.computed.cpuPercentText,
        cpuFoot: Dashboard.computed.cpuFoot,
        memoryUsedText: Dashboard.computed.memoryUsedText,
        memoryFoot: Dashboard.computed.memoryFoot
    }
    Object.entries(computed).forEach(([key, getter]) => {
        Object.defineProperty(vm, key, { get: getter.bind(vm) })
    })
    vm.number = Dashboard.methods.number.bind(vm)
    vm.meterClass = Dashboard.methods.meterClass.bind(vm)
    vm.meterStyle = Dashboard.methods.meterStyle.bind(vm)
    return vm
}

describe('首页 CPU / 内存卡片', () => {
    it('后端给出数值时按「数值 + 占用比」展示', () => {
        const vm = page({
            available: true,
            // 后端已把百分比收敛到一位小数，前端只负责补单位
            cpu: { percent: 23.4, cores: 8, load1: 0.42 },
            memory: { total: 6086426624, available: 2269134848, used: 3817291776, percent: 62.7 }
        })
        expect(vm.cpuPercentText).toBe('23.4%')
        expect(vm.cpuFoot).toBe('8 核 · 负载 0.42')
        expect(vm.memoryUsedText).toBe('3.56 GiB / 5.67 GiB')
        expect(vm.memoryFoot).toBe('已用 63% · 可用 2.11 GiB')
    })

    it('采样未到位时显示占位符，不显示 0%', () => {
        const vm = page({ available: true, cpu: {}, memory: {} })
        expect(vm.cpuPercentText).toBe('-')
        expect(vm.memoryUsedText).toBe('-')
        expect(vm.cpuFoot).toBe('等待采样')
        expect(vm.memoryFoot).toBe('等待采样')
        // 宽度归零但配色是灰色的「无数据」，不是低负载的蓝色
        expect(vm.meterStyle(undefined).width).toBe('0%')
        expect(vm.meterClass(undefined)).toBe('meter-fill off')
    })

    it('采样失败时说明不可用，不冒充 0 占用', () => {
        const vm = page({ available: false, message: '读取 /proc 失败' })
        expect(vm.cpuPercentText).toBe('-')
        expect(vm.memoryUsedText).toBe('-')
        expect(vm.memoryFoot).toBe('暂不可用')
    })

    it('占用越高配色越醒目，且宽度不会溢出卡片', () => {
        const vm = page({})
        expect(vm.meterClass(30)).toBe('meter-fill')
        expect(vm.meterClass(50)).toBe('meter-fill')
        expect(vm.meterClass(50.1)).toBe('meter-fill warn')
        expect(vm.meterClass(80)).toBe('meter-fill warn')
        expect(vm.meterClass(80.1)).toBe('meter-fill danger')
        expect(vm.meterStyle(120).width).toBe('100%')
    })

    it('CPU 使用率 null（两次采样间隔内没有时间推进）同样显示占位符', () => {
        const vm = page({ available: true, cpu: { percent: null, cores: 4 }, memory: {} })
        expect(vm.cpuPercentText).toBe('-')
        expect(vm.cpuFoot).toBe('4 核')
    })
})
