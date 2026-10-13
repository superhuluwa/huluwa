import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { message, dialog, notification } from './libs/discrete'
import './styles/tokens.css'
// Font Awesome 7 图标（自 quantum-web 迁移）：此前 huluwa 只写了 fa-* class 却没引入字体，
// 所有图标都渲染为空白。
import './assets/fontawesome-7.1/css/all.min.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.config.globalProperties.$message = message
app.config.globalProperties.$dialog = dialog
app.config.globalProperties.$notification = notification

app.mount('#app')
