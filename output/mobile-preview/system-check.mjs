import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const base = 'http://localhost:4173'
const dir = 'C:/Users/Jing/Desktop/huluwa/output/mobile-preview'

const notify = (id, level, source, title, body, at) => ({
  id, sessionId: 'system', role: 'bot', type: 'notify', content: body, contentText: title,
  payload: JSON.stringify({ level, source, title }), read: false, createdAt: at
})
const notices = [
  notify(9, 'error', 'qb', 'qBittorrent 连接失败', '无法连接到 http://192.168.3.202:8080，请检查下载器地址与账号密码。', '2026-10-14 08:30:12'),
  notify(10, 'info', 'system', '控制台已启动', '葫芦娃助手已在 192.168.3.202 上完成启动，共加载 5 个会话。', '2026-10-14 08:41:00'),
  notify(11, 'success', 'hls', '《凡人修仙传》第 128 集下载完成', '已完成分片校验并保存到 /vol1/1000/downloads/凡人修仙传，可直接在飞牛影视中播放。', '2026-10-14 09:02:10'),
  notify(12, 'warning', 'xunlei', '迅雷登录态即将过期', '当前迅雷账号的登录态将于 24 小时内失效，请到「推送设置」重新登录。', '2026-10-14 09:20:44')
]
const sessions = [
  { id: 'movie', title: '电影港', kind: 'movie', unread: 0, updatedAt: '2026-10-14 09:30:00' },
  { id: 'system', title: '系统通知', kind: 'system', unread: 2, updatedAt: '2026-10-14 09:20:44',
    last: { role: 'bot', type: 'notify', content: '迅雷远程下载登录态即将过期，请及时更新账号密码。', contentText: '迅雷登录态即将过期', createdAt: '2026-10-14 09:20:44' } }
]
const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'ok', data }) })

async function open(page, hash, handlers) {
  await page.route((url) => new URL(url).pathname.startsWith('/api/'), (route) => {
    const url = route.request().url()
    for (const [frag, body] of Object.entries(handlers)) {
      if (url.includes(frag)) return route.fulfill(json(body))
    }
    return route.fulfill(json([]))
  })
  await page.addInitScript(() => localStorage.setItem('huluwa.token', 'preview-token'))
  await page.goto(base + hash)
  await page.waitForTimeout(1500)
}

const browser = await chromium.launch({ channel: 'msedge' })
for (const [name, width, height, dpr] of [['mobile', 390, 844, 3], ['desktop', 1280, 900, 1]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr,
    userAgent: width <= 900 ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' : undefined })
  const handlers = {
    '/chat/sessions/system/messages': notices,
    '/chat/sessions/movie/messages': [{ id: 5, sessionId: 'movie', role: 'bot', type: 'text', content: '电影港已就绪，输入片名开始搜索。', contentText: null, payload: null, read: true, createdAt: '2026-10-14 09:30:00' }],
    '/chat/sessions': sessions
  }
  await open(page, '#/messages?session=system', handlers)
  console.log(name, '| 系统通知 composer:', await page.locator('.composer').count(),
    '| 卡片:', await page.locator('.notify-card').count(),
    '| 气泡:', await page.locator('.bubble').count())
  const hSys = await page.evaluate(() => Math.round(document.querySelector('.message-stream').getBoundingClientRect().height))
  await page.screenshot({ path: `${dir}/${name}-system-notice.png` })

  await open(page, '#/messages?session=movie', handlers)
  console.log(name, '| 电影港 composer:', await page.locator('.composer').count(),
    '| placeholder:', await page.locator('.composer textarea').getAttribute('placeholder'))
  const hMovie = await page.evaluate(() => Math.round(document.querySelector('.message-stream').getBoundingClientRect().height))
  console.log(name, '| 消息流高度 系统通知/电影港:', hSys, '/', hMovie, '(差 ' + (hMovie - hSys) + 'px)')
  await page.close()
}

// 未选择会话（会话列表为空）对照：输入条保持原样可见
const empty = await browser.newPage({ viewport: { width: 390, height: 844 } })
await open(empty, '#/messages', { '/chat/sessions': [] })
console.log('空会话 | composer:', await empty.locator('.composer').count(),
  '| disabled:', await empty.locator('.composer textarea').isDisabled().catch(() => 'n/a'))
await browser.close()
