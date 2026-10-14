import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const poster = 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="#666"/></svg>').toString('base64')
const mk = (sub, id) => ({ id, title: '流浪地球 2', year: '2023', origin: '大陆', genre: '科幻', cover: poster,
  url: 'https://www.dyttzyw.com/detail/' + id, source: 'dytt', subscribed: sub,
  playSources: [{ name: 'dyttm3u8', total: 1, items: [{ title: 'HD', url: 'https://x/1.m3u8' }] }] })
const msg = (p, id) => ({ id, sessionId: 'dytt', role: 'bot', type: 'image', content: p.cover,
  contentText: `[${p.id}] ${p.title}`, payload: JSON.stringify(p), read: true, createdAt: '2026-10-14 09:00:00' })
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
  const url = route.request().url()
  if (url.includes('/chat/sessions/dytt/messages')) return route.fulfill(json([msg(mk(false, 1), 1), msg(mk(true, 2), 2)]))
  if (url.includes('/chat/sessions')) return route.fulfill(json([{ id: 'dytt', title: '电影天堂', kind: 'dytt', unread: 0, updatedAt: '2026-10-14 09:00:00' }]))
  return route.fulfill(json([]))
})
await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
await page.goto('http://localhost:5173/#/messages?session=dytt')
await page.waitForTimeout(1600)
console.log(JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('.bubble')].map(b => {
  const btn = b.querySelector('.subscribe-corner')
  const vm = b.__vueParentComponent
  return { text: btn && btn.innerText.trim(), disabled: btn && btn.disabled,
    hasSubClass: btn && btn.className,
    payloadSub: vm && vm.ctx && vm.ctx.subscribed }
})), null, 1))
console.log('payloads:', JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('.bubble')].map(b => {
  const vm = b.__vueParentComponent
  return vm && vm.ctx && vm.ctx.parsedPayload
}))))
await browser.close()
