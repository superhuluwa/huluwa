import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const rows = [
  { id: 41, seriesTitle: '庆余年 第二季', season: 2, episode: 12, status: 'completed', progressPercent: 100, phase: 'completed', downloadSpeed: '', attempts: 1, nextAttemptAt: '', savePath: '/vol1/1000/downloads/庆余年 第二季/庆余年S02E012.mp4', message: '', paused: 0 },
  { id: 33, seriesTitle: '三体', season: 1, episode: '05', status: 'failed', progressPercent: null, phase: 'failed', downloadSpeed: '', attempts: 3, nextAttemptAt: '2026-10-13 22:10:00', savePath: '', message: '分片下载失败', paused: 0 }
]
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  const data = url.includes('/hls/downloads') ? rows : []
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
})
await page.goto('http://localhost:5173/#/downloads')
await page.waitForTimeout(1200)
console.log('桌面端仍用表格:', await page.locator('.n-data-table').count() > 0, '| 卡片数:', await page.locator('.task-card').count())
const header = await page.locator('.n-data-table-th').allInnerTexts()
console.log('表头:', JSON.stringify(header))
await page.screenshot({ path: 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview/desktop-table-check.png' })
await browser.close()
