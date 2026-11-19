#!/usr/bin/env node
/*
 * 黄果短剧（huangguoai.com）接口自检。
 *
 * 只依赖 Node.js 18+ 自带的 fetch、WebCrypto，无第三方包。
 * 公共业务接口可直接 GET；播放地址必须经过站点的 dx150-edge-v1 加密通道，
 * 握手为 ECDH P-256，派生为 HKDF-SHA256，载荷为 AES-GCM。
 *
 * 用法：
 *   node hg/huangguoai_check.mjs search 任意门
 *   node hg/huangguoai_check.mjs episodes 10586
 *   node hg/huangguoai_check.mjs play 10586 1
 *   node hg/huangguoai_check.mjs all 任意门
 */

const SITE = 'https://huangguoai.com'
const PROTOCOL = 'dx150-edge-v1'
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
  'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36'

const encoder = new TextEncoder()
const decoder = new TextDecoder()

function base64Url(bytes) {
  return Buffer.from(bytes).toString('base64url')
}

function fromBase64Url(value) {
  return new Uint8Array(Buffer.from(value, 'base64url'))
}

function base64(bytes) {
  return Buffer.from(bytes).toString('base64')
}

function fromBase64(value) {
  return new Uint8Array(Buffer.from(value, 'base64'))
}

async function getJson(path, referer = SITE + '/') {
  const response = await fetch(SITE + path, {
    headers: {
      'User-Agent': USER_AGENT,
      'Referer': referer,
      'Accept': 'application/json, text/plain, */*'
    }
  })
  const body = await response.json()
  if (!response.ok) {
    throw new Error(`GET ${path} -> HTTP ${response.status}: ${JSON.stringify(body)}`)
  }
  return body
}

/** 搜索。官方参数名是 q，不是 keyword。 */
export async function search(keyword, page = 1, size = 20) {
  const query = new URLSearchParams({ q: keyword, page: String(page), size: String(size) })
  const result = await getJson(`/api/search?${query}`, `${SITE}/search/`)
  if (result.status !== 1) {
    throw new Error(`搜索失败: ${result.msg || result.status}`)
  }
  return result.data
}

/** 详情接口提供真实的集数与每集时长，播放页 HTML 里的 epPlaySrcs 通常为空。 */
export async function detail(id) {
  const result = await getJson(`/api/videos/${encodeURIComponent(id)}`, `${SITE}/video/${id}/`)
  if (result.status !== 1 || !result.data) {
    throw new Error(`详情失败: ${result.msg || result.status}`)
  }
  return result.data
}

function nonce(seq) {
  const value = new Uint8Array(12)
  new DataView(value.buffer).setUint32(8, seq, false)
  return value
}

function packet(method, path, body) {
  const bytes = encoder.encode(body === undefined ? '' : JSON.stringify(body))
  return {
    method,
    path,
    body: base64(bytes),
    content_type: body === undefined ? '' : 'application/json',
    visitor_id: ''
  }
}

/** 建立 dx150-edge-v1 会话。 */
async function openSession(scope, referer) {
  const ecdh = { name: 'ECDH', namedCurve: 'P-256' }
  const pair = await crypto.subtle.generateKey(ecdh, false, ['deriveBits'])
  const publicKey = await crypto.subtle.exportKey('raw', pair.publicKey)
  const endpoint = `${SITE}/api/${scope}/request`
  const headers = {
    'Content-Type': 'application/json',
    'User-Agent': USER_AGENT,
    'Origin': SITE,
    'Referer': referer
  }

  let response = await fetch(endpoint, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      v: 1,
      type: 'hello',
      client_pub: base64Url(new Uint8Array(publicKey))
    })
  })
  const hello = await response.json()
  if (!response.ok || hello.v !== 1 || hello.type !== 'hello') {
    throw new Error(`加密握手失败: HTTP ${response.status} ${JSON.stringify(hello)}`)
  }

  const serverPublicKey = await crypto.subtle.importKey(
    'raw', fromBase64Url(hello.server_pub), ecdh, false, [])
  const shared = await crypto.subtle.deriveBits(
    { name: 'ECDH', public: serverPublicKey }, pair.privateKey, 256)
  const hkdf = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey'])
  const salt = fromBase64Url(hello.salt)
  const info = [PROTOCOL, scope, hello.site, hello.kid, hello.sid].join('\n')
  const derive = (direction, usage) => crypto.subtle.deriveKey({
    name: 'HKDF',
    hash: 'SHA-256',
    salt,
    info: encoder.encode(`${info}\n${direction}`)
  }, hkdf, { name: 'AES-GCM', length: 256 }, false, [usage])
  const [requestKey, responseKey] = await Promise.all([
    derive('req', 'encrypt'),
    derive('res', 'decrypt')
  ])
  const localTime = Math.floor(Date.now() / 1000)
  return {
    endpoint,
    headers,
    info,
    kid: hello.kid,
    sid: hello.sid,
    offset: hello.server_time - localTime,
    expires: localTime + hello.expires_at - hello.server_time,
    seq: 0,
    requestKey,
    responseKey
  }
}

async function callSession(session, requestPacket) {
  const seq = ++session.seq
  const timestamp = Math.floor(Date.now() / 1000) + session.offset
  const iv = nonce(seq)
  const params = direction => ({
    name: 'AES-GCM',
    iv,
    additionalData: encoder.encode([session.info, direction, seq, timestamp].join('\n'))
  })

  const cipher = await crypto.subtle.encrypt(
    params('req'), session.requestKey,
    encoder.encode(JSON.stringify({ request: requestPacket })))
  const response = await fetch(session.endpoint, {
    method: 'POST',
    headers: session.headers,
    body: JSON.stringify({
      v: 1,
      type: 'call',
      kid: session.kid,
      sid: session.sid,
      seq,
      ts: timestamp,
      ciphertext: base64Url(new Uint8Array(cipher))
    })
  })
  const envelope = await response.json()
  if (!response.ok) {
    throw new Error(`加密调用失败: HTTP ${response.status} ${JSON.stringify(envelope)}`)
  }
  const plain = await crypto.subtle.decrypt(
    params('res'), session.responseKey, fromBase64Url(envelope.ciphertext))
  const inner = JSON.parse(decoder.decode(plain))
  return {
    status: inner.status,
    body: JSON.parse(decoder.decode(fromBase64(inner.body)))
  }
}

/** 获取单集播放地址。返回 data.streams.main 等原始字段。 */
export async function playback(id, ep) {
  const referer = `${SITE}/video/${id}/${ep === 1 ? '' : `ep-${ep}/`}`
  const session = await openSession('playback', referer)
  const result = await callSession(session, packet(
    'POST', `/api/videos/${id}/episodes/${ep}/playback`, {}))
  if (result.status !== 200 || result.body.status !== 1) {
    throw new Error(`播放接口失败: ${JSON.stringify(result.body)}`)
  }
  return result.body.data
}

/** 读取 m3u8，返回时长、分片数和密钥方式，不下载分片。 */
export async function probeStream(url) {
  const response = await fetch(url, {
    headers: {
      'User-Agent': USER_AGENT,
      'Referer': SITE + '/'
    }
  })
  const text = await response.text()
  let duration = 0
  for (const match of text.matchAll(/#EXTINF:([\d.]+)/g)) {
    duration += Number(match[1])
  }
  return {
    status: response.status,
    duration: Math.round(duration * 10) / 10,
    segments: (text.match(/#EXTINF/g) || []).length,
    keyMethod: (text.match(/EXT-X-KEY:METHOD=([^,\s]+)/i) || [])[1] || ''
  }
}

async function main() {
  const [command, ...args] = process.argv.slice(2)
  if (command === 'search') {
    const keyword = args[0]
    if (!keyword) throw new Error('用法: search <关键词>')
    console.log(JSON.stringify(await search(keyword, Number(args[1] || 1), Number(args[2] || 20)), null, 2))
  } else if (command === 'episodes') {
    const id = args[0]
    if (!id) throw new Error('用法: episodes <剧集ID>')
    const data = await detail(id)
    console.log(JSON.stringify({
      id: data.id,
      title: data.title,
      episode_count: data.episode_count,
      episodes: data.episodes,
      tracking_episodes: data.tracking_episodes
    }, null, 2))
  } else if (command === 'play') {
    const [id, ep = '1'] = args
    if (!id) throw new Error('用法: play <剧集ID> <集数>')
    const data = await playback(id, ep)
    const probe = data.streams?.main ? await probeStream(data.streams.main) : null
    console.log(JSON.stringify({ ...data, probe }, null, 2))
  } else if (command === 'all') {
    const keyword = args[0]
    if (!keyword) throw new Error('用法: all <关键词>')
    const found = await search(keyword, 1, 10)
    const output = { keyword, items: [] }
    for (const item of found.items || []) {
      const info = await detail(item.id)
      const episodes = []
      for (let ep = 1; ep <= Math.min(Number(info.episode_count || 1), 20); ep++) {
        const data = await playback(item.id, ep)
        episodes.push({
          ep,
          url: data.streams?.main || '',
          hevc: data.streams?.hevc || ''
        })
      }
      output.items.push({ id: info.id, title: info.title, episodes })
    }
    console.log(JSON.stringify(output, null, 2))
  } else {
    console.log('用法: search <关键词> | episodes <剧集ID> | play <剧集ID> <集数> | all <关键词>')
    process.exitCode = 2
  }
}

if (process.argv[1]?.endsWith('huangguoai_check.mjs')) {
  main().catch(error => {
    console.error(error.message)
    process.exitCode = 1
  })
}
