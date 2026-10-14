import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:4173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E3A8A"/><stop offset="0.5" stop-color="#6D28D9"/><stop offset="1" stop-color="#0EA5E9"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/></svg>').toString('base64')
const mk = (sub, id, src, title) => ({ id, title, year: '2024', origin: '大陆', genre: '动画', cover: poster,
  url: src === 'dytt' ? `https://www.dyttzyw.com/detail/${id}` : `https://www.dygangs.com/movie/${id}.html`,
  source: src, subscribed: sub, ...(src === 'dytt' ? { playSources: [{ name: 'dyttm3u8', total: 1, items: [{ title: 'HD', url: 'https://x/1.m3u8' }] }] } : {}) })
const msg = (p, id, s) => ({ id, sessionId: s, role: 'bot', type: 'image', content: p.cover, contentText: `[${p.id}] ${p.title}（${p.year} / ${p.origin} / ${p.genre}）\n详情：${p.url}`, payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 09:00:00' })
const json = (d) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data: d }) })
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 09:00:00' }, { id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 09:00:00' }]
const browser = await chromium.launch({ channel: 'msedge' })
const out = []
for (const [name, width, height, dpr] of [['desktop', 1280, 900, 1], ['mobile', 390, 844, 3]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg(mk(false, '1', 'dygangs', '凡人修仙传 年番'), 1, 'movie'), msg(mk(true, '2', 'dygangs', '凡人修仙传 年番'), 2, 'movie')]))
    if (url.includes('/chat/sessions/dytt/messages')) return route.fulfill(json([msg(mk(false, '9', 'dytt', '流浪地球 2'), 3, 'dytt'), msg(mk(true, '10', 'dytt', '流浪地球 2'), 4, 'dytt')]))
    if (url.includes('/hls/subscriptions')) return route.fulfill(json([{ id: 1, resId: '10', title: '流浪地球 2', enabled: true }]))
    if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json(['https://www.dygangs.com/movie/2.html']))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  for (const s of ['movie', 'dytt']) {
    await page.goto(`${base}/#/messages?session=${s}`)
    await page.waitForTimeout(1600)
    const res = await page.evaluate(() => [...document.querySelectorAll('.bubble')].map((b) => {
      const btn = b.querySelector('.subscribe-corner')
      const cs = getComputedStyle(btn), t = btn.getBoundingClientRect(), bb = b.getBoundingClientRect()
      return { text: btn.innerText.trim(), border: cs.borderStyle, bg: cs.backgroundColor, color: cs.color,
               inCard: t.right <= bb.right + 1 && t.bottom <= bb.bottom + 1, size: [Math.round(t.width), Math.round(t.height)] }
    }))
    out.push([name, s, JSON.stringify(res)])
    await page.screenshot({ path: `${dir}/final-${s}-${name}.png` })
  }
  await page.close()
}
for (const r of out) console.log(r.join(' | '))
await browser.close()
