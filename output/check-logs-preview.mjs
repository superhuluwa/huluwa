import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:4173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/check-logs-preview'
import fs from 'node:fs'
fs.mkdirSync(dir, { recursive: true })

const json = (d) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data: d }) })
const logs = [
  { id: 12, source: 'dygangs', subscriptionId: 3, seriesTitle: '斗破苍穹 第5季', trigger: 'scheduled', status: 'updated',
    foundCount: 2, submittedCount: 2, message: '发现 2 条新磁力，已提交下载 2 条。', durationMs: 4820, createdAt: '2026-10-14 10:00:00',
    details: ['第 214 集 [2160P] 斗破苍穹 第5季214.2160p.HD国语中字无水印.mp4', '第 213 集 [2160P] 斗破苍穹 第5季213.2160p.HD国语中字无水印.mp4'] },
  { id: 11, source: 'dytt', subscriptionId: 9, seriesTitle: '凡人修仙传 年番', trigger: 'manual', status: 'updated',
    foundCount: 3, submittedCount: 3, message: '发现 3 集更新，已加入离线下载队列。', durationMs: 1560, createdAt: '2026-10-14 09:30:00',
    details: ['第 141 集 第141集', '第 140 集 第140集', '第 139 集 第139集', '第 138 集 第138集', '第 137 集 第137集', '第 136 集 第136集'] },
  { id: 10, source: 'dygangs', subscriptionId: 3, seriesTitle: '斗破苍穹 第5季', trigger: 'scheduled', status: 'ok',
    foundCount: 0, submittedCount: 0, message: '巡检完成：暂未发现新磁力。', durationMs: 3120, createdAt: '2026-10-14 09:00:00', details: [] },
  { id: 9, source: 'dytt', subscriptionId: 9, seriesTitle: '凡人修仙传 年番', trigger: 'scheduled', status: 'failed',
    foundCount: 0, submittedCount: 0, message: '检查失败：连接采集接口超时，请检查网络后重试。', durationMs: 20040, createdAt: '2026-10-14 08:00:00', details: [] },
  { id: 8, source: 'dygangs', subscriptionId: 7, seriesTitle: '流浪地球 2', trigger: 'scheduled', status: 'baseline',
    foundCount: 18, submittedCount: 0, message: '补建磁力基线 18 条，本轮不触发下载。', durationMs: 6400, createdAt: '2026-10-14 07:00:00', details: [] }
]

const browser = await chromium.launch({ channel: 'msedge' })
const messages = []
for (const [name, width, height, dpr] of [['desktop', 1440, 900, 1], ['mobile', 390, 844, 3]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const u = route.request().url()
    if (u.includes('/chat/check-logs')) return route.fulfill(json(logs))
    if (u.includes('/chat/sessions')) return route.fulfill(json([]))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  const errors = []
  page.on('pageerror', (e) => errors.push('pageerror: ' + e))
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()) })
  await page.goto(base + '/#/check-logs')
  await page.waitForTimeout(1500)
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
  const cards = await page.locator('.log-card').count()
  const moreVisible = await page.locator('.detail-more').count()
  if (name === 'mobile' && moreVisible > 0) {
    await page.locator('.detail-more').first().click()
    await page.waitForTimeout(300)
    const detailLines = await page.locator('.log-card').nth(1).locator('.detail-line').count()
    messages.push(['mobile-expand', 'lines=' + detailLines])
    await page.screenshot({ path: dir + '/check-logs-mobile-expanded.png' })
    await page.locator('.detail-more').first().click()
    await page.waitForTimeout(200)
  }
  if (name === 'desktop') {
    const expandToggle = page.locator('.logs-page .n-data-table-tbody .n-data-table-tr .n-data-table-td--expand').first()
    await expandToggle.click()
    await page.waitForTimeout(300)
    const expandedLines = await page.locator('.expand-line').count()
    messages.push(['desktop-expand', 'lines=' + expandedLines])
    await page.screenshot({ path: dir + '/check-logs-desktop-expanded.png' })
  }
  const rows = await page.locator('.n-data-table-tbody .n-data-table-tr').count()
  const heading = await page.locator('.crumb').innerText().catch(() => '')
  messages.push([name, 'cards=' + cards, 'rows=' + rows, 'overflow=' + overflow, 'crumb=' + heading.replace(/\s+/g, ' ').trim(), 'errors=' + errors.length])
  if (errors.length) messages.push(['ERR', name, errors.join(' | ')])
  await page.screenshot({ path: dir + '/check-logs-' + name + '.png', fullPage: false })
  await page.close()
}
for (const m of messages) console.log(m.join(' | '))
await browser.close()
