import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 电影港卡片布局契约：
 *  - 订阅按钮固定在卡片右上角（绝对定位浮在封面之上），不再占操作条位置；
 *  - 原来「监听新增剧集并自动下载 / 每小时巡检更新」那行提示文字已删除；
 *  - 有封面的卡片操作条只留「资源列表 + 订阅设置」；
 *  - 无封面（纯文本）的卡片仍保留操作条里的订阅按钮，保证不会出现无法订阅的卡片。
 */
function parts() {
    const file = fileURLToPath(new URL('../src/views/message/components/MessageBubble.vue', import.meta.url))
    const source = readFileSync(file, 'utf8')
    return { source, template: source.slice(0, source.lastIndexOf('</template>')) }
}

describe('电影港卡片：订阅按钮固定在右上角', () => {
    it('右上角订阅按钮存在，且只对有封面的电影港卡片渲染', () => {
        const { template } = parts()
        expect(template).toContain('class="subscribe-corner"')
        expect(template).toMatch(/v-if="isMovie && isImage"[\s\S]{0,400}class="subscribe-corner"/)
        expect(template).toContain('@click.stop="onSubscribe"')
    })

    it('原来那行「监听新增剧集并自动下载 / 每小时巡检更新」提示已删除', () => {
        const { source } = parts()
        expect(source).not.toContain('监听新增剧集并自动下载')
        expect(source).not.toContain('每小时巡检更新')
    })

    it('有封面的卡片操作条只剩资源列表与订阅设置；纯文本卡片仍可订阅', () => {
        const { template } = parts()
        const coverBranch = template.slice(template.indexOf('<RichText class="cover-caption"'), template.indexOf('<div v-else-if="isDytt"'))
        expect(coverBranch).toContain('资源列表')
        expect(coverBranch).toContain('订阅设置')
        expect(coverBranch).not.toContain("subscribed ? '已订阅'")
        const textBranch = template.slice(template.indexOf('<MovieSearchWatchAction'), template.lastIndexOf('<div v-else-if="isDytt" class="movie-actions">'))
        expect(textBranch).toContain('class="subscribe-btn"')
    })

    it('右上角按钮样式：绝对定位贴右上，并让复制工具条让位到左侧', () => {
        const { source } = parts()
        const style = source.slice(source.lastIndexOf('<style scoped>'))
        expect(style).toMatch(/\.subscribe-corner\s*\{[\s\S]*?position:\s*absolute;[\s\S]*?top:\s*10px;[\s\S]*?right:\s*10px;/)
        expect(style).toMatch(/\.bubble\.has-corner-action \.bubble-tools\s*\{[\s\S]*?right:\s*auto;[\s\S]*?left:\s*8px;/)
    })
})
