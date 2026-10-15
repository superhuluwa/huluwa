// Docker 一次性采样通常没有 precpu_stats；使用同一采样通道的上一帧计算 CPU。
export function resourceSnapshot(stats = {}, previousCpu) {
    const cpu = stats.cpu_stats || {}
    const previous = previousCpu || stats.precpu_stats || {}
    const cpuDelta = Number(cpu.cpu_usage?.total_usage || 0) - Number(previous.cpu_usage?.total_usage || 0)
    const systemDelta = Number(cpu.system_cpu_usage || 0) - Number(previous.system_cpu_usage || 0)
    const cores = Number(cpu.online_cpus || cpu.cpu_usage?.percpu_usage?.length || 1)
    const cpuPercent = previous.system_cpu_usage > 0 && cpuDelta > 0 && systemDelta > 0
        ? cpuDelta / systemDelta * cores * 100 : Number(stats.CPUPercRaw || 0)
    const memoryBytes = Number(stats.memory_stats?.usage ?? stats.MemoryUsageRaw ?? 0)
    return {
        cpuPercent: Number.isFinite(cpuPercent) ? Math.max(0, cpuPercent) : 0,
        memoryBytes: Number.isFinite(memoryBytes) ? Math.max(0, memoryBytes) : 0,
        previousCpu: { ...cpu, cpu_usage: { ...cpu.cpu_usage } }
    }
}

// 字节格式化与首页内存卡片共用同一实现，避免两处单位口径漂移
export { formatBytes } from '@/utils/format'

export function memoryClass(bytes) {
    return bytes > 5 * 1024 ** 3 ? 'high-usage' : bytes > 1024 ** 3 ? 'medium-usage' : 'low-usage'
}

export function appendSample(history, sample) {
    return [...history, sample].slice(-60)
}
