import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const base = 'http://localhost:5173'

const moviePayload = {
  id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画',
  cover: 'https://image.tmdb.org/t/p/w300/hE24GM7t8m9QKMR2woz0nBTZ4vE.jpg',
  url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false
}
const notice = (id, kind, sessionId, content, contentText, payload) => ({
  id, sessionId, role: 'bot', type: kind, content, contentText, payload: JSON.stringify(payload),
  read: true, createdAt: '2026-10-14 08:30:00'
})
const sessions = [
  { id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 08:30:00' },
  { id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 08:30:00' },
  { id: 'system', title: '系统通知', kind: 'system', unread: 0, updatedAt: '2026-10-14 08:30:00' }
]
const movieMessages = [
  notice(1, 'text', 'movie', '关键字「凡人修仙传」命中 1 条，展示前 1 条：', null, null),
  notice(2, 'image', 'movie', moviePayload.cover,
    '[12345] 凡人修仙传 年番（2024 / 大陆 / 动画）\n详情：https://www.dygangs.com/movie/12345.html', moviePayload)
]
const dyttPayload = {
  id: '90001', title: '流浪地球 2', year: '2023', origin: '大陆', genre: '科幻',
  cover: '', url: 'https://www.dyttzyw.com/detail/90001', source: 'dytt',
  playSources: [{ name: 'dyttm3u8', total: 2, items: [{ name: 'HD', url: 'https://x/1.m3u8' }, { name: 'BD', url: 'https://x/2.m3u8' }] }]
}
const dyttMessages = [
  notice(3, 'image', 'dytt', 'https://image.tmdb.org/t/p/w300/2CAL2433ZeIihfX1Hb2139CX0pW.jpg',
    '[90001] 流浪地球 2（2023 / 大陆 / 科幻）', dyttPayload)
]

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
const report = []
for (const [name, width, height, dpr, session] of [['desktop', 1280, 900, 1, 'movie'], ['mobile', 390, 844, 3, 'movie'], ['mobile-dytt', 390, 844, 3, 'dytt']]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json(movieMessages))
    if (url.includes('/chat/sessions/dytt/messages')) return route.fulfill(json(dyttMessages))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json([]))
    if (url.includes('/chat/subscriptions/settings')) return route.fulfill(json({ downloader: 'qb', saveRoot: '/vol1/1000/downloads', intervalMinutes: 60 }))
    if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(`${base}/#/messages?session=${session}`)
  await page.waitForTimeout(1500)
  const corner = page.locator('.subscribe-corner')
  const oldBtn = page.locator('.movie-actions .subscribe-btn')
  const tip = page.locator('.subscribe-tip')
  const box = await corner.first().boundingBox().catch(() => null)
  const bubbleBox = await page.locator('.bubble').last().boundingBox().catch(() => null)
  const metrics = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > window.innerWidth + 1 }))
  report.push({ name, corner: await corner.count(), oldBtn: await oldBtn.count(), tip: await tip.count(),
    cornerBox: box && { x: Math.round(box.x), y: Math.round(box.y), w: Math.round(box.width), h: Math.round(box.height) },
    bubbleRight: bubbleBox && Math.round(bubbleBox.x + bubbleBox.width), overflow: metrics.overflow })
  await page.screenshot({ path: `${dir}/corner-${name}.png` })
  await page.close()
}
await browser.close()
for (const row of report) console.log(JSON.stringify(row))
