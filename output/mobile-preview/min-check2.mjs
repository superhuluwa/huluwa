import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E3A8A"/><stop offset="0.5" stop-color="#6D28D9"/><stop offset="1" stop-color="#0EA5E9"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/></svg>').toString('base64')

const movieOff = { id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画', cover: poster, url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false }
const movieOn = { ...movieOff, id: '12346', url: 'https://www.dygangs.com/movie/12346.html', subscribed: true }
const dyttOff = { id: '90001', title: '流浪地球 2', year: '2023', origin: '大陆', genre: '科幻', cover: poster, url: 'https://www.dyttzyw.com/detail/90001', source: 'dytt', subscribed: false,
  playSources: [{ name: 'dyttm3u8', total: 2, items: [{ title: 'HD', url: 'https://x/1.m3u8' }] }] }
const dyttOn = { ...dyttOff, id: '90002', url: 'https://www.dyttzyw.com/detail/90002', subscribed: true }

const msg = (p, id, session) => ({ id, sessionId: session, role: 'bot', type: 'image', content: p.cover,
  contentText: `[${p.id}] ${p.title}（${p.year} / ${p.origin} / ${p.genre}）\n详情：${p.url}`,
  payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 09:00:00' })

const sessions = [
  { id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 09:00:00' },
  { id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 09:00:00' }
]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const hlsSubs = [{ id: 1, resId: '90002', title: '流浪地球 2', enabled: true }]

const browser = await chromium.launch({ channel: 'msedge' })
const report = []
for (const [name, width, height, dpr] of [['desktop', 1280, 900, 1], ['mobile', 390, 844, 3]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg(movieOff, 1, 'movie'), msg(movieOn, 2, 'movie')]))
    if (url.includes('/chat/sessions/dytt/messages')) return route.fulfill(json([msg(dyttOff, 3, 'dytt'), msg(dyttOn, 4, 'dytt')]))
    if (url.includes('/hls/subscriptions')) return route.fulfill(json(hlsSubs))
    if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json(['https://www.dygangs.com/movie/12346.html']))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  for (const [session, tag] of [['movie', 'movie'], ['dytt', 'dytt']]) {
    await page.goto(`${base}/#/messages?session=${session}`)
    await page.waitForTimeout(1700)
    const rows = await page.evaluate(() => [...document.querySelectorAll('.bubble')].map((b) => {
      const btn = b.querySelector('.subscribe-corner')
      const img = b.querySelector('img.cover')
      const t = btn.getBoundingClientRect(), i = img.getBoundingClientRect(), bb = b.getBoundingClientRect()
      const cs = getComputedStyle(btn)
      return { text: btn.innerText.trim(), bg: cs.backgroundColor, border: cs.borderStyle, color: cs.color,
        overlapsCover: t.left < i.right && t.top < i.bottom, inCard: t.right <= bb.right + 1 && t.bottom <= bb.bottom + 1,
        size: [Math.round(t.width), Math.round(t.height)] }
    }))
    report.push({ name, tag, rows })
    await page.screenshot({ path: `${dir}/min2-${tag}-${name}.png` })
  }
  await page.close()
}
for (const r of report) console.log(r.name, r.tag, JSON.stringify(r.rows))
await browser.close()
