/** 预加载与实际导航共用同一个请求；失败后允许用户再次进入页面时重试。 */
export function createRouteLoader(importPage) {
    let pending
    return () => {
        if (!pending) {
            pending = Promise.resolve().then(importPage).catch((error) => {
                pending = undefined
                throw error
            })
        }
        return pending
    }
}

/** 只加载页面代码，不挂载组件、不触发页面接口；预加载失败不打断当前页面。 */
export async function preloadRoute(router, name) {
    try {
        const route = router.resolve({ name })
        await Promise.all(route.matched.flatMap((record) =>
            Object.values(record.components || {})
                .filter((component) => typeof component === 'function')
                .map((load) => load())
        ))
        return true
    } catch (error) {
        return false
    }
}
