#!/usr/bin/env node
/**
 * 获取 4hu.tv 顶部“小说”区域的分类标题、ID 与跳转路径。
 *
 * 数据来源：站点前端把菜单数组 AES-CBC 加密后内嵌在 Nuxt 的
 * Eu3kWdKfo99mwJz-.js 中。脚本自动从首页资源清单定位该模块，抽取 aa 常量，
 * 解密后筛选 listtempid=11（小说）的分类。
 *
 * 用法：
 *   node scripts/fetch-4hu-novel-categories.mjs
 *   node scripts/fetch-4hu-novel-categories.mjs --json
 *   node scripts/fetch-4hu-novel-categories.mjs --url https://www.4hu.tv/home/ --json
 *   node scripts/fetch-4hu-novel-categories.mjs --input menu.js --json
 *
 * 仅依赖 Node.js 内置模块，建议 Node.js 18+。
 */
import crypto from 'node:crypto';
import fs from 'node:fs/promises';

const DEFAULTS = {
  pageUrl: 'https://4hu.tv/home/',
  key: '22946bc50fd63164b79df55070a85a92',
  iv: 'kaixin1234567890',
  novelType: 11,
  timeoutMs: 30_000,
};

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
  'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

function usage() {
  console.log(`用法：node scripts/fetch-4hu-novel-categories.mjs [选项]

选项：
  --url <url>        首页地址，默认 ${DEFAULTS.pageUrl}
  --input <file>     从已下载的 JS 模块读取菜单，不访问网络
  --json             输出 JSON 数组
  --pretty           配合 --json，格式化输出（默认）
  --compact          配合 --json，单行输出
  -h, --help         显示帮助

输出字段：
  title              分类标题，例如“都市小说”
  id                 分类 ID，例如 16
  path               前端路由路径，例如 /category/16/novel
  classpath          站点分类路径，例如 pic/dushi
  listtempid         列表模板 ID，小说固定为 11
`);
}

function parseArgs(argv) {
  const opts = { ...DEFAULTS, json: false, compact: false, input: null };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--url') {
      opts.pageUrl = requiredValue(argv, ++i, arg);
    } else if (arg.startsWith('--url=')) {
      opts.pageUrl = arg.slice('--url='.length);
    } else if (arg === '--input') {
      opts.input = requiredValue(argv, ++i, arg);
    } else if (arg.startsWith('--input=')) {
      opts.input = arg.slice('--input='.length);
    } else if (arg === '--json') {
      opts.json = true;
    } else if (arg === '--compact') {
      opts.compact = true;
    } else if (arg === '--pretty') {
      opts.compact = false;
    } else if (arg === '-h' || arg === '--help') {
      usage();
      process.exit(0);
    } else {
      throw new Error(`未知参数：${arg}`);
    }
  }
  return opts;
}

function requiredValue(argv, index, flag) {
  const value = argv[index];
  if (!value || value.startsWith('--')) throw new Error(`${flag} 缺少参数值`);
  return value;
}

function normalizeUrl(value, base) {
  try {
    return new URL(value, base).href;
  } catch {
    throw new Error(`无效 URL：${value}`);
  }
}

async function fetchText(url, timeoutMs) {
  const response = await fetch(url, {
    redirect: 'follow',
    signal: AbortSignal.timeout(timeoutMs),
    headers: {
      'user-agent': USER_AGENT,
      accept: '*/*',
      'accept-language': 'zh-CN,zh;q=0.9,en;q=0.6',
    },
  });
  if (!response.ok) {
    throw new Error(`请求失败 ${response.status} ${response.statusText}：${url}`);
  }
  return response.text();
}

async function findMenuModuleScript(pageUrl, timeoutMs) {
  const html = await fetchText(pageUrl, timeoutMs);
  const scripts = [...html.matchAll(/<script\b[^>]*\bsrc=["']([^"']+\.js)["'][^>]*>/gi)]
    .map((match) => match[1]);

  if (scripts.length === 0) {
    throw new Error('首页未找到 JS 模块；站点可能启用了 Cloudflare 验证或页面结构已变化');
  }

  // 首页会预加载多个模块，逐一查找包含菜单密文常量 aa 的模块。
  for (const source of scripts) {
    const scriptUrl = normalizeUrl(source, pageUrl);
    const code = await fetchText(scriptUrl, timeoutMs);
    const cipher = extractMenuCipher(code);
    if (cipher) return { scriptUrl, cipher, html };
  }

  throw new Error('未在首页 JS 模块中找到菜单密文；站点前端结构可能已更新');
}

function extractMenuCipher(code) {
  // 前端源码形式：...,la={data:aa}，其中 aa="<AES-CBC 密文>"。
  const match = code.match(/(?:^|[,;{])\s*aa\s*=\s*["']([A-Za-z0-9_+\-/=]+)["']/);
  return match ? match[1] : null;
}

function decrypt(cipherText, keyText, ivText) {
  let base64 = cipherText.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4 !== 0) base64 += '=';

  const key = Buffer.from(keyText, 'utf8');
  const iv = Buffer.from(ivText, 'utf8');
  if (key.length !== 32) throw new Error(`AES-256 密钥长度错误：${key.length} 字节`);
  if (iv.length !== 16) throw new Error(`AES-CBC IV 长度错误：${iv.length} 字节`);

  const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);
  const plain = Buffer.concat([
    decipher.update(Buffer.from(base64, 'base64')),
    decipher.final(),
  ]).toString('utf8');

  return JSON.parse(plain);
}

function collectNovelCategories(menu, novelType) {
  const categories = [];
  const seen = new Set();

  for (const group of Array.isArray(menu) ? menu : []) {
    for (const item of Array.isArray(group?.items) ? group.items : []) {
      if (Number(item?.listtempid) !== novelType) continue;
      const id = Number(item.classid);
      if (!Number.isInteger(id) || seen.has(id)) continue;
      seen.add(id);
      categories.push({
        title: String(item.classname ?? ''),
        id,
        path: `/category/${id}/novel`,
        classpath: String(item.classpath ?? ''),
        listtempid: novelType,
      });
    }
  }

  return categories;
}

function printHuman(categories) {
  if (categories.length === 0) {
    console.log('未找到小说分类');
    return;
  }
  console.log('标题\tID\t路径');
  for (const item of categories) {
    console.log(`${item.title}\t${item.id}\t${item.path}`);
  }
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));

  let cipher;
  let source;
  if (opts.input) {
    source = opts.input;
    cipher = extractMenuCipher(await fs.readFile(opts.input, 'utf8'));
    if (!cipher) throw new Error(`文件中未找到菜单密文：${opts.input}`);
  } else {
    const found = await findMenuModuleScript(opts.pageUrl, opts.timeoutMs);
    source = found.scriptUrl;
    cipher = found.cipher;
  }

  const menu = decrypt(cipher, opts.key, opts.iv);
  const categories = collectNovelCategories(menu, opts.novelType);

  if (opts.json) {
    console.log(JSON.stringify(categories, null, opts.compact ? 0 : 2));
  } else {
    console.log(`来源：${source}`);
    printHuman(categories);
  }
}

main().catch((error) => {
  console.error(`错误：${error.message}`);
  process.exitCode = 1;
});
