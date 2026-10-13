import pw from 'file:///C:/Users/wiggins.wang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/wiggins.wang/Desktop/huluwa/output/bubble-preview'
const browser = await chromium.launch({ channel: 'msedge' })
for (const [name, width, height, dpr] of [['mobile', 381, 900, 1.5], ['desktop', 900, 800, 1]]) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: dpr })
  await page.goto('file:///' + dir + '/index.html')
  await page.waitForTimeout(200)
  const m = await page.evaluate(() => window.__measure())
  console.log(name, JSON.stringify(m))
  await page.screenshot({ path: dir + '/shot-' + name + '.png' })
  await page.close()
}
await browser.close()



