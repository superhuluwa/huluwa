import { defineStore } from 'pinia'
import http from '@/libs/http'

const TOKEN_KEY = 'huluwa.token'
const USER_KEY = 'huluwa.username'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem(TOKEN_KEY) || '',
        username: localStorage.getItem(USER_KEY) || '',
        initialized: true
    }),
    getters: {
        logged: (state) => Boolean(state.token)
    },
    actions: {
        async fetchStatus() {
            const data = await http.get('/auth/status')
            this.initialized = Boolean(data.initialized)
            if (data.username) {
                this.username = this.username || data.username
            }
            return data
        },
        async setup(payload) {
            const data = await http.post('/auth/setup', payload, { skipAuth: true })
            this.applyToken(data)
            return data
        },
        async login(payload) {
            const data = await http.post('/auth/login', payload, { skipAuth: true })
            this.applyToken(data)
            return data
        },
        async changePassword(payload) {
            await http.post('/auth/password', payload)
            // 服务端改密后历史令牌全部失效，必须重新登录
            this.clear()
        },
        applyToken(data) {
            this.token = data.token
            this.username = data.username || this.username
            localStorage.setItem(TOKEN_KEY, this.token)
            localStorage.setItem(USER_KEY, this.username)
        },
        clear() {
            this.token = ''
            localStorage.removeItem(TOKEN_KEY)
        }
    }
})