/** 字节数格式化成可读单位；非有限值或负数返回占位符，0 保留单位避免只剩一个孤零零的 0。 */
export function formatBytes(bytes) {
    const value = Number(bytes)
    if (!Number.isFinite(value) || value < 0) return '-'
    if (!value) return '0 B'
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
    const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
    return (value / 1024 ** index).toFixed(2) + ' ' + units[index]
}
