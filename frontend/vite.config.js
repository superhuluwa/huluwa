import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 生产构建直接产出到 Spring Boot 的静态资源目录，后端单进程即可托管整个控制台
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8000',
                changeOrigin: true
            },
            // 聊天 WebSocket 通道（dev 代理转发到后端；生产同源直连不需要）
            '/ws': {
                target: 'http://127.0.0.1:8000',
                ws: true,
                changeOrigin: true
            }
        }
    },
    build: {
        outDir: '../src/main/resources/static',
        emptyOutDir: true
    }
})