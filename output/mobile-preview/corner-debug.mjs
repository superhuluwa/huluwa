import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const moviePayload = { id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画',
  cover: 'https://image.tmdb.org/t/p/w300/hE24GM7t8m9QKMR2woz0nBTZ4vE.jpg',
  url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false }
const msg = { id: 2, sessionId: 'movie', role: 'bot', type: 'image', content: moviePayload.cover,
  contentText: '[12345] 凡人修仙传 年番（2024 / 大陆 / 动画）\n详情：https://www.dygangs.com/movie/12345.html',
  payload: JSON.stringify(moviePayload), read: true, createdAt: '2026-10-14 08:30:00' }
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 08:30:00' }]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg]))
  if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
  if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json([]))
  if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
  return route.fulfill(json([]))
})
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
await page.goto(base + '/#/messages?session=movie')
await page.waitForTimeout(2500)
console.log(JSON.stringify(await page.evaluate(() => {
  const bubble = document.querySelectorAll('.bubble')[0]
  const img = bubble.querySelector('img.cover')
  const fb = bubble.querySelector('.cover-fallback')
  const btn = bubble.querySelector('.subscribe-corner')
  const r = (el) => el ? { x: Math.round(el.getBoundingClientRect().x), y: Math.round(el.getBoundingClientRect().y), w: Math.round(el.getBoundingClientRect().width), h: Math.round(el.getBoundingClientRect().height) } : null
  return { bubbleClass: bubble.className, img: r(img), natural: img && { w: img.naturalWidth, h: img.naturalHeight }, fallback: r(fb), btn: r(btn), bubble: r(bubble) }
}), null, 1))
await browser.close()
