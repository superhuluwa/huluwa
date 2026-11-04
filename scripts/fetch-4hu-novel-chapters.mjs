#!/usr/bin/env node
/**
 * 抓取 4hu.tv 小说分类下的章节列表。
 *
 * 站点数据链路：
 *   分类章节 JSON：/json/category/<分类ID>-<页码>.json
 *   章节详情 JSON：/json/view/<章节ID>.json
 *   两者都是 {"status":1,"data":"<AES-CBC 密文>"}，解密后为 JSON。
 *
 * 分类页默认按发布时间倒序，所以第 1 页是最新章节。使用 --all 可遍历全部页；
 * 使用 --with-content 可继续按章节 ID 拉取正文。章节列表本身不会逐条请求详情。
 *
 * 用法：
 *   node scripts/fetch-4hu-novel-chapters.mjs --category 16
 *   node scripts/fetch-4hu-novel-chapters.mjs --category 16 --page 1 --json
 *   node scripts/fetch-4hu-novel-chapters.mjs --category 16 --all --pages 3 --json
 *   node scripts/fetch-4hu-novel-chapters.mjs --category 16 --id 102944 --with-content --json
 *   node scripts/fetch-4hu-novel-chapters.mjs --input chapter-list.json --json
 *
 * 默认通过 r.jina.ai 读取 JSON（绕过目标站的 Cloudflare 浏览器验证）；
 * --direct 则直接请求目标站。仅依赖 Node.js 内置模块，建议 Node.js 18+。
 */
import crypto from 'node:crypto';
import fs from 'node:fs/promises';

const DEFAULTS = {
  baseUrl: 'https://4hu.tv',
  proxyBaseUrl: 'https://r.jina.ai',
  key: '22946bc50fd63164b79df55070a85a92',
  iv: 'kaixin1234567890',
  category: null,
  page: 1,
  pages: null,
  all: false,
  chapterId: null,
  withContent: false,
  concurrency: 4,
  timeoutMs: 60_000,
  retries: 2,
  delayMs: 120,
  direct: false,
  json: false,
  compact: false,
  input: null,
};

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
  'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

function usage() {
  console.log(`用法：node scripts/fetch-4hu-novel-chapters.mjs [选项]

选项：
  --category <id>     小说分类 ID，例如 16（都市小说）
  --page <n>          起始页，默认 1
  --pages <n>         从起始页连续抓取 n 页
  --all               抓取全部分页（可用 --pages 限制总页数）
  --id <chapter-id>   只抓取指定章节详情（仍需 --category 校验分类）
  --with-content      抓取每条章节的正文（请求量较大）
  --direct            直接访问 4hu.tv，默认经 r.jina.ai 读取
  --base-url <url>    目标站地址，默认 ${DEFAULTS.baseUrl}
  --proxy-url <url>   JSON 代理地址，默认 ${DEFAULTS.proxyBaseUrl}
  --input <file>      从已保存的 {"status":1,"data":"..."} JSON 读取
  --concurrency <n>   正文并发数，默认 ${DEFAULTS.concurrency}
  --timeout <ms>      单请求超时，默认 ${DEFAULTS.timeoutMs}
  --retries <n>       失败重试次数，默认 ${DEFAULTS.retries}
  --delay <ms>        请求间隔，默认 ${DEFAULTS.delayMs}
  --json              JSON 输出
  --pretty            格式化 JSON（默认）
  --compact           单行 JSON
  -h, --help          显示帮助

示例：
  node scripts/fetch-4hu-novel-chapters.mjs --category 16 --page 1
  node scripts/fetch-4hu-novel-chapters.mjs --category 16 --all --json > chapters.json
  node scripts/fetch-4hu-novel-chapters.mjs --category 16 --id 102944 --with-content --json
`);
}

function parseArgs(argv) {
  const opts = { ...DEFAULTS };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const take = () => requiredValue(argv, ++i, arg);
    const numeric = (value, name) => {
      const n = Number(value);
      if (!Number.isInteger(n) || n < 0) throw new Error(`${name} 必须是非负整数：${value}`);
      return n;
    };
    if (arg === '--category') opts.category = numeric(take(), '--category');
    else if (arg.startsWith('--category=')) opts.category = numeric(arg.slice(11), '--category');
    else if (arg === '--page') opts.page = numeric(take(), '--page');
    else if (arg.startsWith('--page=')) opts.page = numeric(arg.slice(7), '--page');
    else if (arg === '--pages') opts.pages = numeric(take(), '--pages');
    else if (arg.startsWith('--pages=')) opts.pages = numeric(arg.slice(8), '--pages');
    else if (arg === '--all') opts.all = true;
    else if (arg === '--id') opts.chapterId = numeric(take(), '--id');
    else if (arg.startsWith('--id=')) opts.chapterId = numeric(arg.slice(5), '--id');
    else if (arg === '--with-content') opts.withContent = true;
    else if (arg === '--direct') opts.direct = true;
    else if (arg === '--base-url') opts.baseUrl = take();
    else if (arg.startsWith('--base-url=')) opts.baseUrl = arg.slice(11);
    else if (arg === '--proxy-url') opts.proxyBaseUrl = take();
    else if (arg.startsWith('--proxy-url=')) opts.proxyBaseUrl = arg.slice(12);
    else if (arg === '--input') opts.input = take();
    else if (arg.startsWith('--input=')) opts.input = arg.slice(8);
    else if (arg === '--concurrency') opts.concurrency = Math.max(1, numeric(take(), '--concurrency'));
    else if (arg.startsWith('--concurrency=')) opts.concurrency = Math.max(1, numeric(arg.slice(14), '--concurrency'));
    else if (arg === '--timeout') opts.timeoutMs = Math.max(1, numeric(take(), '--timeout'));
    else if (arg.startsWith('--timeout=')) opts.timeoutMs = Math.max(1, numeric(arg.slice(10), '--timeout'));
    else if (arg === '--retries') opts.retries = numeric(take(), '--retries');
    else if (arg.startsWith('--retries=')) opts.retries = numeric(arg.slice(10), '--retries');
    else if (arg === '--delay') opts.delayMs = numeric(take(), '--delay');
    else if (arg.startsWith('--delay=')) opts.delayMs = numeric(arg.slice(8), '--delay');
    else if (arg === '--json') opts.json = true;
    else if (arg === '--pretty') opts.compact = false;
    else if (arg === '--compact') opts.compact = true;
    else if (arg === '-h' || arg === '--help') {
      usage();
      process.exit(0);
    } else {
      throw new Error(`未知参数：${arg}`);
    }
  }

  opts.baseUrl = opts.baseUrl.replace(/\/+$/, '');
  opts.proxyBaseUrl = opts.proxyBaseUrl.replace(/\/+$/, '');
  if (opts.chapterId !== null && opts.category === null) {
    throw new Error('使用 --id 时必须同时提供 --category 以确定分类上下文');
  }
  if (!opts.input && opts.category === null) {
    throw new Error('缺少 --category；都市小说使用 --category 16');
  }
  return opts;
}

function requiredValue(argv, index, flag) {
  const value = argv[index];
  if (!value || value.startsWith('--')) throw new Error(`${flag} 缺少参数值`);
  return value;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildJsonUrl(target, opts) {
  if (opts.direct) return `${opts.baseUrl}${target}`;
  return `${opts.proxyBaseUrl}/${opts.baseUrl}${target}`;
}

async function fetchText(url, opts) {
  let lastError;
  for (let attempt = 0; attempt <= opts.retries; attempt += 1) {
    try {
      if (attempt > 0) await sleep(opts.delayMs * attempt * 2);
      const response = await fetch(url, {
        redirect: 'follow',
        signal: AbortSignal.timeout(opts.timeoutMs),
        headers: {
          'user-agent': USER_AGENT,
          accept: 'application/json,text/plain,*/*',
          'accept-language': 'zh-CN,zh;q=0.9,en;q=0.6',
        },
      });
      const text = await response.text();
      if (!response.ok) {
        throw new Error(`HTTP ${response.status} ${response.statusText}`);
      }
      return text;
    } catch (error) {
      lastError = error;
    }
  }
  throw new Error(`请求失败：${url}（${lastError?.message || '未知错误'}）`);
}

function extractEncryptedPayload(text) {
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed.data === 'string') return parsed.data;
  } catch {
    // r.jina.ai 返回 Markdown 包装，JSON 在正文中。
  }

  // 兼容直接返回的 JSON、Markdown 代码块和 r.jina.ai 的正文。
  const match = text.match(/\{"status"\s*:\s*1\s*,\s*"data"\s*:\s*"([A-Za-z0-9_+\-/=]+)"\s*\}/);
  if (match) return match[1];
  throw new Error('响应中没有找到 status/data 加密载荷；可能被 Cloudflare 拦截或接口已变更');
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

async function readEncryptedJson(url, opts) {
  const text = await fetchText(url, opts);
  return decrypt(extractEncryptedPayload(text), opts.key, opts.iv);
}

function formatDate(seconds) {
  const value = Number(seconds);
  if (!Number.isFinite(value)) return '';
  return new Date(value * 1000).toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
}

function normalizeChapter(item) {
  return {
    id: Number(item.id),
    categoryId: Number(item.classid),
    title: String(item.title ?? ''),
    date: formatDate(item.newstime),
    timestamp: Number(item.newstime) || null,
    url: `/view/${item.id}`,
  };
}

function normalizeList(payload) {
  return {
    categoryName: String(payload.category_name ?? ''),
    categoryId: Number(payload.category_tempid ?? 0),
    page: Number(payload.current_page ?? 0),
    perPage: Number(payload.per_page ?? 0),
    lastPage: Number(payload.last_page ?? 0),
    total: Number(payload.total ?? 0),
    chapters: Array.isArray(payload.data) ? payload.data.map(normalizeChapter) : [],
  };
}

async function fetchCategoryPage(category, page, opts) {
  const url = buildJsonUrl(`/json/category/${category}-${page}.json`, opts);
  return normalizeList(await readEncryptedJson(url, opts));
}

async function fetchChapterDetail(id, opts) {
  const url = buildJsonUrl(`/json/view/${id}.json`, opts);
  const payload = await readEncryptedJson(url, opts);
  return {
    id: Number(payload.id),
    categoryId: Number(payload.classid),
    categoryName: String(payload.category_name ?? ''),
    categoryType: String(payload.category_type ?? ''),
    templateId: Number(payload.category_tempid ?? 0),
    title: String(payload.title ?? ''),
    date: formatDate(payload.newstime),
    timestamp: Number(payload.newstime) || null,
    url: `/view/${payload.id}`,
    contentHtml: String(payload.content ?? ''),
    contentText: htmlToText(String(payload.content ?? '')),
    breadcrumb: Array.isArray(payload.breadcrumb) ? payload.breadcrumb : [],
    prev: payload.prev_and_next?.prev ?? null,
    next: payload.prev_and_next?.next ?? null,
  };
}

function htmlToText(html) {
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|h[1-6]|li)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

async function mapLimit(items, limit, work) {
  const results = new Array(items.length);
  let cursor = 0;
  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= items.length) return;
      results[index] = await work(items[index], index);
    }
  }
  const workers = Array.from({ length: Math.min(limit, items.length) }, () => worker());
  await Promise.all(workers);
  return results;
}

async function loadListFromInput(file) {
  const text = await fs.readFile(file, 'utf8');
  const payload = decrypt(extractEncryptedPayload(text), DEFAULTS.key, DEFAULTS.iv);
  return normalizeList(payload);
}

async function collectPages(opts) {
  const first = await fetchCategoryPage(opts.category, opts.page, opts);
  const results = [first];
  let endPage = opts.page;

  if (opts.chapterId !== null) {
    return { pages: results, endPage };
  }

  if (opts.all) {
    const remaining = Math.max(0, first.lastPage - opts.page);
    const requested = opts.pages === null ? remaining : Math.min(remaining, Math.max(0, opts.pages - 1));
    endPage = opts.page + requested;
  } else if (opts.pages !== null) {
    endPage = Math.min(first.lastPage, opts.page + Math.max(0, opts.pages - 1));
  }

  for (let page = opts.page + 1; page <= endPage; page += 1) {
    if (opts.delayMs) await sleep(opts.delayMs);
    results.push(await fetchCategoryPage(opts.category, page, opts));
  }
  return { pages: results, endPage };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));

  let listMeta;
  let chapters;

  if (opts.input) {
    const list = await loadListFromInput(opts.input);
    listMeta = { ...list, chapters: undefined };
    chapters = list.chapters;
  } else if (opts.chapterId !== null) {
    const detail = await fetchChapterDetail(opts.chapterId, opts);
    if (detail.categoryId !== opts.category) {
      throw new Error(`章节 ${opts.chapterId} 属于分类 ${detail.categoryId}，与 --category ${opts.category} 不一致`);
    }
    chapters = [{
      id: detail.id,
      categoryId: detail.categoryId,
      title: detail.title,
      date: detail.date,
      timestamp: detail.timestamp,
      url: detail.url,
    }];
    listMeta = { categoryName: detail.categoryName, categoryId: opts.category, page: null, perPage: null, lastPage: null, total: 1, endPage: null };
  } else {
    const collected = await collectPages(opts);
    const first = collected.pages[0];
    listMeta = {
      categoryName: first.categoryName,
      categoryId: opts.category,
      page: opts.page,
      endPage: collected.endPage,
      perPage: first.perPage,
      lastPage: first.lastPage,
      total: first.total,
      fetchedPages: collected.pages.length,
    };
    chapters = collected.pages.flatMap((page) => page.chapters);
  }

  if (opts.withContent) {
    chapters = await mapLimit(chapters, opts.concurrency, async (chapter) => {
      if (opts.delayMs) await sleep(opts.delayMs);
      const detail = await fetchChapterDetail(chapter.id, opts);
      if (detail.categoryId !== chapter.categoryId) {
        throw new Error(`章节 ${chapter.id} 分类不一致：${detail.categoryId} != ${chapter.categoryId}`);
      }
      return { ...chapter, ...detail };
    });
  }

  const result = {
    source: opts.input ? opts.input : (opts.direct ? opts.baseUrl : opts.proxyBaseUrl),
    category: listMeta,
    count: chapters.length,
    chapters,
  };

  if (opts.json) {
    console.log(JSON.stringify(result, null, opts.compact ? 0 : 2));
    return;
  }

  console.log(`分类：${listMeta.categoryName || opts.category || ''}（ID ${listMeta.categoryId || opts.category}）`);
  if (listMeta.total) {
    console.log(`总数：${listMeta.total} 章节；每页 ${listMeta.perPage}；共 ${listMeta.lastPage} 页`);
  }
  console.log(`本次：${result.count} 章节`);
  console.log('ID\t日期\t标题\t链接');
  for (const chapter of chapters) {
    console.log(`${chapter.id}\t${chapter.date.slice(0, 10)}\t${chapter.title}\t${opts.baseUrl}${chapter.url}`);
    if (opts.withContent) {
      console.log(chapter.contentText);
      console.log('');
    }
  }
}

main().catch((error) => {
  console.error(`错误：${error.message}`);
  process.exitCode = 1;
});
