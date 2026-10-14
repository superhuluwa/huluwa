import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output'
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1024, height: 900 } })
const errors = []
page.on('pageerror', e => errors.push(String(e)))
page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()) })
await page.goto('file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-subscribe-variants.html')
await page.waitForTimeout(1200)
const frame = page.frameLocator('iframe')
console.log('carousel:', await frame.locator('.viz-carousel').count(), 'variants:', await frame.locator('[data-variant]').count())
console.log('visible variant:', await frame.locator('[data-variant]:visible').getAttribute('data-variant'))
console.log('subscribe buttons visible:', await frame.locator('.hw-sub:visible').count())
console.log('icons replaced:', await frame.locator('.lucide').count(), 'placeholders:', await frame.locator('[data-lucide]').count())
await page.screenshot({ path: dir + '/variant-1-glass.png' })
// 切到下一版
for (const name of ['角标收藏夹', '图标按钮', '封面内嵌']) {
  await page.locator('iframe').contentFrame()
  const frameEl = page.frames().find(f => f !== page.mainFrame())
  await frameEl.locator('.viz-carousel-controls button').last().click()
  await page.waitForTimeout(400)
  const vis = await frameEl.locator('[data-variant]:visible').getAttribute('data-variant')
  console.log('after next ->', vis)
  await page.screenshot({ path: `${dir}/variant-${name}.png` })
}
console.log('errors:', errors.length ? errors : 'none')
await browser.close()
