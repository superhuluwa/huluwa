import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:5173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'
const poster = 'data:image/svg+xml;base64,' + Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="480" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0f766e"/><stop offset="1" stop-color="#0369a1"/></linearGradient></defs><rect width="480" height="600" fill="url(#g)"/><text x="240" y="300" font-size="44" fill="#fff" text-anchor="middle" font-family="sans-serif">CLASSIC</text></svg>`).toString('base64')
const mk = (subscribed) => ({ id: '12345', title: '凡人修仙传 年番', year: '2024', origin: '大陆', genre: '动画',
  cover: poster, url: 'https://www.dygangs.com/movie/12345.html', source: 'dygangs', subscribed })
const msg = (p, id) => ({ id, sessionId: 'movie', role: 'bot', type: 'image', content: p.cover,
  contentText: `[12345] 凡人修仙传 年番（2024 / 大陆 / 动画）\n详情：https://www.dygangs.com/movie/12345.html`,
  payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 08:30:00' })
const sessions = [{ id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 08:30:00' }]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  if (url.includes('/chat/sessions/movie/messages')) return route.fulfill(json([msg(mk(false), 1), msg(mk(true), 2)]))
  if (url.includes('/chat/sessions')) return route.fulfill(json(sessions))
  if (url.includes('/chat/subscriptions/urls')) return route.fulfill(json(['https://www.dygangs.com/movie/12345.html']))
  if (url.includes('/movie-search-watches')) return route.fulfill(json([]))
  return route.fulfill(json({ subscription: null }))
})
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
await page.goto(base + '/#/messages?session=movie')
await page.waitForTimeout(1800)
// 悬停第二条气泡，检查复制工具条是否被推到左侧且不与右上角按钮重叠
const second = page.locator('.bubble').nth(1)
await second.hover()
await page.waitForTimeout(300)
const geom = await page.evaluate(() => {
  const bubbles = [...document.querySelectorAll('.bubble')]
  const b = bubbles[1]
  const tools = b.querySelector('.bubble-tools')
  const btn = b.querySelector('.subscribe-corner')
  const r = (el) => el ? el.getBoundingClientRect() : null
  const t = r(tools), k = r(btn)
  const style = btn ? getComputedStyle(btn) : null
  return { tools: t && { x: Math.round(t.x), y: Math.round(t.y), w: Math.round(t.width) },
           btn: k && { x: Math.round(k.x), y: Math.round(k.y), w: Math.round(k.width) },
           overlap: !!(t && k && t.x < k.x + k.width && k.x < t.x + t.width && t.y < k.y + k.height && k.y < t.y + t.height),
           label: btn && btn.innerText.trim(), color: style && style.color }
})
console.log(JSON.stringify(geom))
await page.screenshot({ path: `${dir}/corner-subscribed-hover.png` })
// 点击第一张卡片的右上角订阅按钮 -> 应弹出订阅对话框而不是大图
await page.locator('.subscribe-corner').first().click()
await page.waitForTimeout(1200)
const dlg = await page.locator('.n-modal').count()
const title = await page.locator('.n-modal .msd-title, .n-modal .n-card-header__main').allInnerTexts().catch(() => [])
console.log('弹窗数:', dlg, '标题:', JSON.stringify(title))
await page.screenshot({ path: `${dir}/corner-click-dialog.png` })
await browser.close()
