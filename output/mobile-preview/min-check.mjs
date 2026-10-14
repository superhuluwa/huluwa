import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E3A8A"/><stop offset="0.5" stop-color="#6D28D9"/><stop offset="1" stop-color="#0EA5E9"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/></svg>`).toString('base64')

const movieOff = { id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画', cover: poster, url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false }
const movieOn = { ...movieOff, id: '12346', url: 'https://www.dygangs.com/movie/12346.html', subscribed: true }
const dyttOff = { id: '90001', title: '流浪地球 2', year: '2023', origin: '大陆', genre: '科幻', cover: poster, url: 'https://www.dyttzyw.com/detail/90001', source: 'dytt', subscribed: false,
  playSources: [{ name: 'dyttm3u8', total: 2, items: [{ title: 'HD', url: 'https://x/1.m3u8' }, { title: 'BD', url: 'https://x/2.m3u8' }] }] }
const dyttOn = { ...dyttOff, id: '90002', url: 'https://www.dyttzyw.com/detail/90002', subscribed: true }

const msg = (p, id, session) => ({ id, sessionId: session, role: 'bot', type: 'image', content: p.cover,
  contentText: `[${p.id}] ${p.title}（${p.year} / ${p.origin} / ${p.genre}）\n详情：${p.url}`,
  payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 09:00:00' })

const sessions = [
  { id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 09:00:00' },
  { id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 09:00:00' }
]
const movieMessages = [msg(movieOff, 1, 'movie'), msg(movieOn, 2, 'movie')]
const dyttMessages = [msg(dyttOff, 3, 'dytt'), msg(dyttOn, 4, 'dytt')]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })

const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json(movieMessages))
  if (url.includes('/chat/sessions/dytt/messages')) return route.fulfill(json(dyttMessages))
  if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
  if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json([]))
  if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
  return route.fulfill(json([]))
})
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))

for (const [session, tag] of [['movie', 'movie'], ['dytt', 'dytt']]) {
  await page.goto(`${base}/#/messages?session=${session}`)
  await page.waitForTimeout(1600)
  const rows = await page.evaluate(() => [...document.querySelectorAll('.bubble')].map((b) => {
    const btn = b.querySelector('.subscribe-corner')
    const img = b.querySelector('img.cover')
    const t = btn.getBoundingClientRect(), i = img.getBoundingClientRect(), bb = b.getBoundingClientRect()
    const cs = getComputedStyle(btn)
    return { text: btn.innerText.trim(), bg: cs.backgroundColor, border: cs.borderStyle, color: cs.color,
      overCover: t.left < i.right && t.top < i.bottom,
      inCard: t.right <= bb.right + 1 && t.left >= bb.left - 1 && t.top >= bb.top - 1 && t.bottom <= bb.bottom + 1,
      size: [Math.round(t.width), Math.round(t.height)] }
  }))
  console.log(tag, JSON.stringify(rows, null, 0))
  await page.screenshot({ path: `${dir}/min-${tag}-desktop.png` })
}
// 悬停看浅底
const hoverBtn = page.locator('.subscribe-corner').first()
await hoverBtn.hover()
await page.waitForTimeout(320)
const hoverBg = await hoverBtn.evaluate(el => getComputedStyle(el).backgroundColor)
console.log('hover 背景:', hoverBg)
await page.screenshot({ path: dir + '/min-dytt-hover.png' })
await browser.close()
