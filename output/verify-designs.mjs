import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output'
const url = 'file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-designs.html'
const browser = await chromium.launch({ channel: 'msedge' })
const errors = []
const page = await browser.newPage({ viewport: { width: 1360, height: 1200 }, deviceScaleFactor: 2 })
page.on('pageerror', e => errors.push(String(e)))
await page.goto(url)
await page.waitForTimeout(600)

// 逐个方案：未订阅态截图 + 点击后已订阅态截图 + 越界检查
for (let i = 0; i < 6; i++) {
  const v = page.locator('.variant').nth(i)
  const name = await v.locator('.v-name').innerText()
  const btn = v.locator('.sb')
  const offText = (await btn.innerText()).replace(/\s+/g, '')
  const geom = await v.evaluate((el) => {
    const btn = el.querySelector('.sb'), bubble = el.querySelector('.bubble')
    const b = bubble.getBoundingClientRect(), t = btn.getBoundingClientRect()
    return { inside: t.left >= b.left - 1 && t.right <= b.right + 1 && t.top >= b.top - 1 && t.bottom <= b.bottom + 1,
             size: [Math.round(t.width), Math.round(t.height)] }
  })
  await v.screenshot({ path: `${dir}/v${i + 1}-off.png` })
  await btn.click()
  await page.waitForTimeout(150)
  const onText = (await btn.innerText()).replace(/\s+/g, '')
  await v.screenshot({ path: `${dir}/v${i + 1}-on.png` })
  await btn.click()
  await page.waitForTimeout(120)
  console.log(`#${i + 1} ${name} | 未订阅「${offText}」-> 已订阅「${onText}」| 在气泡内 ${geom.inside} | 尺寸 ${geom.size.join('x')}`)
}

// 图标按钮悬停展开态
const icon = page.locator('.variant').nth(3).locator('.sb-icon')
await icon.hover()
await page.waitForTimeout(450)
await page.locator('.variant').nth(3).screenshot({ path: dir + '/v4-hover.png' })

// 手机宽度 + 手机卡片宽度预设
await page.locator('#seg-state button[data-state="off"]').click()
await page.locator('#seg-width button[data-width="mobile"]').click()
await page.setViewportSize({ width: 780, height: 1200 })
await page.waitForTimeout(350)
const mobileOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
await page.screenshot({ path: dir + '/designs-mobile.png', fullPage: true })
console.log('窄屏横向溢出:', mobileOverflow, '| errors:', errors.length ? errors : 'none')
await browser.close()
