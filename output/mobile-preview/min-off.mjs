import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E3A8A"/><stop offset="0.5" stop-color="#6D28D9"/><stop offset="1" stop-color="#0EA5E9"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/></svg>').toString('base64')
const movieOff = { id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画', cover: poster, url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed: false }
const dyttOff = { id: '90001', title: '流浪地球 2', year: '2023', origin: '大陆', genre: '科幻', cover: poster, url: 'https://www.dyttzyw.com/detail/90001', source: 'dytt', subscribed: false, playSources: [{ name: 'dyttm3u8', total: 1, items: [{ title: 'HD', url: 'https://x/1.m3u8' }] }] }
const msg = (p, id, s) => ({ id, sessionId: s, role: 'bot', type: 'image', content: p.cover, contentText: `[${p.id}] ${p.title}（${p.year} / ${p.origin} / ${p.genre}）\n详情：${p.url}`, payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 09:00:00' })
const json = (d) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data: d }) })
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 09:00:00' }, { id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 09:00:00' }]
const browser = await chromium.launch({ channel: 'msedge' })
for (const [session, tag] of [['movie', 'movie'], ['dytt', 'dytt']]) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    if (url.includes(`/chat/sessions/${session}/messages`)) return route.fulfill(json([msg(session === 'movie' ? movieOff : dyttOff, 1, session)]))
    if (url.includes('/hls/subscriptions')) return route.fulfill(json([]))
    if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(`${base}/#/messages?session=${session}`)
  await page.waitForTimeout(1600)
  await page.locator('.variant').first().waitFor({ state: 'attached' }).catch(() => {})
  await page.screenshot({ path: `${dir}/min3-${tag}-off.png` })
  await page.close()
}
await browser.close()
console.log('ok')
