import pw from 'file:///C:/Users/Jing/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js'
const { chromium } = pw
const dir = 'C:/Users/Jing/Desktop/huluwa/output'
const url = 'file:///C:/Users/Jing/Desktop/huluwa/output/movie-card-designs.html'
const browser = await chromium.launch({ channel: 'msedge' })
const errors = []
const page = await browser.newPage({ viewport: { width: 1360, height: 1100 } })
page.on('pageerror', e => errors.push('pageerror: ' + e))
page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()) })
await page.goto(url)
await page.waitForTimeout(700)

const names = await page.locator('.v-name').allInnerTexts()
console.log('方案:', JSON.stringify(names))
console.log('卡片数:', await page.locator('.bubble').count(), '| 订阅按钮数:', await page.locator('.sb').count())

// 检查每个按钮是否越界（按钮必须落在它所属的 .bubble 内）
const bounds = await page.evaluate(() => {
  const rows = []
  document.querySelectorAll('.bubble').forEach((bubble, i) => {
    const btn = bubble.querySelector('.sb')
    if (!btn) { return }
    const b = bubble.getBoundingClientRect(), t = btn.getBoundingClientRect()
    rows.push({ i, name: btn.className.replace('sb sb-', ''), inside: t.left >= b.left - 1 && t.right <= b.right + 1 && t.top >= b.top - 1 && t.bottom <= b.bottom + 1,
      btn: [Math.round(t.x), Math.round(t.y), Math.round(t.width), Math.round(t.height)] })
  })
  return rows
})
console.log('边界:', JSON.stringify(bounds))
console.log('横向溢出:', await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1))

// 逐一点击，验证两态切换
const bubbles = await page.locator('.bubble').count()
for (let i = 0; i < bubbles; i++) {
  const btn = page.locator('.bubble').nth(i).locator('.sb')
  const before = await btn.getAttribute('data-sub')
  await btn.click()
  await page.waitForTimeout(120)
  const after = await btn.getAttribute('data-sub')
  const text = (await btn.innerText()).replace(/\s+/g, '')
  console.log(`#${i} ${before} -> ${after} | 文案「${text}」`)
}
await page.screenshot({ path: dir + '/designs-desktop.png', fullPage: true })

// 批量状态 + 手机宽度
await page.locator('#seg-state button[data-state="on"]').click()
await page.locator('#seg-width button[data-width="mobile"]').click()
await page.waitForTimeout(300)
await page.setViewportSize({ width: 760, height: 1100 })
await page.waitForTimeout(300)
console.log('手机预览横向溢出:', await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1))
await page.screenshot({ path: dir + '/designs-mobile.png', fullPage: true })
console.log('errors:', errors.length ? errors : 'none')
await browser.close()
