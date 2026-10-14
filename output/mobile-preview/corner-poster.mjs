import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'

// 用 SVG data URI 充当真实封面（480x600 竖版海报）
const poster = 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1d4ed8"/><stop offset="1" stop-color="#9333ea"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/><text x="240" y="300" font-size="44" fill="#fff" text-anchor="middle" font-family="sans-serif">POSTER</text></svg>`).toString('base64')

const moviePayload = { id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画',
  cover: poster, url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false }
const photoPayload = { ...moviePayload }
const msg = (p) => ({ id: 2, sessionId: 'movie', role: 'bot', type: 'image', content: p.cover,
  contentText: '[12345] 凡人修仙传 年番（2024 / 大陆 / 动画）\n详情：https://www.dygangs.com/movie/12345.html',
  payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 08:30:00' })
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 08:30:00' }]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
for (const [name, width, height, dpr] of [['desktop', 1280, 900, 1], ['mobile', 390, 844, 3]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg(moviePayload)]))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json([]))
    if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(base + '/#/messages?session=movie')
  await page.waitForTimeout(2000)
  const info = await page.evaluate(() => {
    const b = document.querySelector('.bubble'); const img = b.querySelector('img.cover'); const btn = b.querySelector('.subscribe-corner')
    const r = (el) => el ? { x: Math.round(el.getBoundingClientRect().x), y: Math.round(el.getBoundingClientRect().y), w: Math.round(el.getBoundingClientRect().width), h: Math.round(el.getBoundingClientRect().height) } : null
    return { img: r(img), btn: r(btn), bubble: r(b), natural: img && img.naturalWidth }
  })
  console.log(name, JSON.stringify(info))
  await page.screenshot({ path: `${dir}/corner-poster-${name}.png` })
  await page.close()
}
await browser.close()
