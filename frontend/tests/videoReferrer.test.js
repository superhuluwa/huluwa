import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 「视频解析」会话的播放回归：
 *
 * 抖音 CDN 对携带站外 Referer 的直链请求返回 403（同一条直链不带 Referer 时 200/206），
 * 而 <video> 属于媒体元素，不支持 referrerpolicy 属性——属性加了也照发页面 Referer，
 * 于是点封面播放必然失败；「下载查看」用 rel=noreferrer 新开标签，不带 Referer 所以能播。
 *
 * 修法只能在文档级声明 Referrer 策略：同源请求照常（后端接口不受影响），
 * 跨域媒体请求不带 Referer，直链就不会被防盗链拦下。
 */
function indexHtml() {
    return readFileSync(fileURLToPath(new URL('../index.html', import.meta.url)), 'utf8')
}

function videoBubbleSource() {
    return readFileSync(
        fileURLToPath(new URL('../src/views/message/components/VideoBubble.vue', import.meta.url)), 'utf8')
}

describe('视频解析气泡的跨域播放', () => {
    it('index.html 用文档级 same-origin 策略，跨域媒体请求不携带 Referer', () => {
        expect(indexHtml()).toMatch(/<meta\s+name="referrer"\s+content="same-origin"\s*\/?>/)
    })

    it('播放器是原生 <video> 且以消息内容为直链', () => {
        const source = videoBubbleSource()
        const template = source.slice(0, source.lastIndexOf('</template>'))
        expect(template).toContain('<video')
        expect(template).toContain(':src="videoUrl"')
        expect(template).toContain('controls autoplay')
    })

    it('不再依赖对媒体元素无效的 referrerpolicy 属性', () => {
        expect(videoBubbleSource()).not.toContain('referrerpolicy')
    })
})
