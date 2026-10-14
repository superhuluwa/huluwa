import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const browser = await chromium.launch({ channel: 'msedge' })
const errors = []
for (const [w, h, tag] of [[1024, 900, 'desktop'], [390, 900, 'mobile']]) {
  const page = await browser.newPage({ viewport: { width: w, height: h } })
  page.on('pageerror', e => errors.push(tag + ': ' + e))
  await page.goto('file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-subscribe-variants.html')
  await page.waitForTimeout(900)
  const frame = page.frames().find(f => f !== page.mainFrame())
  const overflow = await frame.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
  const cards = await frame.locator('[data-variant]').count()
  const visible = await frame.locator('[data-variant]:visible').getAttribute('data-variant')
  console.log(tag, '| overflow:', overflow, '| variants:', cards, '| visible:', visible)
  await page.screenshot({ path: `C:/Users/Jing/Desktop/huluwa/output/variant-${tag}.png` })
  await page.close()
}
console.log('errors:', errors.length ? errors : 'none')
await browser.close()
