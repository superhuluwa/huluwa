import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:4173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7c3aed"/><stop offset="1" stop-color="#0ea5e9"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/><text x="240" y="300" font-size="44" fill="#fff" text-anchor="middle" font-family="sans-serif">PROD</text></svg>`).toString('base64')
const mk = (sub) => ({ id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画', cover: poster,
  url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: sub })
const msg = (p, id) => ({ id, sessionId: 'movie', role: 'bot', type: 'image', content: p.cover,
  contentText: '[12345] 凡人修仙传 年番（2024 / 大陆 / 动画）\n详情：https://www.dygangs.com/movie/12345.html',
  payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 08:30:00' })
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 08:30:00' }]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
const report = []
for (const [name, width, height, dpr] of [['desktop', 1280, 900, 1], ['mobile', 390, 844, 3]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg(mk(false), 1), msg(mk(true), 2)]))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json(['https://www.dygangs.com/movie/12345.html']))
    if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(`${base}/#/messages?session=movie`)
  await page.waitForTimeout(1500)
  const info = await page.evaluate(() => {
    const bubbles = [...document.querySelectorAll('.bubble')]
    const box = (el) => el ? { x: Math.round(el.getBoundingClientRect().x), y: Math.round(el.getBoundingClientRect().y), w: Math.round(el.getBoundingClientRect().width), right: Math.round(el.getBoundingClientRect().right) } : null
    const b = bubbles[0], btn = b.querySelector('.subscribe-corner')
    return { cornerCount: document.querySelectorAll('.subscribe-corner').length,
             oldBtnCount: document.querySelectorAll('.movie-actions .subscribe-btn').length,
             tipCount: document.querySelectorAll('.subscribe-tip').length,
             btn: box(btn), bubble: box(b), btnLabel: btn && btn.innerText.trim(),
             btnInUpperRight: !!(btn && b) && (btn.getBoundingClientRect().right > b.getBoundingClientRect().right - 20) && (btn.getBoundingClientRect().top < b.getBoundingClientRect().top + 40),
             secondLabel: bubbles[1].querySelector('.subscribe-corner').innerText.trim(),
             overflow: document.documentElement.scrollWidth > window.innerWidth + 1 }
  })
  report.push({ name, ...info })
  await page.screenshot({ path: `${dir}/prod-${name}.png` })
  await page.close()
}
await browser.close()
for (const row of report) console.log(JSON.stringify(row))
