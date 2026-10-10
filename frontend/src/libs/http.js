import axios from 'axios'
import { message } from '@/libs/discrete'

const TOKEN_KEY = 'huluwa.token'

const instance = axios.create({
    baseURL: '/api',
    timeout: 20000
})

instance.interceptors.request.use((config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token && !config.skipAuth) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

instance.interceptors.response.use(
    (response) => {
        const body = response.data
        // 后端统一信封 { code, message, data }
        if (body && typeof body === 'object' && 'code' in body) {
            if (body.code === 200) {
                return body.data
            }
            message.error(body.message || '请求失败')
            return Promise.reject(new Error(body.message || '请求失败'))
        }
        return body
    },
    (error) => {
        const body = error.response && error.response.data
        const status = error.response && error.response.status
        const text = (body && body.message) || error.message || '网络异常'
        if (status === 401) {
            localStorage.removeItem(TOKEN_KEY)
            if (window.location.hash !== '#/login') {
                window.location.hash = '#/login'
            }
            message.warning('登录状态已失效，请重新登录')
        } else {
            message.error(text)
        }
        return Promise.reject(error)
    }
)

// 统一解包信封里的 data，页面直接拿业务数据
export default {
    get(url, params, config) {
        return instance.get(url, { params, ...config })
    },
    post(url, data, config) {
        return instance.post(url, data, config)
    },
    put(url, data, config) {
        return instance.put(url, data, config)
    },
    delete(url, config) {
        return instance.delete(url, config)
    }
}