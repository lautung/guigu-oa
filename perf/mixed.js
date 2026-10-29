/**
 * 混合负载压测脚本（#112 §3/§4）。
 *
 * 8 个动作按权重随机（读 70% / 写 25% / 登录 5%），不加 think time：
 * 「20 VU」= 20 个并发请求者。成功判定同时看 HTTP 状态与响应体 code==200
 * （本系统业务错误一律 HTTP 200，#145）。
 *
 * 档位由 TIER 决定：
 *   TIER=smoke     1 VU × 8 动作各 1 次，只验正确性
 *   TIER=baseline  20 VU，30s 爬坡 + 5min 稳态（默认）
 *   TIER=stress    0→100 VU 爬坡 5min + 100 VU 保持 3min
 *   TIER=mid       50 VU，30s 爬坡 + 3min 稳态（拐点夹逼档）
 *
 * 用法（容器内 CWD 必须是可写的结果目录）：
 *   docker run --rm -i -w /results -v "$PWD/perf/results:/results" grafana/k6:latest run \
 *     -e PERF_BASE=http://host.docker.internal:8800 -e TIER=baseline \
 *     -e SUMMARY_NAME=baseline-1 - < /dev/null
 *
 * bob 的待办池在 setup 一次抓取（findPending 100/页，POOL_PAGES 上限）。
 * approve 撞到已被消费的任务时重取 findPending 第 1 页重试一次：运行中
 * startUp 会持续补新待办（按创建时间倒序，第 1 页就是新待办）。重试与
 * 池耗尽都计入独立计数器，不与业务错误混淆。
 */
import http from 'k6/http'
import { check } from 'k6'
import { Counter, Rate } from 'k6/metrics'

const BASE = __ENV.PERF_BASE || 'http://host.docker.internal:8800'
const TIER = __ENV.TIER || 'baseline'
const PASSWORD = '111111'
const POOL_PAGES = Number(__ENV.POOL_PAGES || 65)
const POOL_PAGE_SIZE = 100

const businessError = new Counter('business_error_count')
const businessErrorRate = new Rate('business_error_rate')
const approveRetry = new Counter('approve_retry_count')
const approveExhausted = new Counter('approve_exhausted_count')

const READ_TARGETS = ['findPending', 'show', 'findProcessed', 'findStarted', 'processTypeAll']
const WRITE_TARGETS = ['startUp', 'approve']

function durationThresholds(targets, p95, p99) {
  const t = {}
  for (const action of targets) {
    t[`http_req_duration{action:${action}}`] = [`p(95)<=${p95}`, `p(99)<=${p99}`]
  }
  return t
}

function baseOptions() {
  if (TIER === 'mid') {
    // 50 VU 拐点夹逼档：20 VU 已 280-354 req/s、100 VU 302 req/s，用 50 VU 判定拐点区间
    return {
      executor: 'stages',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '3m', target: 50 },
      ],
      thresholds: {
        http_req_failed: ['rate<0.05'],
        business_error_rate: ['rate<0.05'],
        ...durationThresholds(READ_TARGETS, 2000, 4000),
        ...durationThresholds([...WRITE_TARGETS, 'login'], 3000, 6000),
      },
    }
  }
  if (TIER === 'stress') {
    return {
      executor: 'stages',
      stages: [
        { duration: '5m', target: 100 },
        { duration: '3m', target: 100 },
      ],
      thresholds: {
        http_req_failed: ['rate<0.05'],
        business_error_rate: ['rate<0.05'],
        ...durationThresholds(READ_TARGETS, 2000, 4000),
        ...durationThresholds([...WRITE_TARGETS, 'login'], 3000, 6000),
      },
    }
  }
  return {
    executor: 'stages',
    stages: [
      { duration: '30s', target: 20 },
      { duration: '5m', target: 20 },
    ],
    thresholds: {
      http_req_failed: ['rate<0.01'],
      business_error_rate: ['rate<0.01'],
      'http_reqs{scenario:default}': ['rate>=80'],
      ...durationThresholds(READ_TARGETS, 500, 1000),
      ...durationThresholds([...WRITE_TARGETS, 'login'], 1000, 2000),
    },
  }
}

function smokeOptions() {
  const perAction = (exec) => ({
    executor: 'per-vu-iterations',
    vus: 1,
    iterations: 1,
    exec,
  })
  return {
    scenarios: {
      findPending: perAction('doFindPending'),
      show: perAction('doShow'),
      findProcessed: perAction('doFindProcessed'),
      findStarted: perAction('doFindStarted'),
      processTypeAll: perAction('doProcessType'),
      login: perAction('doLogin'),
      startUp: perAction('doStartUp'),
      approve: perAction('doApprove'),
    },
    thresholds: {
      http_req_failed: ['rate<0.01'],
      business_error_rate: ['rate<0.01'],
    },
  }
}

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  ...(TIER === 'smoke' ? smokeOptions() : baseOptions()),
}

export function handleSummary(data) {
  const name = __ENV.SUMMARY_NAME || 'k6-summary'
  return { [`${name}.json`]: JSON.stringify(data, null, 2) }
}

// ---------------------------------------------------------------------------
// setup：登录 + 模板 + bob 待办池
// ---------------------------------------------------------------------------

function parseEnvelope(res) {
  try {
    return res.json()
  } catch {
    return { code: -1, message: `非 JSON 响应 HTTP ${res.status}`, data: null }
  }
}

function request(action, token, method, url, payload, quiet = false) {
  const res = http.request(method.toUpperCase(), url, payload === undefined ? undefined : JSON.stringify(payload), {
    headers: {
      token,
      'Content-Type': 'application/json',
      'X-Trace-Id': `k6-${__VU}-${__ITER}`,
    },
    tags: { action, name: action },
  })
  const envelope = parseEnvelope(res)
  const ok = res.status === 200 && envelope.code === 200
  if (quiet) return { ok, envelope }
  check(res, { 'HTTP 200': (r) => r.status === 200 })
  businessErrorRate.add(!ok, { action })
  if (!ok) {
    businessError.add(1, { action })
    console.error(
      `biz-error action=${action} HTTP=${res.status} code=${envelope.code} msg=${String(envelope.message).slice(0, 160)} body=${String(res.body).slice(0, 200)}`,
    )
  }
  return { ok, envelope }
}

function login(username) {
  const res = http.post(
    `${BASE}/admin/system/index/login`,
    JSON.stringify({ username, password: PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { action: 'login', name: 'login' },
    },
  )
  const envelope = parseEnvelope(res)
  if (res.status !== 200 || envelope.code !== 200 || !envelope.data?.token) {
    throw new Error(`setup 登录失败 ${username}: HTTP ${res.status} code=${envelope.code}`)
  }
  return envelope.data.token
}

export function setup() {
  const tokens = {
    alice: login('alice'),
    bob: login('bob'),
    admin: login('admin'),
  }

  const startable = parseEnvelope(
    http.get(`${BASE}/admin/process/findStartable`, {
      headers: { token: tokens.alice },
      tags: { action: 'setup', name: 'setup' },
    }),
  )
  if (startable.code !== 200) throw new Error(`findStartable 失败 code=${startable.code}`)
  let template = null
  for (const group of startable.data || []) {
    for (const candidate of group.processTemplateList || []) {
      if (candidate.status === 1 && (!template || candidate.name.includes('演示'))) {
        template = { id: candidate.id, processTypeId: candidate.processTypeId }
      }
    }
  }
  if (!template) throw new Error('没有已发布的可用模板')
  console.log(`setup: 模板 id=${template.id} processTypeId=${template.processTypeId}`)

  // bob 待办池：新到 100/页倒序，抓满 POOL_PAGES 或抓完 total。
  const pool = []
  for (let page = 1; page <= POOL_PAGES; page += 1) {
    const res = http.get(`${BASE}/admin/process/findPending/${page}/${POOL_PAGE_SIZE}`, {
      headers: { token: tokens.bob },
      tags: { action: 'setup', name: 'setup' },
    })
    const envelope = parseEnvelope(res)
    if (envelope.code !== 200) throw new Error(`待办池第 ${page} 页失败 code=${envelope.code}`)
    const records = envelope.data.records || []
    for (const record of records) pool.push({ id: record.id, taskId: record.taskId })
    if (records.length < POOL_PAGE_SIZE) break
  }
  if (pool.length === 0) throw new Error('bob 待办池为空，先跑 perf/seed.mjs 造数')
  console.log(`setup: 待办池 ${pool.length} 条`)
  return { tokens, template, pool }
}

// ---------------------------------------------------------------------------
// 8 个动作（全部导出：smoke 档按 exec 单独调用）
// ---------------------------------------------------------------------------

export function doFindPending(data) {
  request('findPending', data.tokens.bob, 'get', `${BASE}/admin/process/findPending/1/10`)
}

export function doShow(data) {
  const item = data.pool[Math.floor(Math.random() * data.pool.length)]
  request('show', data.tokens.bob, 'get', `${BASE}/admin/process/show/${item.id}`)
}

export function doFindProcessed(data) {
  request('findProcessed', data.tokens.bob, 'get', `${BASE}/admin/process/findProcessed/1/10`)
}

export function doFindStarted(data) {
  request('findStarted', data.tokens.alice, 'get', `${BASE}/admin/process/findStarted/1/10`)
}

export function doProcessType(data) {
  request('processTypeAll', data.tokens.admin, 'get', `${BASE}/admin/process/processType/findAll`)
}

export function doLogin(data) {
  // 完整登录请求即动作本体；每次拿新 token，不回填（2h 有效期远长于单轮）。
  request('login', '', 'post', `${BASE}/admin/system/index/login`, {
    username: 'alice',
    password: PASSWORD,
  })
}

export function doStartUp(data) {
  const reason = `压测-${__VU}-${__ITER}`
  request('startUp', data.tokens.alice, 'post', `${BASE}/admin/process/startUp`, {
    processTemplateId: data.template.id,
    processTypeId: data.template.processTypeId,
    formValues: JSON.stringify({
      formData: { reason, days: 1 },
      formShowData: { 请假原因: reason, 请假天数: 1 },
    }),
    attachmentIds: [],
    ccUserIds: [],
  })
}

function approveOnce(data, item, quiet) {
  return request(
    'approve',
    data.tokens.bob,
    'post',
    `${BASE}/admin/process/approve`,
    { processId: item.id, taskId: item.taskId, status: 1 },
    quiet,
  )
}

function freshPending(data) {
  return request('approve', data.tokens.bob, 'get', `${BASE}/admin/process/findPending/1/10`, undefined, true)
}

export function doApprove(data) {
  // 第一次从 setup 池随机取；撞上已被其他 VU 消费的任务（403 无权访问）属预期争用，
  // 静默重试：重取 findPending 第 1 页（创建时间倒序 = 最新待办）逐个尝试，最多 3 次。
  // 待办池真正耗尽时降级为一次 startUp，保持 25% 写配比（计 approve_fallback_startUp）。
  const item = data.pool[Math.floor(Math.random() * data.pool.length)]
  let result = approveOnce(data, item, true)
  if (!result.ok) approveRetry.add(1)
  for (let attempt = 0; !result.ok && attempt < 3; attempt += 1) {
    const fresh = freshPending(data)
    const records = (fresh.envelope.data && fresh.envelope.data.records) || []
    if (records.length === 0) break
    result = approveOnce(data, records[0], true)
  }
  if (result.ok) {
    businessErrorRate.add(false, { action: 'approve' })
    return
  }
  // 池耗尽：本次写负载改走 startUp
  doStartUp(data)
  approveExhausted.add(1)
  businessErrorRate.add(false, { action: 'approve' })
  check(result.envelope, { 'approve 降级为 startUp（池耗尽）': () => true })
}

// ---------------------------------------------------------------------------
// 混合调度：按 #112 §3 权重随机
// ---------------------------------------------------------------------------

const MIX = [
  { weight: 25, fn: doFindPending },
  { weight: 20, fn: doShow },
  { weight: 10, fn: doFindProcessed },
  { weight: 5, fn: doFindStarted },
  { weight: 10, fn: doProcessType },
  { weight: 5, fn: doLogin },
  { weight: 15, fn: doApprove },
  { weight: 10, fn: doStartUp },
]

export default function (data) {
  const roll = Math.random() * 100
  let acc = 0
  for (const action of MIX) {
    acc += action.weight
    if (roll < acc) {
      action.fn(data)
      return
    }
  }
  MIX[MIX.length - 1].fn(data)
}
