import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const rows = [
  { id: 41, seriesTitle: '庆余年 第二季', season: 2, episode: 12, status: 'completed', progressPercent: 100, phase: 'completed', downloadSpeed: '', attempts: 1, nextAttemptAt: '', savePath: '/vol1/1000/downloads/庆余年 第二季/庆余年S02E012.mp4', message: '', paused: 0 },
  { id: 42, seriesTitle: '甄嬛传', season: 1, episode: 76, status: 'pending', progressPercent: null, phase: 'pending', downloadSpeed: '', attempts: 0, nextAttemptAt: '2026-10-13 23:00:00', savePath: '', message: '', paused: 1 }
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
for (const card of await page.locator('.task-card').all()) {
  const log = await card.locator('.task-head .n-button').boundingBox()
  const pill = await card.locator('.task-head .pill').boundingBox()
  console.log(await card.locator('.pill').innerText(), '| logRight =', Math.round(log.x + log.width), '| pillLeft =', Math.round(pill.x), '| 在左侧 =', (log.x + log.width) <= pill.x)
}
await page.screenshot({ path: dir + '/mobile-completed-log.png', fullPage: true })
await browser.close()
