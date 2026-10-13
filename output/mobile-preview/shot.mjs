import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'

const magnetRows = [
  { id: 11, seriesTitle: '沙丘：预言 第一季', episode: '06', quality: '2160p', downloader: 'qb', status: 'downloading', savePath: '/vol1/1000/downloads/沙丘：预言 第一季', createdAt: '2026-10-13 21:04:11', message: '' },
  { id: 12, seriesTitle: '怪奇物语 第五季 - WEB-DL 4K HDR 内封中字', episode: '02', quality: '1080p', downloader: 'xunlei', status: 'completed', savePath: '/vol1/1000/downloads/怪奇物语 第五季', createdAt: '2026-10-13 20:31:02', message: '' },
  { id: 13, seriesTitle: '漫长的季节', episode: '10', quality: '1080p', downloader: 'qb', status: 'failed', savePath: '/vol1/1000/downloads/漫长的季节', createdAt: '2026-10-13 19:22:40', message: '下载器返回 404：任务已被删除' }
]

const hlsRows = [
  { id: 31, seriesTitle: '凡人修仙传 年番', season: 3, episode: 128, status: 'downloading', progressPercent: 46.5, phase: 'downloading', downloadSpeed: '3.2 MB/s', attempts: 1, nextAttemptAt: '', savePath: '/vol1/1000/downloads/凡人修仙传/凡人修仙传S03E128.mp4', message: '', paused: 0 },
  { id: 32, seriesTitle: '斗破苍穹 年番', season: 1, episode: 64, status: 'verifying', progressPercent: 99, phase: 'verifying', downloadSpeed: '', attempts: 2, nextAttemptAt: '', savePath: '/vol1/1000/downloads/斗破苍穹/斗破苍穹S01E064.mkv', message: '', paused: 0 },
  { id: 33, seriesTitle: '三体', season: 1, episode: "05", status: 'failed', progressPercent: null, phase: 'failed', downloadSpeed: '', attempts: 3, nextAttemptAt: '2026-10-13 22:10:00', savePath: '', message: '分片下载失败：连接重置（get tcp 10.0.0.2:443）', paused: 0 }
]

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })

async function open(page, hash, handlers) {
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    for (const [frag, body] of Object.entries(handlers)) {
      if (url.includes(frag)) return route.fulfill(json(body))
    }
    return route.fulfill(json({}))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(base + hash)
  await page.waitForTimeout(900)
}

const browser = await chromium.launch({ channel: 'msedge' })
const shots = []
for (const [name, width, height, dpr] of [['mobile', 390, 844, 3], ['narrow', 360, 780, 3], ['desktop', 1280, 900, 1]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await open(page, '#/downloads', { '/chat/subscriptions/downloads': magnetRows, '/hls/downloads': hlsRows })
  await page.screenshot({ path: `${dir}/${name}-hls.png` })
  await page.locator('.n-tabs-tab').filter({ hasText: '磁力下载' }).click()
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${dir}/${name}-magnet.png` })
  const metrics = await page.evaluate(() => ({
    docScroll: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
    horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 1
  }))
  shots.push([name, JSON.stringify(metrics)])
  await page.close()
}
await browser.close()
for (const line of shots) console.log(line[0], line[1])




