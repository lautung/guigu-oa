/**
 * 附件独立压测场景（#112 §3/§4）：10 VU × 3min，上传 1 MB / 下载 1 MB 各约 50%。
 *
 * 上传链 = POST /admin/attachments/uploads（申请凭证）→ 预签名 PUT（MinIO）→
 * POST /{id}/complete（登记）。真实流量打在 MinIO，只有签名接口打后端。
 * 下载链 = POST /processes/{pid}/{aid}/download-url → 预签名 GET（MinIO）。
 *
 * 阈值口径：签名接口（uploads / download-url）p95 ≤ 500ms；
 * 整链自定义 Trend：upload_chain p95 ≤ 2s、download_chain p95 ≤ 1s。
 * 预签名 URL 的 host 是 S3_PUBLIC_ENDPOINT（localhost:9000），容器内改写到
 * host.docker.internal:9000。
 *
 *   docker run --rm -i -w /results -v "$PWD/perf/results:/results" grafana/k6:latest run \
 *     -e PERF_BASE=http://host.docker.internal:8800 -e SUMMARY_NAME=attachment-1 \
 *     - < /dev/null
 */
import http from 'k6/http'
import { check } from 'k6'
import { randomBytes } from 'k6/crypto'
import { Counter, Rate, Trend } from 'k6/metrics'

// 预签名 URL 的 Host 参与签名，不能用改写 host 的方式换成容器可达地址；
// 压测期间由 compose.override.yaml 把 S3_PUBLIC_ENDPOINT 指到 host.docker.internal:9000。
function uuid() {
  const b = new Uint8Array(randomBytes(16))
  b[6] = (b[6] & 0x0f) | 0x40
  b[8] = (b[8] & 0x3f) | 0x80
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}

const BASE = __ENV.PERF_BASE || 'http://host.docker.internal:8800'
const PASSWORD = '111111'
const PAIRS_WANTED = Number(__ENV.PAIRS_WANTED || 110)
const ONE_MB = 1024 * 1024

const businessError = new Counter('business_error_count')
const businessErrorRate = new Rate('business_error_rate')
const uploadChain = new Trend('upload_chain', true)
const downloadChain = new Trend('download_chain', true)

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  executor: 'constant-vus',
  vus: 10,
  duration: '3m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    business_error_rate: ['rate<0.01'],
    'http_req_duration{action:uploads}': ['p(95)<=500'],
    'http_req_duration{action:downloadUrl}': ['p(95)<=500'],
    upload_chain: ['p(95)<=2000'],
    download_chain: ['p(95)<=1000'],
  },
}

export function handleSummary(data) {
  const name = __ENV.SUMMARY_NAME || 'k6-attachment-summary'
  return { [`${name}.json`]: JSON.stringify(data, null, 2) }
}

function parseEnvelope(res) {
  try {
    return res.json()
  } catch {
    return { code: -1, message: `非 JSON 响应 HTTP ${res.status}`, data: null }
  }
}

function api(token, method, path, payload, action) {
  const res = http.request(
    method.toUpperCase(),
    `${BASE}${path}`,
    payload === undefined ? undefined : JSON.stringify(payload),
    {
      headers: {
        token,
        'Content-Type': 'application/json',
        'X-Trace-Id': `k6-att-${globalThis.__VU ?? 0}-${globalThis.__ITER ?? 0}`,
      },
      tags: { name: path.split('?')[0], ...(action ? { action } : {}) },
    },
  )
  return { res, envelope: parseEnvelope(res) }
}

export function setup() {
  const login = http.post(
    `${BASE}/admin/system/index/login`,
    JSON.stringify({ username: 'alice', password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  )
  const token = parseEnvelope(login).data.token
  if (!token) throw new Error('setup 登录失败')

  // 从 alice 已发起列表找已结束实例，收集 110 个 (processId, attachmentId)。
  // 造数把附件挂在最老的已结束实例上；列表自 #179 起显式最新在前，旧的
  // 「第 1 页 = 最老实例」隐式顺序假设失效——这里从最后一页向前翻，不依赖排序方向（#182）。
  // 下面这套尾页起翻逻辑与 perf/seed.mjs Phase C 是双份实现（#178 评审）：曾想抽到
  // perf/lib/ 共享模块，但 round.sh 以 stdin 喂 k6 脚本且容器只挂 perf/results，
  // 共享模块文件在 k6 容器内不可达——两处必须同步改，改掉 round.sh 的挂载与传参
  // 协议后才具备抽共享条件。
  const PAGE_SIZE = 50
  const first = api(token, 'get', `/admin/process/findStarted/1/${PAGE_SIZE}`)
  if (first.envelope.code !== 200) throw new Error(`findStarted 第 1 页失败 code=${first.envelope.code}`)
  const lastPage = Math.ceil((first.envelope.data.total || 0) / PAGE_SIZE)
  const pairs = []
  for (let page = lastPage; page >= 1 && pairs.length < PAIRS_WANTED; page -= 1) {
    const { res, envelope } =
      page === 1 ? first : api(token, 'get', `/admin/process/findStarted/${page}/${PAGE_SIZE}`)
    if (envelope.code !== 200) throw new Error(`findStarted 第 ${page} 页失败 code=${envelope.code}`)
    for (const record of envelope.data.records || []) {
      if (record.status !== 2) continue
      const listed = api(token, 'get', `/admin/attachments/processes/${record.id}`)
      if (listed.envelope.code !== 200) continue
      const attachment = (listed.envelope.data.attachments || [])[0]
      if (attachment) {
        pairs.push({ processId: record.id, attachmentId: attachment.id, size: attachment.sizeBytes })
      }
      if (pairs.length >= PAIRS_WANTED) break
    }
  }
  const oneMb = pairs.filter((p) => (p.size || 0) <= ONE_MB)
  console.log(`setup: 附件对 ${pairs.length} 对，其中 1MB 档 ${oneMb.length} 对`)
  if (oneMb.length === 0) throw new Error('没有可下载的 1MB 附件，先跑 perf/seed.mjs')
  return { token, pairs: oneMb }
}

let payload = null

export default function (data) {
  if (Math.random() < 0.5) upload(data)
  else download(data)
}

function upload(data) {
  const draftId = uuid()
  const t0 = Date.now()
  const grant = api(
    data.token,
    'post',
    '/admin/attachments/uploads',
    {
      draftId,
      fileName: `perf-${draftId}.bin`,
      contentType: 'application/octet-stream',
      sizeBytes: ONE_MB,
    },
    'uploads',
  )
  const okGrant = grant.res.status === 200 && grant.envelope.code === 200
  businessErrorRate.add(!okGrant, { action: 'uploads' })
  check(grant.res, { 'uploads HTTP 200': (r) => r.status === 200 })
  if (!okGrant) {
    businessError.add(1, { action: 'uploads' })
    console.error(`biz uploads HTTP=${grant.res.status} code=${grant.envelope.code} msg=${String(grant.envelope.message).slice(0, 120)} body=${String(grant.res.body).slice(0, 120)}`)
    return
  }
  const put = http.put(
    grant.envelope.data.uploadUrl,
    getPayload(),
    {
      headers: grant.envelope.data.headers || { 'Content-Type': 'application/octet-stream' },
      tags: { action: 'minio-put', name: 'minio-put' },
    },
  )
  check(put, { 'MinIO PUT 2xx': (r) => r.status >= 200 && r.status < 300 })
  const complete = api(
    data.token,
    'post',
    `/admin/attachments/${grant.envelope.data.attachmentId}/complete`,
    undefined,
    'uploads',
  )
  const okComplete = complete.res.status === 200 && complete.envelope.code === 200
  businessErrorRate.add(!okComplete, { action: 'uploads' })
  check(complete.res, { 'complete HTTP 200': (r) => r.status === 200 })
  if (!okComplete) {
    businessError.add(1, { action: 'uploads' })
    console.error(`biz complete HTTP=${complete.res.status} code=${complete.envelope.code} msg=${String(complete.envelope.message).slice(0, 120)} body=${String(complete.res.body).slice(0, 120)}`)
  }
  uploadChain.add(Date.now() - t0)
}

function download(data) {
  const pair = data.pairs[Math.floor(Math.random() * data.pairs.length)]
  const t0 = Date.now()
  const grant = api(
    data.token,
    'post',
    `/admin/attachments/processes/${pair.processId}/${pair.attachmentId}/download-url`,
    undefined,
    'downloadUrl',
  )
  const okGrant = grant.res.status === 200 && grant.envelope.code === 200
  businessErrorRate.add(!okGrant, { action: 'downloadUrl' })
  check(grant.res, { 'download-url HTTP 200': (r) => r.status === 200 })
  if (!okGrant) {
    businessError.add(1, { action: 'downloadUrl' })
    console.error(`biz downloadUrl HTTP=${grant.res.status} code=${grant.envelope.code} msg=${String(grant.envelope.message).slice(0, 120)} body=${String(grant.res.body).slice(0, 120)}`)
    return
  }
  const get = http.get(grant.envelope.data.downloadUrl, {
    tags: { action: 'minio-get', name: 'minio-get' },
  })
  const okGet = get.status >= 200 && get.status < 300
  businessErrorRate.add(!okGet, { action: 'downloadUrl' })
  check(get, { 'MinIO GET 2xx': (r) => r.status >= 200 && r.status < 300 })
  if (!okGet) {
    businessError.add(1, { action: 'downloadUrl' })
    console.error(`biz minio-get HTTP=${get.status} body=${String(get.body).slice(0, 120)}`)
  }
  downloadChain.add(Date.now() - t0)
}

function getPayload() {
  if (!payload) payload = new Uint8Array(ONE_MB)
  return payload
}
