import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output'
const browser = await chromium.launch({ channel: 'msedge' })
const page = await browser.newPage({ viewport: { width: 390, height: 900 } })
await page.goto('file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-subscribe-variants.html')
await page.waitForTimeout(900)
const frame = page.frames().find(f => f !== page.mainFrame())
const next = frame.locator('.viz-carousel-controls button').last()
for (let i = 0; i < 4; i++) {
  const name = await frame.locator('[data-variant]:visible').getAttribute('data-variant')
  const info = await frame.evaluate(() => {
    const v = document.querySelector('[data-variant]:not([hidden])')
    const btn = v.querySelector('.hw-sub')
    const bubble = v.querySelector('.hw-bubble')
    const r = (el) => { const b = el.getBoundingClientRect(); return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right), bottom: Math.round(b.bottom) } }
    const bb = r(btn), bu = r(bubble)
    return { btn: bb, bubble: bu, inside: bb.x >= bu.x - 1 && bb.right <= bu.right + 1 && bb.y >= bu.y - 1 && bb.bottom <= bu.bottom + 1,
             docOverflow: document.documentElement.scrollWidth > window.innerWidth + 1 }
  })
  console.log('mobile', name, JSON.stringify(info))
  await page.screenshot({ path: `${dir}/m-${i + 1}.png` })
  if (i < 3) await next.click()
  await page.waitForTimeout(350)
}
await browser.close()
