/** 两种来源共用加入时间排序；来源与编号共同标识一条订阅。 */
export function mergeSubscriptions(movieRows, hlsRows) {
    const wrap = (rows, source) => (Array.isArray(rows) ? rows : []).map(item => ({
        ...item,
        source,
        subscriptionKey: `${source}:${item.id}`
    }))
    return [...wrap(movieRows, 'dygangs'), ...wrap(hlsRows, 'dytt')].sort((left, right) => {
        const leftTime = left.createdAt || left.baselineAt || '9999-12-31 23:59:59'
        const rightTime = right.createdAt || right.baselineAt || '9999-12-31 23:59:59'
        return leftTime.localeCompare(rightTime)
            || Number(left.id) - Number(right.id)
            || left.source.localeCompare(right.source)
    })
}
