import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const rows = [
  { id: 31, seriesTitle: '凡人修仙传 年番', season: 3, episode: 128, status: 'downloading', progressPercent: 46.5, phase: 'downloading', downloadSpeed: '3.2 MB/s', attempts: 1, nextAttemptAt: '', savePath: '/vol1/1000/downloads/凡人修仙传/凡人修仙传S03E128.mp4', message: '', paused: 0 },
  { id: 33, seriesTitle: '三体', season: 1, episode: '05', status: 'failed', progressPercent: null, phase: 'failed', downloadSpeed: '', attempts: 3, nextAttemptAt: '2026-10-13 22:10:00', savePath: '', message: '分片下载失败：连接重置（get tcp 10.0.0.2:443）', paused: 0 },
  { id: 34, seriesTitle: '一个非常非常长的剧名用来验证日志按钮不会被挤走', season: 1, episode: 1, status: 'skipped', progressPercent: null, phase: 'skipped', downloadSpeed: '', attempts: 0, nextAttemptAt: '', savePath: '', message: '', paused: 0 }
]
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' })
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  const data = url.includes('/hls/downloads') ? rows : []
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
})
await page.goto('http://localhost:5173/#/downloads')
await page.waitForTimeout(1200)
// 各卡片「日志」按钮与状态胶囊的相对位置：日志应在状态左侧
for (const card of await page.locator('.task-card').all()) {
  const log = await card.locator('.task-head .n-button').boundingBox()
  const pill = await card.locator('.task-head .pill').boundingBox()
  const title = await card.locator('.task-title').innerText()
  console.log(title.slice(0, 12), '| log.right =', Math.round(log.x + log.width), '| pill.left =', Math.round(pill.x), '| ok =', (log.x + log.width) <= pill.x)
}
await page.screenshot({ path: dir + '/mobile-log-left.png', fullPage: true })
console.log('overflow:', await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1))
await browser.close()
