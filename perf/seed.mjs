#!/usr/bin/env node
/**
 * 性能造数脚本（#112 §5）：走真实 API 造 10,000 条审批实例 + 附件，不直插 ACT_* 表。
 *
 *   node perf/seed.mjs                       # TARGET=10000, FINISHED=4000, 并发 20
 *   TARGET=5000 FINISHED=2000 node perf/seed.mjs
 *
 * 流程：
 *   A. alice 发起 TARGET 条审批（演示模板由 Compose 启动时 DemoApprovalPublisher
 *      自动发布，#128；脚本发现没有已发布模板时直接失败退出）。
 *   B. bob 分页收集 FINISHED 条待办并逐条 approve → 已结束实例。
 *      剩下 TARGET-FINISHED 条保持待处理。
 *   C. 在 100 条已结束实例上各传 1 个 1MB 附件、10 条上各传 1 个 5MB 附件。
 *
 * 输出 perf/results/seed-summary.json 与全程进度日志。结束行数核对在
 * perf/ops/row-counts.sh 里做。
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { randomUUID } from 'node:crypto'

const BASE = process.env.PERF_BASE || 'http://localhost:8800'
const MINIO_PUT_BASE = process.env.PERF_MINIO_PUT || 'http://localhost:9000'
const TARGET = Number(process.env.TARGET || 10000)
const FINISHED = Number(process.env.FINISHED || 4000)
const CONCURRENCY = Number(process.env.CONCURRENCY || 20)
const ATTACH_SMALL = Number(process.env.ATTACH_SMALL || 100)
const ATTACH_BIG = Number(process.env.ATTACH_BIG || 10)
const PASSWORD = '111111'
const RESULTS = new URL('./results/', import.meta.url).pathname

mkdirSync(RESULTS, { recursive: true })
const startedAt = Date.now()
const log = (msg) => console.log(`[${((Date.now() - startedAt) / 1000).toFixed(0)}s] ${msg}`)

async function api(token, method, path, payload) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { token } : {}),
    },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
  let body
  try {
    body = await res.json()
  } catch {
    throw new Error(`${method} ${path} → HTTP ${res.status} 非 JSON`)
  }
  if (body.code !== 200) {
    throw new Error(`${method} ${path} → HTTP ${res.status} code=${body.code} ${body.message ?? ''}`)
  }
  return body.data
}

const login = (username) => api(null, 'post', '/admin/system/index/login', { username, password: PASSWORD })

async function withRetry(fn, attempts = 3) {
  let lastError
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, 250 * attempt))
    }
  }
  throw lastError
}

/** 固定并发跑任务列表；onError 抛出则整体失败。 */
async function runPool(items, worker, concurrency = CONCURRENCY) {
  let cursor = 0
  let done = 0
  const errors = []
  async function loop() {
    while (true) {
      const index = cursor++
      if (index >= items.length) return
      try {
        await worker(items[index], index)
      } catch (error) {
        errors.push({ index, error: String(error.message || error) })
      }
      done += 1
      if (done % 250 === 0 || done === items.length) {
        log(`${done}/${items.length} 完成，错误 ${errors.length}`)
      }
      if (errors.length > 50) throw new Error(`错误超过 50 个，中止：${errors[0].error}`)
    }
  }
  await Promise.all(Array.from({ length: concurrency }, loop))
  if (errors.length > 0) throw new Error(`共 ${errors.length} 个错误，首个：${errors[0].error}`)
}

const summary = {
  base: BASE,
  target: TARGET,
  finished: FINISHED,
  concurrency: CONCURRENCY,
  phases: {},
}

// ---------------------------------------------------------------------------

const alice = (await login('alice')).token
const bob = (await login('bob')).token

log('读取可发起模板')
const groups = await api(alice, 'get', '/admin/process/findStartable')
let template = null
for (const group of groups) {
  for (const candidate of group.processTemplateList || []) {
    if (candidate.status === 1 && (!template || candidate.name.includes('演示'))) {
      template = candidate
    }
  }
}
if (!template) {
  console.error('没有已发布的模板：演示模板应由 Compose 启动发布（#128），请确认栈是全新卷起的。')
  process.exit(1)
}
log(`模板：id=${template.id} name=${template.name}`)
summary.template = { id: template.id, name: template.name }

function formValues(index) {
  const reason = `压测-${index}`
  return JSON.stringify({
    formData: { reason, days: 1 },
    formShowData: { 请假原因: reason, 请假天数: 1 },
  })
}

// Phase A：补差造到 TARGET 条新实例（+1 是演示已结束实例）。先读总数再补，可安全重跑。
{
  const t0 = Date.now()
  const startOne = (i) => withRetry(() => api(alice, 'post', '/admin/process/startUp', {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: formValues(i),
    attachmentIds: [],
    ccUserIds: [],
  }))
  const expected = TARGET + 1
  let total = (await api(alice, 'get', '/admin/process/findStarted/1/1')).total
  let created = 0
  while (total < expected) {
    const batch = Math.min(expected - total, 2000)
    if (created > 0) log(`对账：已发起 ${total}/${expected}，再补 ${batch} 条`)
    await runPool(Array.from({ length: batch }, (_, i) => i), startOne)
    created += batch
    total = (await api(alice, 'get', '/admin/process/findStarted/1/1')).total
  }
  if (total > expected) log(`注意：已发起 ${total} 超出预期 ${expected}（重试撞上丢失响应时会重复发起）`)
  summary.phases.startUp = { created, totalNow: total, seconds: (Date.now() - t0) / 1000 }
  log(`Phase A 完成：本轮发起 ${created} 条，累计 ${total}（预期 ${expected}），耗时 ${(summary.phases.startUp.seconds).toFixed(0)}s`)
}

// Phase B：补差审批到 FINISHED 条已结束（+1 是演示实例）。先读已办总数再补。
{
  const t0 = Date.now()
  const expected = FINISHED + 1
  let processed = (await api(bob, 'get', '/admin/process/findProcessed/1/1')).total
  if (processed > expected) throw new Error(`已办 ${processed} 已超出预期 ${expected}，请换全新卷`)
  const need = expected - processed
  if (need > 0) {
    const tasks = []
    for (let page = 1; tasks.length < need; page += 1) {
      const data = await api(bob, 'get', `/admin/process/findPending/${page}/100`)
      const records = data.records || []
      for (const record of records) tasks.push({ id: record.id, taskId: record.taskId })
      if (records.length < 100) break
    }
    if (tasks.length < need) {
      throw new Error(`待办只有 ${tasks.length} 条，少于要审批的 ${need} 条`)
    }
    log(`收集到 ${tasks.length} 条待办，审批其中 ${need} 条`)
    await runPool(tasks.slice(0, need), (task) => withRetry(() => api(bob, 'post', '/admin/process/approve', {
      processId: task.id,
      taskId: task.taskId,
      status: 1,
    })))
    processed = (await api(bob, 'get', '/admin/process/findProcessed/1/1')).total
  }
  summary.phases.approve = { approved: need, processedNow: processed, seconds: (Date.now() - t0) / 1000 }
  log(`Phase B 完成：本轮审批 ${need} 条，已办累计 ${processed}（预期 ${expected}），耗时 ${(summary.phases.approve.seconds).toFixed(0)}s`)
}

// Phase C：附件（已结束实例，alice 是发起人即成员）
// 附件挂最老的已结束实例；列表自 #179 起显式最新在前，旧的「第 1 页 = 最老实例」
// 隐式顺序假设失效——从最后一页向前翻，不依赖排序方向（#182）。
// 下面这套尾页起翻逻辑与 perf/attachment.js setup 是双份实现（#178 评审）：曾想抽到
// perf/lib/ 共享模块，但 attachment.js 由 round.sh 以 stdin 喂 k6 脚本且容器只挂
// perf/results，共享模块文件在 k6 容器内不可达——两处必须同步改，改掉 round.sh 的
// 挂载与传参协议后才具备抽共享条件。
{
  const t0 = Date.now()
  const PAGE_SIZE = 50
  const first = await api(alice, 'get', `/admin/process/findStarted/1/${PAGE_SIZE}`)
  const lastPage = Math.ceil((first.total || 0) / PAGE_SIZE)
  const finished = []
  for (let page = lastPage; page >= 1 && finished.length < ATTACH_SMALL + ATTACH_BIG; page -= 1) {
    const data = page === 1 ? first : await api(alice, 'get', `/admin/process/findStarted/${page}/${PAGE_SIZE}`)
    for (const record of data.records || []) {
      if (record.status === 2) finished.push(record.id)
    }
  }
  if (finished.length < ATTACH_SMALL + ATTACH_BIG) {
    throw new Error(`已结束实例只有 ${finished.length} 条，不够挂附件`)
  }
  const oneMb = Buffer.alloc(1024 * 1024, 7)
  const fiveMb = Buffer.alloc(5 * 1024 * 1024, 7)

  async function attach(processId, buffer) {
    const draftId = randomUUID()
    const grant = await api(alice, 'post', '/admin/attachments/uploads', {
      processId,
      draftId,
      fileName: `perf-${processId}.bin`,
      contentType: 'application/octet-stream',
      sizeBytes: buffer.length,
    })
    const url = grant.uploadUrl.replace(/^http:\/\/localhost:9000/, MINIO_PUT_BASE)
    const put = await fetch(url, {
      method: 'put',
      headers: grant.headers || { 'Content-Type': 'application/octet-stream' },
      body: buffer,
    })
    if (!put.ok) throw new Error(`MinIO PUT HTTP ${put.status}`)
    await api(alice, 'post', `/admin/attachments/${grant.attachmentId}/complete`)
  }

  // 幂等：已有附件的实例跳过（重跑不重复挂）
  const needAttach = []
  for (const id of finished) {
    const list = await api(alice, 'get', `/admin/attachments/processes/${id}`)
    if ((list.attachments || []).length === 0) needAttach.push(id)
    if (needAttach.length >= ATTACH_SMALL + ATTACH_BIG) break
  }
  const jobs = [
    ...needAttach.slice(0, ATTACH_SMALL).map((id) => ({ id, buffer: oneMb })),
    ...needAttach.slice(ATTACH_SMALL, ATTACH_SMALL + ATTACH_BIG).map((id) => ({ id, buffer: fiveMb })),
  ]
  await runPool(jobs, (job) => withRetry(() => attach(job.id, job.buffer), 3), 10)
  summary.phases.attachments = { count: jobs.length, seconds: (Date.now() - t0) / 1000 }
  log(`Phase C 完成：${jobs.length} 个附件，耗时 ${(summary.phases.attachments.seconds).toFixed(0)}s`)
}

summary.totalSeconds = (Date.now() - startedAt) / 1000
log(`全部完成，总耗时 ${summary.totalSeconds.toFixed(0)}s`)
writeFileSync(`${RESULTS}seed-summary.json`, JSON.stringify(summary, null, 2))
