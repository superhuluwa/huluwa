import { createDiscreteApi } from 'naive-ui'
import { theme, themeOverrides } from '@/theme/naive'

// 非组件上下文（axios 拦截器、路由守卫）使用的全局反馈 API，与 App.vue 内 provider 共用同一主题
const { message, dialog, notification, loadingBar } = createDiscreteApi(
    ['message', 'dialog', 'notification', 'loadingBar'],
    {
        configProviderProps: {
            theme,
            themeOverrides
        }
    }
)

window.$message = message
window.$dialog = dialog
window.$notification = notification
window.$loadingBar = loadingBar

export { message, dialog, notification, loadingBar }