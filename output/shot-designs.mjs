import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output'
const url = 'file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-designs.html'
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1360, height: 1200 }, deviceScaleFactor: 2 })
await page.goto(url)
await page.waitForTimeout(600)
// 左侧四个方案（默认未订阅态）单独截图，放大看细节
await page.locator('.variant').nth(0).screenshot({ path: dir + '/d1-minimal.png' })
await page.locator('.variant').nth(1).screenshot({ path: dir + '/d2-glass.png' })
await page.locator('.variant').nth(2).screenshot({ path: dir + '/d3-ribbon.png' })
await page.locator('.variant').nth(3).screenshot({ path: dir + '/d4-icon-hover.png' })
await page.locator('.variant').nth(4).screenshot({ path: dir + '/d5-inset.png' })
await page.locator('.variant').nth(5).screenshot({ path: dir + '/d6-glow.png' })
// 图标按钮：鼠标悬停展开态
await page.locator('.variant').nth(3).locator('.sb-icon').hover()
await page.waitForTimeout(500)
await page.locator('.variant').nth(3).screenshot({ path: dir + '/d4-icon-open.png' })
await page.screenshot({ path: dir + '/designs-overview.png', fullPage: true })
console.log('ok')
await browser.close()
