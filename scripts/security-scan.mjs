#!/usr/bin/env node

/**
 * 安全定向用例扫描（#113 决议）。
 *
 * 只打本机 Compose 演示栈：9530（统一客户端，经 /dev-api 反代）与 8800（匿名面）。
 * 每条用例有 ID、期望、实际、结果；支持 --only SEC-JWT,SEC-IDOR（族或具体用例 ID）。
 *
 *   node scripts/security-scan.mjs --out docs/test/evidence/2026-10-29-security-baseline
 *   node scripts/security-scan.mjs --only SEC-JWT,SEC-XSS --out docs/test/evidence/<轮次>
 *
 * `--out` 必填：证据目录必须显式指定，避免不带参数时落到基线目录。目标目录已有
 * `security-results.json` 时拒绝覆盖，确需重写同一轮加 `--force`。
 *
 * 证据只保留结论/长度/状态码与脱敏摘要，不写真实 token 或密码。
 */

import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const REPO_ROOT = path.resolve(fileURLToPath(new URL('..', import.meta.url)))
const ORIGIN = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')
const API = `${ORIGIN}/dev-api`
const API_DIRECT = (process.env.API_ORIGIN || 'http://localhost:8800').replace(/\/$/, '')
const CLIENT_DIR = path.join(REPO_ROOT, 'client', 'guigu-oa-client')
const PASSWORD = '111111'
const UPLOAD_TTL_SECONDS = 300

const state = {
  runId: `sec${Date.now().toString(36)}`,
  tokens: {},
  startedAt: new Date(),
  fixtures: {},
  results: [],
}

const REDACTED = '<redacted>'

function maskToken(token) {
  if (!token) return REDACTED
  return `${token.slice(0, 8)}…(${token.length})`
}

function codeOf(response) {
  return response?.json?.code ?? null
}

function messageOf(response) {
  return response?.json?.message ?? response?.error ?? ''
}

function dataOf(response) {
  return response?.json?.data ?? null
}

function pass(actual) {
  return { actual, pass: true }
}

function fail(actual) {
  return { actual, pass: false }
}

async function call(method, target, options = {}) {
  const { token, body, form, headers = {}, base = API, origin } = options
  const url = target.startsWith('http') ? target : `${base}${target}`
  const requestHeaders = { ...headers }
  if (token) requestHeaders.token = token
  if (origin) requestHeaders.Origin = origin

  let payload
  if (form !== undefined) {
    payload = form
  } else if (body !== undefined) {
    requestHeaders['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  const started = Date.now()
  try {
    const response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: payload,
      redirect: 'manual',
    })
    const text = await response.text()
    let json = null
    try {
      json = JSON.parse(text)
    } catch {
      // 非 JSON 响应按原文保留摘要
    }
    return {
      status: response.status,
      json,
      text,
      headers: Object.fromEntries(response.headers),
      ms: Date.now() - started,
    }
  } catch (error) {
    return { status: 0, error: String(error), json: null, text: '', headers: {}, ms: Date.now() - started }
  }
}

async function login(username, password = PASSWORD) {
  const response = await call('post', '/admin/system/index/login', {
    body: { username, password },
  })
  if (codeOf(response) !== 200 || !dataOf(response)?.token) {
    throw new Error(`登录失败 ${username}: code=${codeOf(response)} ${messageOf(response)}`)
  }
  return dataOf(response)
}

async function findStartableTemplate(token) {
  const response = await call('get', '/admin/process/findStartable', { token })
  const groups = dataOf(response) || []
  for (const group of groups) {
    for (const template of group.processTemplateList || []) {
      if (template.name === '演示请假申请' && template.availableForInitiation) {
        return { id: template.id, processTypeId: group.id }
      }
    }
  }
  throw new Error('findStartable 里没有可发起的「演示请假申请」')
}

async function findStarted(token, marker) {
  const first = await call('get', '/admin/process/findStarted/1/50', { token })
  const firstRecords = dataOf(first)?.records || []
  const found = firstRecords.find((record) => JSON.stringify(record).includes(marker))
  if (found) return found
  // 已发起显式按创建时间倒序（#179）：刚发起的审批就在第一页；翻页循环只是防御性兜底。
  const pages = Math.ceil((dataOf(first)?.total || firstRecords.length) / 50)
  for (let page = pages; page >= 2; page -= 1) {
    const response = await call('get', `/admin/process/findStarted/${page}/50`, { token })
    const records = dataOf(response)?.records || []
    const hit = records.find((record) => JSON.stringify(record).includes(marker))
    if (hit) return hit
  }
  return null
}

async function findPending(token, processId) {
  for (let page = 1; page <= 8; page += 1) {
    const response = await call('get', `/admin/process/findPending/${page}/50`, { token })
    const records = dataOf(response)?.records || []
    const found = records.find((record) => record.id === processId)
    if (found) return found
    if (records.length < 50) break
  }
  return null
}

async function findUserId(token, username) {
  const response = await call(
    'get',
    `/admin/system/sysUser/1/50?keyword=${encodeURIComponent(username)}`,
    { token },
  )
  const record = (dataOf(response)?.records || []).find((item) => item.username === username)
  return record?.id ?? null
}

async function uploadDraft(token, { fileName, contentType, body, keepUploading = false }) {
  const draftId = crypto.randomUUID()
  const grant = await call('post', '/admin/attachments/uploads', {
    token,
    body: { draftId, fileName, contentType, sizeBytes: body.length },
  })
  if (codeOf(grant) !== 200) {
    throw new Error(`创建上传凭证失败: code=${codeOf(grant)} ${messageOf(grant)}`)
  }
  const grantData = dataOf(grant)
  if (keepUploading) return { draftId, attachmentId: grantData.attachmentId, grantData }

  const put = await fetch(grantData.uploadUrl, {
    method: 'PUT',
    headers: grantData.headers || { 'Content-Type': contentType },
    body,
  })
  const complete = await call('post', `/admin/attachments/${grantData.attachmentId}/complete`, {
    token,
  })
  return {
    draftId,
    attachmentId: grantData.attachmentId,
    putStatus: put.status,
    completeCode: codeOf(complete),
  }
}

async function startApproval(token, template, marker, options = {}) {
  const payload = {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: JSON.stringify({
      formData: { reason: marker, days: 1 },
      formShowData: { 请假原因: options.showReason ?? marker, 请假天数: 1 },
    }),
    attachmentIds: options.attachmentIds ?? [],
  }
  if (options.attachmentDraftId) payload.attachmentDraftId = options.attachmentDraftId

  const response = await call('post', '/admin/process/startUp', { token, body: payload })
  if (codeOf(response) !== 200) {
    throw new Error(`发起失败: code=${codeOf(response)} ${messageOf(response)}`)
  }
  const record = await findStarted(token, marker)
  if (!record) throw new Error(`发起后在已发起列表找不到 ${marker}`)
  return record
}

function readDevJwtSecret() {
  const file = path.join(
    REPO_ROOT,
    'server/guigu-oa-parent/service-oa/src/main/resources/application-dev.yml',
  )
  if (!fs.existsSync(file)) return null
  const text = fs.readFileSync(file, 'utf8')
  const match = text.match(/jwt:\s*\n\s*secret:\s*["']?([^"'\s#]+)["']?/)
  return match ? match[1] : null
}

function signHs256(payload, secret) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url')
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url')
  return `${header}.${body}.${signature}`
}

function tamperPayload(token) {
  const [header, body, signature] = token.split('.')
  const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'))
  payload.userId = 1
  payload.username = 'admin'
  return `${header}.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.${signature}`
}

async function prepare() {
  for (const username of ['admin', 'user', 'alice', 'bob', 'carol']) {
    state.tokens[username] = (await login(username)).token
  }

  state.fixtures.template = await findStartableTemplate(state.tokens.alice)
  state.fixtures.userIds = {}
  for (const username of ['admin', 'user', 'alice', 'bob', 'carol']) {
    state.fixtures.userIds[username] = await findUserId(state.tokens.admin, username)
  }

  // 只用于弱口令与停用 token 用例的临时账号，结束时删除。
  const bruteUsername = `sec-b-${state.runId}`
  const bruteSave = await call('post', '/admin/system/sysUser/save', {
    token: state.tokens.admin,
    body: {
      username: bruteUsername,
      password: PASSWORD,
      name: '安全扫描临时账号',
      status: 1,
      description: state.runId,
    },
  })
  if (codeOf(bruteSave) !== 200) {
    throw new Error(`临时账号创建失败: code=${codeOf(bruteSave)} ${messageOf(bruteSave)}`)
  }
  state.fixtures.brute = {
    username: bruteUsername,
    id: await findUserId(state.tokens.admin, bruteUsername),
  }
  if (!state.fixtures.brute.id) throw new Error('临时账号创建后查不到 id')
  state.fixtures.brute.token = (await login(bruteUsername)).token

  // 未发布模板：验证办事侧模板读只暴露已发布。
  const typeResponse = await call('get', '/admin/process/processType/findAll', {
    token: state.tokens.admin,
  })
  const processTypeId = dataOf(typeResponse)?.[0]?.id
  if (!processTypeId) throw new Error('没有可用的审批类型')
  const templateName = `sec-unpublished-${state.runId}`
  await call('post', '/admin/process/processTemplate/save', {
    token: state.tokens.admin,
    body: {
      name: templateName,
      processTypeId,
      iconUrl: '',
      description: state.runId,
      formProps: '[]',
      formOptions: '{}',
    },
  })
  const templateList = await call('get', '/admin/process/processTemplate/1/50', {
    token: state.tokens.admin,
  })
  const unpublished = (dataOf(templateList)?.records || []).find((item) => item.name === templateName)
  if (!unpublished) throw new Error('未发布模板创建后查不到')
  state.fixtures.unpublishedTemplateId = unpublished.id

  // 审批 A：附件 + 申请人 alice / 当前处理人 bob / 抄送 carol。
  const markerA = `sec-${state.runId}-a`
  const draft = await uploadDraft(state.tokens.alice, {
    fileName: `sec-note-${state.runId}.txt`,
    contentType: 'text/plain',
    body: Buffer.from(`sec attachment ${state.runId}`),
  })
  if (draft.completeCode !== 200) {
    throw new Error(`附件登记失败: code=${draft.completeCode}`)
  }
  const approvalA = await startApproval(state.tokens.alice, state.fixtures.template, markerA, {
    attachmentIds: [draft.attachmentId],
    attachmentDraftId: draft.draftId,
  })
  state.fixtures.approvalA = {
    id: approvalA.id,
    marker: markerA,
    attachmentId: draft.attachmentId,
  }
  const pendingA = await findPending(state.tokens.bob, approvalA.id)
  state.fixtures.approvalA.taskId = pendingA?.taskId
  if (!state.fixtures.approvalA.taskId) throw new Error('审批 A 在 bob 待处理里没有 taskId')

  // 审批 B：只用于「历史处理人」能力矩阵。
  const markerB = `sec-${state.runId}-b`
  const approvalB = await startApproval(state.tokens.alice, state.fixtures.template, markerB)
  state.fixtures.approvalB = { id: approvalB.id, marker: markerB }
  const pendingB = await findPending(state.tokens.bob, approvalB.id)
  state.fixtures.approvalB.taskId = pendingB?.taskId
  if (!state.fixtures.approvalB.taskId) throw new Error('审批 B 在 bob 待处理里没有 taskId')

  // 他人的待绑定附件（PENDING）与仅登记未上传（UPLOADING）。
  const pendingDraft = await uploadDraft(state.tokens.alice, {
    fileName: `sec-pending-${state.runId}.txt`,
    contentType: 'text/plain',
    body: Buffer.from(`sec pending ${state.runId}`),
  })
  state.fixtures.pendingAttachment = pendingDraft
  const uploading = await uploadDraft(state.tokens.alice, {
    fileName: `sec-uploading-${state.runId}.txt`,
    contentType: 'text/plain',
    body: Buffer.from(`sec uploading ${state.runId}`),
    keepUploading: true,
  })
  state.fixtures.uploadingAttachment = uploading

  // 过期凭证：立即登记，等 5 分钟 TTL 过后再完成。
  const expiredDraftId = crypto.randomUUID()
  const expiredGrant = await call('post', '/admin/attachments/uploads', {
    token: state.tokens.alice,
    body: {
      draftId: expiredDraftId,
      fileName: `sec-expired-${state.runId}.txt`,
      contentType: 'text/plain',
      sizeBytes: 4,
    },
  })
  state.fixtures.expiredUpload = {
    draftId: expiredDraftId,
    attachmentId: dataOf(expiredGrant)?.attachmentId,
    uploadUrl: dataOf(expiredGrant)?.uploadUrl,
    headers: dataOf(expiredGrant)?.headers,
    createdAt: Date.now(),
  }
}

function record(def, outcome, durationMs) {
  state.results.push({
    id: def.id,
    family: def.family,
    title: def.title,
    severity: def.severity ?? null,
    expected: def.expected,
    actual: outcome.actual,
    pass: outcome.pass,
    skipped: outcome.skipped ?? false,
    durationMs,
    evidence: outcome.evidence ?? null,
  })
}

async function runCase(def) {
  const started = Date.now()
  try {
    const outcome = await def.run()
    record(def, outcome, Date.now() - started)
  } catch (error) {
    record(
      def,
      { actual: `执行异常：${error.message}`, pass: false, evidence: { error: error.message } },
      Date.now() - started,
    )
  }
  const last = state.results[state.results.length - 1]
  const mark = last.skipped ? 'SKIP' : last.pass ? 'PASS' : 'FAIL'
  console.log(`${mark} ${last.id} ${last.title} — ${last.actual}`)
}

/**
 * 删掉 prepare 造的临时账号与未发布模板。保留会污染 `compose-baseline-check.sh`
 * 的「种子用户齐全」断言；审批与附件无法经 API 删除，随快照恢复清理。
 */
async function cleanupFixtures() {
  if (!state.tokens.admin) return
  const attempts = []
  if (state.fixtures.brute?.id) {
    attempts.push(['sysUser', `/admin/system/sysUser/remove/${state.fixtures.brute.id}`])
  }
  if (state.fixtures.unpublishedTemplateId) {
    attempts.push([
      'processTemplate',
      `/admin/process/processTemplate/remove/${state.fixtures.unpublishedTemplateId}`,
    ])
  }
  for (const [label, target] of attempts) {
    const response = await call('delete', target, { token: state.tokens.admin })
    if (codeOf(response) !== 200) {
      console.warn(`测试夹具清理失败 ${label}: code=${codeOf(response)} ${messageOf(response)}`)
    }
  }
}

const cases = []
function defineCase(def) {
  cases.push(def)
}

/** SEC-JWT：令牌完整性与生命周期。 */
defineCase({
  id: 'SEC-JWT-01',
  family: 'SEC-JWT',
  severity: 'S1',
  title: '无 token 访问受保护接口被拒',
  expected: 'body.code=208',
  async run() {
    const response = await call('get', '/admin/system/index/info')
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)} http=${response.status}`)
  },
})

defineCase({
  id: 'SEC-JWT-02',
  family: 'SEC-JWT',
  severity: 'S1',
  title: '伪造 token 被拒',
  expected: 'body.code=208',
  async run() {
    const response = await call('get', '/admin/system/index/info', { token: 'not-a-jwt' })
    return codeOf(response) === 208 ? pass('code=208') : fail(`code=${codeOf(response)}`)
  },
})

defineCase({
  id: 'SEC-JWT-03',
  family: 'SEC-JWT',
  severity: 'S1',
  title: '篡改 payload（userId→admin）但保留签名被拒',
  expected: 'body.code=208',
  async run() {
    const forged = tamperPayload(state.tokens.alice)
    const response = await call('get', '/admin/system/index/info', { token: forged })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（篡改令牌被接受）`)
  },
})

defineCase({
  id: 'SEC-JWT-04',
  family: 'SEC-JWT',
  severity: 'S1',
  title: 'alg=none 无签名令牌被拒',
  expected: 'body.code=208',
  async run() {
    const now = Math.floor(Date.now() / 1000)
    const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
    const payload = Buffer.from(
      JSON.stringify({
        sub: 'AUTH-USER',
        userId: 1,
        username: 'admin',
        tokenType: 'access',
        iat: now,
        exp: now + 3600,
      }),
    ).toString('base64url')
    const response = await call('get', '/admin/system/index/info', {
      token: `${header}.${payload}.`,
    })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（alg=none 被接受）`)
  },
})

defineCase({
  id: 'SEC-JWT-05',
  family: 'SEC-JWT',
  severity: 'S1',
  title: 'refresh token 当作 access token 使用被拒',
  expected: 'body.code=208',
  async run() {
    const session = await login('alice')
    const response = await call('get', '/admin/system/index/info', {
      token: session.refreshToken,
    })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（refresh token 可作 access）`)
  },
})

defineCase({
  id: 'SEC-JWT-06',
  family: 'SEC-JWT',
  severity: 'S1',
  title: 'access token 当作 refresh token 使用被拒',
  expected: 'body.code=208',
  async run() {
    const session = await login('alice')
    const response = await call('post', '/admin/system/index/refresh', {
      body: { refreshToken: session.token },
    })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（access token 可续期）`)
  },
})

defineCase({
  id: 'SEC-JWT-07',
  family: 'SEC-JWT',
  severity: 'S3',
  title: '登出后 access token 仍可访问（令牌未吊销）',
  expected: 'body.code=208（登出即失效）',
  async run() {
    const session = await login('alice')
    const logout = await call('post', '/admin/system/index/logout', { token: session.token })
    const replay = await call('get', '/admin/system/index/info', { token: session.token })
    const evidence = {
      logoutCode: codeOf(logout),
      replayCode: codeOf(replay),
      token: maskToken(session.token),
    }
    return codeOf(replay) === 208
      ? { ...pass('code=208，登出后令牌失效'), evidence }
      : { ...fail(`登出 code=${codeOf(logout)}，重放仍 code=${codeOf(replay)}`), evidence }
  },
})

defineCase({
  id: 'SEC-JWT-08',
  family: 'SEC-JWT',
  severity: 'S2',
  title: '停用账号的存量 token 被拒',
  expected: 'body.code=209',
  async run() {
    const userId = state.fixtures.brute.id
    await call('put', `/admin/system/sysUser/updateStatus/${userId}/0`, {
      token: state.tokens.admin,
    })
    try {
      const response = await call('get', '/admin/system/index/info', {
        token: state.fixtures.brute.token,
      })
      return codeOf(response) === 209
        ? pass('code=209')
        : fail(`code=${codeOf(response)}（停用后令牌仍可用）`)
    } finally {
      await call('put', `/admin/system/sysUser/updateStatus/${userId}/1`, {
        token: state.tokens.admin,
      })
    }
  },
})

defineCase({
  id: 'SEC-JWT-09',
  family: 'SEC-JWT',
  severity: 'S1',
  title: '过期 token 被拒',
  expected: 'body.code=208',
  async run() {
    const secret = readDevJwtSecret()
    if (!secret) return { actual: '跳过：读不到 dev JWT secret', pass: true, skipped: true }
    const now = Math.floor(Date.now() / 1000)
    const expired = signHs256(
      {
        sub: 'AUTH-USER',
        userId: state.fixtures.userIds.user,
        username: 'user',
        tokenType: 'access',
        iat: now - 7200,
        exp: now - 3600,
      },
      secret,
    )
    const response = await call('get', '/admin/system/index/info', { token: expired })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（过期令牌被接受）`)
  },
})

defineCase({
  id: 'SEC-JWT-10',
  family: 'SEC-JWT',
  severity: 'S1',
  title: '其他密钥签名的 token 被拒',
  expected: 'body.code=208',
  async run() {
    const now = Math.floor(Date.now() / 1000)
    const foreign = signHs256(
      {
        sub: 'AUTH-USER',
        userId: 1,
        username: 'admin',
        tokenType: 'access',
        iat: now,
        exp: now + 3600,
      },
      crypto.randomBytes(64).toString('hex'),
    )
    const response = await call('get', '/admin/system/index/info', { token: foreign })
    return codeOf(response) === 208
      ? pass('code=208')
      : fail(`code=${codeOf(response)}（异密钥令牌被接受）`)
  },
})

/** SEC-IDOR：跨用户对象访问。 */
defineCase({
  id: 'SEC-IDOR-01',
  family: 'SEC-IDOR',
  severity: 'S1',
  title: '非成员读取他人审批详情被拒',
  expected: 'body.code=403',
  async run() {
    const response = await call('get', `/admin/process/show/${state.fixtures.approvalA.id}`, {
      token: state.tokens.user,
    })
    return codeOf(response) === 403
      ? pass('code=403')
      : fail(`code=${codeOf(response)}（非成员可读审批详情）`)
  },
})

defineCase({
  id: 'SEC-IDOR-02',
  family: 'SEC-IDOR',
  severity: 'S1',
  title: '非成员处理他人审批被拒',
  expected: 'body.code=403',
  async run() {
    const response = await call('post', '/admin/process/approve', {
      token: state.tokens.user,
      body: {
        processId: state.fixtures.approvalA.id,
        taskId: state.fixtures.approvalA.taskId,
        status: 1,
      },
    })
    return codeOf(response) === 403
      ? pass('code=403')
      : fail(`code=${codeOf(response)}（非成员可处理审批）`)
  },
})

defineCase({
  id: 'SEC-IDOR-03',
  family: 'SEC-IDOR',
  severity: 'S1',
  title: '非成员读取审批附件列表被拒',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call(
      'get',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}`,
      { token: state.tokens.user },
    )
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}（非成员可读附件列表）`)
  },
})

defineCase({
  id: 'SEC-IDOR-04',
  family: 'SEC-IDOR',
  severity: 'S2',
  title: '非成员签发他人附件下载凭证被拒',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call(
      'post',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}/${state.fixtures.approvalA.attachmentId}/download-url`,
      { token: state.tokens.user },
    )
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}（非成员可获取下载凭证）`)
  },
})

defineCase({
  id: 'SEC-IDOR-05',
  family: 'SEC-IDOR',
  severity: 'S2',
  title: '他人草稿附件列表按所有者隔离',
  expected: 'data=[]（看不到 alice 的草稿）',
  async run() {
    const response = await call(
      'get',
      `/admin/attachments/drafts/${state.fixtures.pendingAttachment.draftId}`,
      { token: state.tokens.bob },
    )
    const records = dataOf(response)
    const visible = Array.isArray(records) ? records.length : -1
    return codeOf(response) === 200 && visible === 0
      ? pass(`code=200, records=0`)
      : fail(`code=${codeOf(response)}, records=${visible}`)
  },
})

defineCase({
  id: 'SEC-IDOR-06',
  family: 'SEC-IDOR',
  severity: 'S2',
  title: '他人待绑定附件不可删除',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call(
      'delete',
      `/admin/attachments/pending/${state.fixtures.pendingAttachment.attachmentId}`,
      { token: state.tokens.bob },
    )
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}（他人可删除附件）`)
  },
})

defineCase({
  id: 'SEC-IDOR-07',
  family: 'SEC-IDOR',
  severity: 'S2',
  title: '办事侧模板读只返回已发布模板',
  expected: 'data=null（未发布模板不外泄）',
  async run() {
    const response = await call(
      'get',
      `/admin/process/getProcessTemplate/${state.fixtures.unpublishedTemplateId}`,
      { token: state.tokens.user },
    )
    const value = dataOf(response)
    return codeOf(response) === 200 && (value === null || value === undefined)
      ? pass('code=200, data=null')
      : fail(`code=${codeOf(response)}, data=${value === null ? 'null' : '存在'}`)
  },
})

defineCase({
  id: 'SEC-IDOR-08',
  family: 'SEC-IDOR',
  severity: 'S1',
  title: '抄送人不能处理审批',
  expected: 'body.code=403',
  async run() {
    const response = await call('post', '/admin/process/approve', {
      token: state.tokens.carol,
      body: {
        processId: state.fixtures.approvalA.id,
        taskId: state.fixtures.approvalA.taskId,
        status: 1,
      },
    })
    return codeOf(response) === 403
      ? pass('code=403')
      : fail(`code=${codeOf(response)}（抄送人可处理审批）`)
  },
})

defineCase({
  id: 'SEC-IDOR-09',
  family: 'SEC-IDOR',
  severity: 'S2',
  title: '他人未完成上传的附件不能代为完成',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call(
      'post',
      `/admin/attachments/${state.fixtures.uploadingAttachment.attachmentId}/complete`,
      { token: state.tokens.bob },
    )
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}（他人可完成上传）`)
  },
})

/** SEC-MEM：成员资格派生的能力矩阵。 */
defineCase({
  id: 'SEC-MEM-01',
  family: 'SEC-MEM',
  severity: 'S2',
  title: '申请人：可读、可补传、不可处理',
  expected: 'isApprove=false, canUpload=true, approve→403',
  async run() {
    const show = await call('get', `/admin/process/show/${state.fixtures.approvalA.id}`, {
      token: state.tokens.alice,
    })
    const attachments = await call(
      'get',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}`,
      { token: state.tokens.alice },
    )
    const approve = await call('post', '/admin/process/approve', {
      token: state.tokens.alice,
      body: {
        processId: state.fixtures.approvalA.id,
        taskId: state.fixtures.approvalA.taskId,
        status: 1,
      },
    })
    const evidence = {
      isApprove: dataOf(show)?.isApprove ?? null,
      canUpload: dataOf(attachments)?.canUpload ?? null,
      approveCode: codeOf(approve),
    }
    const ok =
      codeOf(show) === 200 &&
      evidence.isApprove === false &&
      evidence.canUpload === true &&
      evidence.approveCode === 403
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-MEM-02',
  family: 'SEC-MEM',
  severity: 'S2',
  title: '当前处理人：可读、可上传、可处理',
  expected: 'isApprove=true, canUpload=true',
  async run() {
    const show = await call('get', `/admin/process/show/${state.fixtures.approvalA.id}`, {
      token: state.tokens.bob,
    })
    const attachments = await call(
      'get',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}`,
      { token: state.tokens.bob },
    )
    const evidence = {
      isApprove: dataOf(show)?.isApprove ?? null,
      canUpload: dataOf(attachments)?.canUpload ?? null,
    }
    const ok = evidence.isApprove === true && evidence.canUpload === true
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-MEM-03',
  family: 'SEC-MEM',
  severity: 'S2',
  title: '抄送人：可读、只读（不可处理/不可上传）',
  expected: 'isApprove=false, canUpload=false',
  async run() {
    const show = await call('get', `/admin/process/show/${state.fixtures.approvalA.id}`, {
      token: state.tokens.carol,
    })
    const attachments = await call(
      'get',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}`,
      { token: state.tokens.carol },
    )
    const evidence = {
      isApprove: dataOf(show)?.isApprove ?? null,
      canUpload: dataOf(attachments)?.canUpload ?? null,
    }
    const ok =
      codeOf(show) === 200 && evidence.isApprove === false && evidence.canUpload === false
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-MEM-04',
  family: 'SEC-MEM',
  severity: 'S2',
  title: '历史处理人：审批后可读、不可再处理',
  expected: 'isApprove=false，再次 approve→403',
  async run() {
    const second = state.fixtures.approvalB
    const approve = await call('post', '/admin/process/approve', {
      token: state.tokens.bob,
      body: { processId: second.id, taskId: second.taskId, status: 1 },
    })
    const show = await call('get', `/admin/process/show/${second.id}`, {
      token: state.tokens.bob,
    })
    const attachments = await call('get', `/admin/attachments/processes/${second.id}`, {
      token: state.tokens.bob,
    })
    const replay = await call('post', '/admin/process/approve', {
      token: state.tokens.bob,
      body: { processId: second.id, taskId: second.taskId, status: 1 },
    })
    const evidence = {
      approveCode: codeOf(approve),
      isApprove: dataOf(show)?.isApprove ?? null,
      canUpload: dataOf(attachments)?.canUpload ?? null,
      replayCode: codeOf(replay),
    }
    const ok =
      evidence.approveCode === 200 &&
      codeOf(show) === 200 &&
      evidence.isApprove === false &&
      evidence.canUpload === false &&
      evidence.replayCode === 403
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-MEM-05',
  family: 'SEC-MEM',
  severity: 'S1',
  title: '非成员：三类能力全部拒绝',
  expected: 'show/attachments/approve 全部拒绝（403/205）',
  async run() {
    const show = await call('get', `/admin/process/show/${state.fixtures.approvalA.id}`, {
      token: state.tokens.user,
    })
    const attachments = await call(
      'get',
      `/admin/attachments/processes/${state.fixtures.approvalA.id}`,
      { token: state.tokens.user },
    )
    const upload = await call('post', '/admin/attachments/uploads', {
      token: state.tokens.user,
      body: {
        processId: state.fixtures.approvalA.id,
        fileName: 'sec-nonmember.txt',
        contentType: 'text/plain',
        sizeBytes: 1,
      },
    })
    const evidence = {
      showCode: codeOf(show),
      attachmentsCode: codeOf(attachments),
      uploadCode: codeOf(upload),
    }
    const denied = (code) => [205, 403].includes(code)
    const ok = denied(evidence.showCode) && denied(evidence.attachmentsCode) && denied(evidence.uploadCode)
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

/**
 * SEC-GATE-01 写端点良构体（#184）：键为端点路径，必填字段以服务端各请求 DTO/Vo 的
 * JSR303 注解为权威，id/外键用假值即可——@PreAuthorize 在 service 之前，gate 拒绝不依赖
 * 数据存在，无需造数。sysMenu/save 用 type=0 规避「类型为菜单时组件路径不能为空」。
 * 空体在这批端点会被校验先于 gate 拒成 201（模块地图第 5 条），不再作为 gate 用例载荷。
 */
const GATE_WRITE_BODIES = {
  '/admin/system/sysUser/save': { username: 'sec', password: 'sec', name: 'sec' },
  '/admin/system/sysUser/update': { id: 999999 },
  '/admin/system/sysRole/save': { roleName: 'sec', roleCode: 'sec' },
  '/admin/system/sysRole/update': { id: 999999 },
  '/admin/system/sysRole/doAssign': { userId: 999999 },
  '/admin/system/sysMenu/save': { parentId: 0, name: 'sec', type: 0, path: '/sec' },
  '/admin/system/sysMenu/update': { id: 999999 },
  '/admin/system/sysMenu/doAssign': { roleId: 999999 },
  '/admin/process/processTemplate/save': { name: 'sec', processTypeId: 999999 },
  '/admin/process/processTemplate/update': { id: 999999 },
  '/admin/process/processType/save': { name: 'sec' },
  '/admin/process/processType/update': { id: 999999 },
}

/** SEC-GATE：35 个配置端点对无配置权限账号一律 205。 */
const GATE_ENDPOINTS = [
  ['PUT', '/admin/system/sysUser/updateStatus/999999/0'],
  ['GET', '/admin/system/sysUser/1/5'],
  ['GET', '/admin/system/sysUser/get/1'],
  ['POST', '/admin/system/sysUser/save'],
  ['PUT', '/admin/system/sysUser/update'],
  ['DELETE', '/admin/system/sysUser/remove/999999'],
  ['GET', '/admin/system/sysRole/toAssign/1'],
  ['POST', '/admin/system/sysRole/doAssign'],
  ['GET', '/admin/system/sysRole/findAll'],
  ['GET', '/admin/system/sysRole/1/5'],
  ['POST', '/admin/system/sysRole/save'],
  ['GET', '/admin/system/sysRole/get/1'],
  ['PUT', '/admin/system/sysRole/update'],
  ['DELETE', '/admin/system/sysRole/remove/999999'],
  ['DELETE', '/admin/system/sysRole/batchRemove'],
  ['GET', '/admin/system/sysMenu/toAssign/1'],
  ['POST', '/admin/system/sysMenu/doAssign'],
  ['GET', '/admin/system/sysMenu/findNodes'],
  ['POST', '/admin/system/sysMenu/save'],
  ['PUT', '/admin/system/sysMenu/update'],
  ['DELETE', '/admin/system/sysMenu/remove/999999'],
  ['GET', '/admin/process/processTemplate/1/5'],
  ['GET', '/admin/process/processTemplate/get/1'],
  ['POST', '/admin/process/processTemplate/save'],
  ['PUT', '/admin/process/processTemplate/update'],
  ['DELETE', '/admin/process/processTemplate/remove/999999'],
  ['POST', '/admin/process/processTemplate/uploadProcessDefinition', 'multipart'],
  ['POST', '/admin/process/processTemplate/publish/999999'],
  ['GET', '/admin/process/processType/findAll'],
  ['GET', '/admin/process/processType/1/5'],
  ['GET', '/admin/process/processType/get/1'],
  ['POST', '/admin/process/processType/save'],
  ['PUT', '/admin/process/processType/update'],
  ['DELETE', '/admin/process/processType/remove/999999'],
  ['GET', '/admin/process/1/5'],
]

defineCase({
  id: 'SEC-GATE-01',
  family: 'SEC-GATE',
  severity: 'S1',
  title: '35 个配置端点对无配置权限账号返回 205',
  expected: '每个端点 body.code=205',
  async run() {
    const failures = []
    for (const [method, endpoint, kind] of GATE_ENDPOINTS) {
      const options = { token: state.tokens.user }
      if (kind === 'multipart') {
        const form = new FormData()
        form.append('file', new Blob([Buffer.from('PK\u0003\u0004sec')]), 'sec.zip')
        options.form = form
      } else if (endpoint.endsWith('/batchRemove')) {
        options.body = []
      } else if (['POST', 'PUT', 'DELETE'].includes(method)) {
        options.body = GATE_WRITE_BODIES[endpoint] ?? {}
      }
      const response = await call(method, endpoint, options)
      if (codeOf(response) !== 205) {
        // 校验失败（201）时消息直接点名缺哪个必填字段：良构体随 DTO 漂移后照消息修即可。
        const detail = messageOf(response)
        failures.push(`${method} ${endpoint} → code=${codeOf(response)}${detail ? `（${detail}）` : ''}`)
      }
    }
    return failures.length === 0
      ? pass(`35/35 端点 code=205`)
      : fail(`${failures.length}/35 未返回 205：${failures.slice(0, 5).join('; ')}`)
  },
})

defineCase({
  id: 'SEC-GATE-02',
  family: 'SEC-GATE',
  severity: 'S1',
  title: '伪造 gate 参数不能绕过配置权限',
  expected: 'body.code=205',
  async run() {
    const response = await call('get', '/admin/system/sysUser/1/5?menuPath=sysUser', {
      token: state.tokens.user,
      headers: { 'X-Menu-Path': 'sysUser', 'X-Configuration-Menu': 'sysUser' },
    })
    return codeOf(response) === 205
      ? pass('code=205')
      : fail(`code=${codeOf(response)}（伪造参数生效）`)
  },
})

defineCase({
  id: 'SEC-GATE-03',
  family: 'SEC-GATE',
  severity: 'S2',
  title: '有配置权限账号可读配置端点（gate 未过度拒绝）',
  expected: '6 个只读端点 code=200',
  async run() {
    const endpoints = [
      '/admin/system/sysUser/1/5',
      '/admin/system/sysRole/findAll',
      '/admin/system/sysMenu/findNodes',
      '/admin/process/processType/findAll',
      '/admin/process/processTemplate/1/5',
      '/admin/process/1/5',
    ]
    const failures = []
    for (const endpoint of endpoints) {
      const response = await call('get', endpoint, { token: state.tokens.admin })
      if (codeOf(response) !== 200) failures.push(`${endpoint} → code=${codeOf(response)}`)
    }
    return failures.length === 0
      ? pass(`6/6 code=200`)
      : fail(`${failures.length}/6 未返回 200：${failures.join('; ')}`)
  },
})

/** SEC-UPLOAD：上传校验与越权。 */
defineCase({
  id: 'SEC-UPLOAD-01',
  family: 'SEC-UPLOAD',
  severity: 'S3',
  title: '超过 50 MiB 的附件登记被拒',
  expected: 'body.code=201 且提示大小限制',
  async run() {
    const response = await call('post', '/admin/attachments/uploads', {
      token: state.tokens.alice,
      body: {
        draftId: crypto.randomUUID(),
        fileName: 'sec-oversize.bin',
        contentType: 'application/octet-stream',
        sizeBytes: 52_428_801,
      },
    })
    const ok = codeOf(response) === 201 && messageOf(response).includes('大小限制')
    return ok ? pass(`code=201, message=${messageOf(response)}`) : fail(`code=${codeOf(response)}`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-02',
  family: 'SEC-UPLOAD',
  severity: 'S3',
  title: '预签名 PUT 的 Content-Type 与登记不一致时不能完成登记',
  expected: 'PUT 被拒或 complete 返回 201',
  async run() {
    const body = Buffer.from('sec-ct-01')
    const draft = await uploadDraft(state.tokens.alice, {
      fileName: 'sec-ct.txt',
      contentType: 'text/plain',
      body,
      keepUploading: true,
    })
    const put = await fetch(draft.grantData.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'text/html' },
      body,
    })
    const complete = await call('post', `/admin/attachments/${draft.attachmentId}/complete`, {
      token: state.tokens.alice,
    })
    const evidence = { putStatus: put.status, completeCode: codeOf(complete) }
    const ok = put.status === 403 || codeOf(complete) === 201
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-UPLOAD-03',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '对象实际大小与登记不一致时不能完成登记',
  expected: 'body.code=201',
  async run() {
    const draft = await uploadDraft(state.tokens.alice, {
      fileName: 'sec-size.txt',
      contentType: 'text/plain',
      body: Buffer.from('12345'),
      keepUploading: true,
    })
    await fetch(draft.grantData.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'text/plain' },
      body: Buffer.from('12345678'),
    })
    const complete = await call('post', `/admin/attachments/${draft.attachmentId}/complete`, {
      token: state.tokens.alice,
    })
    return codeOf(complete) === 201
      ? pass(`code=201, message=${messageOf(complete)}`)
      : fail(`code=${codeOf(complete)}（大小不一致仍登记成功）`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-04',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '当前处理人可向审批补传附件',
  expected: 'code=200 且 canUpload=true',
  async run() {
    const response = await call('post', '/admin/attachments/uploads', {
      token: state.tokens.bob,
      body: {
        processId: state.fixtures.approvalA.id,
        fileName: 'sec-approver.txt',
        contentType: 'text/plain',
        sizeBytes: 1,
      },
    })
    return codeOf(response) === 200
      ? pass(`code=200, attachmentId=${dataOf(response)?.attachmentId}`)
      : fail(`code=${codeOf(response)}（处理人不能补传）`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-05',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '抄送人不能向审批补传附件',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call('post', '/admin/attachments/uploads', {
      token: state.tokens.carol,
      body: {
        processId: state.fixtures.approvalA.id,
        fileName: 'sec-cc.txt',
        contentType: 'text/plain',
        sizeBytes: 1,
      },
    })
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-06',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '非成员不能向审批上传附件',
  expected: 'body.code=205 或 403（拒绝）',
  async run() {
    const response = await call('post', '/admin/attachments/uploads', {
      token: state.tokens.user,
      body: {
        processId: state.fixtures.approvalA.id,
        fileName: 'sec-other.txt',
        contentType: 'text/plain',
        sizeBytes: 1,
      },
    })
    return [205, 403].includes(codeOf(response))
      ? pass(`code=${codeOf(response)}（拒绝）`)
      : fail(`code=${codeOf(response)}`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-07',
  family: 'SEC-UPLOAD',
  severity: 'S3',
  title: '流程模板上传拒绝非 zip 后缀',
  expected: 'body.code=201 且提示必须是 zip',
  async run() {
    const form = new FormData()
    form.append('file', new Blob([Buffer.from('PK')]), 'sec-evil.txt')
    const response = await call('post', '/admin/process/processTemplate/uploadProcessDefinition', {
      token: state.tokens.admin,
      form,
    })
    const ok = codeOf(response) === 201 && messageOf(response).includes('zip')
    return ok ? pass(`code=201, message=${messageOf(response)}`) : fail(`code=${codeOf(response)}`)
  },
})

defineCase({
  id: 'SEC-UPLOAD-08',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '流程 zip 文件名穿越路径被消解',
  expected: '返回路径落在 processes/sha256 下，processDefinitionKey 不含路径分隔',
  async run() {
    const form = new FormData()
    form.append('file', new Blob([Buffer.from('PK\u0003\u0004sec-traversal')]), '../../sec-evil.zip')
    const response = await call('post', '/admin/process/processTemplate/uploadProcessDefinition', {
      token: state.tokens.admin,
      form,
    })
    const data = dataOf(response) || {}
    const pathOk = String(data.processDefinitionPath || '').startsWith('processes/sha256/')
    const keyOk = !String(data.processDefinitionKey || '').includes('/')
    const evidence = {
      code: codeOf(response),
      processDefinitionPath: data.processDefinitionPath,
      processDefinitionKey: data.processDefinitionKey,
    }
    const ok = codeOf(response) === 200 && pathOk && keyOk
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-UPLOAD-09',
  family: 'SEC-UPLOAD',
  severity: 'S2',
  title: '未完成的上传凭证在过期后不能完成登记',
  expected: 'complete 返回 201（凭证已过期）',
  async run() {
    const fixture = state.fixtures.expiredUpload
    const waitMs = fixture.createdAt + (UPLOAD_TTL_SECONDS + 20) * 1000 - Date.now()
    if (waitMs > 0) await new Promise((resolve) => setTimeout(resolve, waitMs))
    let putStatus = null
    if (fixture.uploadUrl) {
      const put = await fetch(fixture.uploadUrl, {
        method: 'PUT',
        headers: fixture.headers || { 'Content-Type': 'text/plain' },
        body: Buffer.from('sec!'),
      })
      putStatus = put.status
    }
    const complete = await call('post', `/admin/attachments/${fixture.attachmentId}/complete`, {
      token: state.tokens.alice,
    })
    const evidence = { putStatus, completeCode: codeOf(complete), message: messageOf(complete) }
    const ok = codeOf(complete) === 201
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

/** SEC-CSRF：跨源与 GET 写语义。 */
defineCase({
  id: 'SEC-CSRF-01',
  family: 'SEC-CSRF',
  severity: 'S4',
  title: '普通接口不回显恶意 Origin 的 CORS 头',
  expected: '无 Access-Control-Allow-Origin',
  async run() {
    const response = await call('get', '/admin/system/index/info', {
      token: state.tokens.user,
      origin: 'http://evil.example',
    })
    const allow = response.headers['access-control-allow-origin'] ?? null
    return allow === null ? pass('无 ACAO 头') : fail(`ACAO=${allow}`)
  },
})

defineCase({
  id: 'SEC-CSRF-02',
  family: 'SEC-CSRF',
  severity: 'S4',
  title: '审批接口不回显恶意 Origin 的 CORS 头',
  expected: '无 Access-Control-Allow-Origin',
  async run() {
    const response = await call('get', '/admin/process/findPending/1/10', {
      token: state.tokens.bob,
      origin: 'http://evil.example',
    })
    const allow = response.headers['access-control-allow-origin'] ?? null
    const evidence = { allowOrigin: allow }
    return allow === null
      ? { ...pass('无 ACAO 头'), evidence }
      : { ...fail(`ACAO=${allow}`), evidence }
  },
})

defineCase({
  id: 'SEC-CSRF-03',
  family: 'SEC-CSRF',
  severity: 'S4',
  title: '跨源预检不授予带 token 头的写请求',
  expected: '预检不返回允许 token 头的 ACAO',
  async run() {
    const response = await call('options', '/admin/process/approve', {
      origin: 'http://evil.example',
      headers: {
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'token,content-type',
      },
    })
    const allowOrigin = response.headers['access-control-allow-origin'] ?? null
    const allowHeaders = response.headers['access-control-allow-headers'] ?? null
    const evidence = { status: response.status, allowOrigin, allowHeaders }
    const granted =
      allowOrigin !== null && String(allowHeaders || '').toLowerCase().includes('token')
    return granted ? { ...fail(JSON.stringify(evidence)), evidence } : { ...pass(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-CSRF-04',
  family: 'SEC-CSRF',
  severity: 'S3',
  title: 'GET updateStatus 不产生写副作用',
  expected: 'GET 不修改状态，写只由 POST/PUT 触发',
  async run() {
    const userId = state.fixtures.brute.id
    const before = await call('get', `/admin/system/sysUser/get/${userId}`, {
      token: state.tokens.admin,
    })
    const getAttempt = await call('get', `/admin/system/sysUser/updateStatus/${userId}/0`, {
      token: state.tokens.admin,
    })
    const after = await call('get', `/admin/system/sysUser/get/${userId}`, {
      token: state.tokens.admin,
    })
    const write = await call('put', `/admin/system/sysUser/updateStatus/${userId}/0`, {
      token: state.tokens.admin,
    })
    const written = await call('get', `/admin/system/sysUser/get/${userId}`, {
      token: state.tokens.admin,
    })
    await call('put', `/admin/system/sysUser/updateStatus/${userId}/1`, {
      token: state.tokens.admin,
    })
    const evidence = {
      beforeStatus: dataOf(before)?.status ?? null,
      getCode: codeOf(getAttempt),
      afterStatus: dataOf(after)?.status ?? null,
      putCode: codeOf(write),
      writtenStatus: dataOf(written)?.status ?? null,
    }
    const ok =
      evidence.getCode !== 200 &&
      evidence.afterStatus === evidence.beforeStatus &&
      evidence.putCode === 200 &&
      evidence.writtenStatus === 0
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-CSRF-05',
  family: 'SEC-CSRF',
  severity: 'S3',
  title: 'GET publish 不再是写操作（源码事实）',
  expected: 'publish 只注册 POST，updateStatus 只注册 PUT',
  async run() {
    const controller = fs.readFileSync(
      path.join(
        REPO_ROOT,
        'server/guigu-oa-parent/service-oa/src/main/java/com/atguigu/process/controller/OaProcessTemplateController.java',
      ),
      'utf8',
    )
    const sysUserController = fs.readFileSync(
      path.join(
        REPO_ROOT,
        'server/guigu-oa-parent/service-oa/src/main/java/com/atguigu/auth/controller/SysUserController.java',
      ),
      'utf8',
    )
    const mappingsOf = (source, pattern) =>
      [...source.matchAll(new RegExp(pattern, 'g'))].map((match) => match[1])
    const publishMappings = mappingsOf(controller, '@(\\w+)Mapping\\("/publish/\\{id\\}"\\)')
    const updateStatusMappings = mappingsOf(
      sysUserController,
      '@(\\w+)Mapping\\("updateStatus/\\{id\\}/\\{status\\}"\\)',
    )
    const expected = { publish: ['Post'], updateStatus: ['Put'] }
    const evidence = { publishMappings, updateStatusMappings }
    const ok =
      publishMappings.join(',') === expected.publish.join(',') &&
      updateStatusMappings.join(',') === expected.updateStatus.join(',')
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

/** SEC-BRUTE：认证失败处理。 */
defineCase({
  id: 'SEC-BRUTE-01',
  family: 'SEC-BRUTE',
  severity: 'S3',
  title: '不存在用户与密码错误返回同一错误码与文案',
  expected: '两者均 code=208 且 message 相同',
  async run() {
    const missing = await call('post', '/admin/system/index/login', {
      body: { username: `no-such-${state.runId}`, password: 'whatever1' },
    })
    const wrong = await call('post', '/admin/system/index/login', {
      body: { username: 'alice', password: 'wrong-password' },
    })
    const evidence = {
      missingCode: codeOf(missing),
      wrongCode: codeOf(wrong),
      sameMessage: messageOf(missing) === messageOf(wrong),
    }
    const ok = evidence.missingCode === 208 && evidence.wrongCode === 208 && evidence.sameMessage
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-BRUTE-02',
  family: 'SEC-BRUTE',
  severity: 'S3',
  title: '30 次失败后正确密码仍可登录（无限流/锁定）',
  expected: '应有锁定或限流；无则记缺陷',
  async run() {
    const username = state.fixtures.brute.username
    let failedLogins = 0
    for (let attempt = 0; attempt < 30; attempt += 1) {
      const response = await call('post', '/admin/system/index/login', {
        body: { username, password: `wrong-${attempt}` },
      })
      if (codeOf(response) === 208) failedLogins += 1
    }
    const correct = await call('post', '/admin/system/index/login', {
      body: { username, password: PASSWORD },
    })
    const evidence = { failedLogins, correctLoginCode: codeOf(correct) }
    const ok = codeOf(correct) !== 200
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

/** SEC-LEAK：敏感信息暴露。 */
defineCase({
  id: 'SEC-LEAK-01',
  family: 'SEC-LEAK',
  severity: 'S2',
  title: '用户分页不返回密码哈希',
  expected: 'records 不含 password',
  async run() {
    const response = await call('get', '/admin/system/sysUser/1/10', {
      token: state.tokens.admin,
    })
    const records = dataOf(response)?.records || []
    const leaked = records.filter((record) => typeof record.password === 'string' && record.password)
    const evidence = {
      total: records.length,
      leaked: leaked.length,
      samplePrefix: leaked[0]?.password ? `${leaked[0].password.slice(0, 4)}…` : null,
    }
    return leaked.length === 0
      ? { ...pass(`0/${records.length} 含 password`), evidence }
      : { ...fail(`${leaked.length}/${records.length} 条含 password`), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-02',
  family: 'SEC-LEAK',
  severity: 'S2',
  title: '用户详情不返回密码哈希',
  expected: 'data 不含 password',
  async run() {
    const response = await call('get', `/admin/system/sysUser/get/1`, {
      token: state.tokens.admin,
    })
    const password = dataOf(response)?.password
    const evidence = { hasPassword: Boolean(password), samplePrefix: password ? `${password.slice(0, 4)}…` : null }
    return password ? { ...fail('data.password 存在'), evidence } : { ...pass('无 password'), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-03',
  family: 'SEC-LEAK',
  severity: 'S4',
  title: 'OpenAPI 文档匿名可读',
  expected: '不应匿名暴露',
  async run() {
    const response = await call('get', '/v3/api-docs', { base: API_DIRECT })
    const evidence = {
      status: response.status,
      hasOpenapi: Boolean(response.json?.openapi),
      paths: Object.keys(response.json?.paths || {}).length,
    }
    return evidence.hasOpenapi ? { ...fail('匿名返回 openapi 文档'), evidence } : { ...pass('未暴露'), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-04',
  family: 'SEC-LEAK',
  severity: 'S4',
  title: 'Swagger UI 匿名可读',
  expected: '不应匿名暴露',
  async run() {
    const response = await call('get', '/swagger-ui/index.html', { base: API_DIRECT })
    const evidence = { status: response.status, length: response.text?.length ?? 0 }
    const exposed = response.status === 200 && String(response.text).includes('swagger')
    return exposed ? { ...fail('匿名可读'), evidence } : { ...pass('未暴露'), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-05',
  family: 'SEC-LEAK',
  severity: 'S4',
  title: '其他 actuator 端点匿名不可读',
  expected: 'body.code=208 或 HTTP 401/403',
  async run() {
    const response = await call('get', '/actuator/env', { base: API_DIRECT })
    const evidence = { status: response.status, code: codeOf(response) }
    const ok = codeOf(response) === 208 || [401, 403].includes(response.status)
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-06',
  family: 'SEC-LEAK',
  severity: 'S3',
  title: '抄送人选项接口不向普通用户暴露账号名',
  expected: '每条只含 id 与显示名',
  async run() {
    const response = await call('get', '/admin/process/carbonCopyRecipients', {
      token: state.tokens.user,
    })
    const recipients = dataOf(response)
    const rows = Array.isArray(recipients) ? recipients : []
    const evidence = {
      code: codeOf(response),
      count: rows.length,
      fields: rows[0] ? Object.keys(rows[0]) : [],
    }
    const unexpectedRow = rows.find((row) => {
      const fields = Object.keys(row)
      return fields.length !== 2 || !fields.includes('id') || !fields.includes('name')
    })
    const ok = codeOf(response) === 200 && rows.length > 0 && !unexpectedRow
    return ok
      ? { ...pass(`只含 id/name（${rows.length} 个）`), evidence }
      : { ...fail(`返回 ${rows.length} 个用户，字段 ${JSON.stringify(evidence.fields)}`), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-07',
  family: 'SEC-LEAK',
  severity: 'S4',
  title: '异常响应不返回堆栈或内部信息',
  expected: '仅通用错误文案',
  async run() {
    const response = await call('get', '/admin/system/sysUser/get/not-a-number', {
      token: state.tokens.admin,
    })
    const text = JSON.stringify(response.json || {})
    const leaks = /exception|at com\.|java\.|org\.springframework/i.test(text)
    return leaks
      ? fail('响应含内部异常信息')
      : pass(`code=${codeOf(response)}, message=${messageOf(response)}`)
  },
})

defineCase({
  id: 'SEC-LEAK-08',
  family: 'SEC-LEAK',
  severity: 'S2',
  title: '服务端日志不含密码或完整 token',
  expected: '日志中无 password/token 明文',
  async run() {
    let logs = ''
    try {
      logs = execFileSync('docker', ['compose', 'logs', '--no-color', 'service-oa'], {
        cwd: REPO_ROOT,
        encoding: 'utf8',
        maxBuffer: 64 * 1024 * 1024,
      })
    } catch (error) {
      return { actual: `无法读取容器日志：${error.message}`, pass: true, skipped: true }
    }
    const passwordHits = logs.match(/password\s*[=:]\s*["']?[^\s"']+/gi) || []
    const jwtHits = logs.match(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g) || []
    const evidence = { passwordHits: passwordHits.length, jwtHits: jwtHits.length }
    return passwordHits.length === 0 && jwtHits.length === 0
      ? { ...pass(JSON.stringify(evidence)), evidence }
      : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

defineCase({
  id: 'SEC-LEAK-09',
  family: 'SEC-LEAK',
  severity: 'S4',
  title: '客户端响应头暴露 nginx 版本',
  expected: '不应暴露精确版本',
  async run() {
    const response = await call('get', '/', { base: ORIGIN })
    const server = response.headers.server ?? null
    const evidence = { server }
    const versioned = server && /\d+\.\d+/.test(server)
    return versioned ? { ...fail(`Server=${server}`), evidence } : { ...pass(`Server=${server}`), evidence }
  },
})

/** SEC-HDR：统一客户端的安全响应头（#139）。 */
const CLIENT_PAGES = ['/', '/login', '/work/pending', '/nonexistent-sec-hdr']

function cspOf(response) {
  return response.headers['content-security-policy'] ?? ''
}

function directiveOf(csp, name) {
  return (
    csp
      .split(';')
      .map((part) => part.trim())
      .find((part) => part === name || part.startsWith(`${name} `)) ?? ''
  )
}

defineCase({
  id: 'SEC-HDR-01',
  family: 'SEC-HDR',
  severity: 'S3',
  title: '客户端 HTML 响应带强制 CSP',
  expected:
    "default-src 'self'、script-src 'self'、frame-ancestors 'none'；style-src 只放宽到 'self' 'unsafe-inline'",
  async run() {
    const evidence = {}
    const problems = []
    for (const page of CLIENT_PAGES) {
      const response = await call('get', page, { base: ORIGIN })
      const csp = cspOf(response)
      const defaultSrc = directiveOf(csp, 'default-src')
      const scriptSrc = directiveOf(csp, 'script-src')
      const styleSrc = directiveOf(csp, 'style-src')
      const frameAncestors = directiveOf(csp, 'frame-ancestors')
      evidence[page] = { status: response.status, defaultSrc, scriptSrc, styleSrc, frameAncestors }
      if (!defaultSrc.includes("'self'")) problems.push(`${page} 缺 default-src 'self'`)
      if (!scriptSrc.includes("'self'")) problems.push(`${page} 缺 script-src 'self'`)
      if (!frameAncestors.includes("'none'")) problems.push(`${page} 缺 frame-ancestors 'none'`)
      if (/unsafe-inline|unsafe-eval/.test(scriptSrc)) problems.push(`${page} 的 script-src 含不安全来源`)
      // Element Plus 的内联样式要求 style-src 带 'unsafe-inline'（ADR 0026）；这里只允许
      // 这一种固定形态，避免把 'unsafe-inline' 之外的通配源带进来。
      if (styleSrc !== "style-src 'self' 'unsafe-inline'") {
        problems.push(`${page} 的 style-src 不是 'self' 'unsafe-inline'：${styleSrc}`)
      }
    }
    return problems.length === 0
      ? { ...pass(`${CLIENT_PAGES.length}/${CLIENT_PAGES.length} 页带强制 CSP`), evidence }
      : { ...fail(problems.join('；')), evidence }
  },
})

defineCase({
  id: 'SEC-HDR-02',
  family: 'SEC-HDR',
  severity: 'S3',
  title: '客户端 HTML 响应带 X-Frame-Options: DENY',
  expected: '每个页面的 X-Frame-Options 都是 DENY',
  async run() {
    const evidence = {}
    const problems = []
    for (const page of CLIENT_PAGES) {
      const response = await call('get', page, { base: ORIGIN })
      const value = response.headers['x-frame-options'] ?? null
      evidence[page] = value
      if (String(value).toUpperCase() !== 'DENY') problems.push(`${page} X-Frame-Options=${value}`)
    }
    return problems.length === 0
      ? { ...pass('全部 DENY'), evidence }
      : { ...fail(problems.join('；')), evidence }
  },
})

defineCase({
  id: 'SEC-HDR-03',
  family: 'SEC-HDR',
  severity: 'S4',
  title: 'HTML 与静态资源带 X-Content-Type-Options: nosniff',
  expected: '文档与构建产物都返回 nosniff',
  async run() {
    const home = await call('get', '/', { base: ORIGIN })
    const asset = String(home.text).match(/\/static\/[^"']+\.js/)?.[0] ?? null
    if (!asset) {
      return { ...fail('首页没找到 /static/*.js 构建产物'), evidence: { asset } }
    }
    const pages = [
      ['/', home],
      [asset, await call('get', asset, { base: ORIGIN })],
    ]
    const values = Object.fromEntries(
      pages.map(([page, response]) => [page, response.headers['x-content-type-options'] ?? null]),
    )
    const problems = pages
      .filter(
        ([, response]) =>
          String(response.headers['x-content-type-options'] ?? '').toLowerCase() !== 'nosniff',
      )
      .map(([page]) => page)
    const evidence = { asset, values }
    return problems.length === 0
      ? { ...pass('文档与静态资源都是 nosniff'), evidence }
      : { ...fail(`${problems.join('、')} 缺 nosniff`), evidence }
  },
})

defineCase({
  id: 'SEC-HDR-04',
  family: 'SEC-HDR',
  severity: 'S3',
  title: 'CSP 的 connect-src 放行预签名上传源',
  expected: "connect-src 含 'self' 与 http(s) 源，且没有未展开的占位符",
  async run() {
    const response = await call('get', '/', { base: ORIGIN })
    const connectSrc = directiveOf(cspOf(response), 'connect-src')
    const evidence = { connectSrc }
    if (!connectSrc) return { ...fail('缺 connect-src 指令'), evidence }
    if (connectSrc.includes('$')) return { ...fail('占位符未展开'), evidence }
    if (!connectSrc.includes("'self'")) return { ...fail("connect-src 缺 'self'"), evidence }
    const origin = connectSrc.split(/\s+/).find((source) => /^https?:\/\//.test(source))
    return origin
      ? { ...pass(`connect-src 放行 ${origin}`), evidence }
      : { ...fail('connect-src 没有 http(s) 源，浏览器直连对象存储会被拦截'), evidence }
  },
})

defineCase({
  id: 'SEC-HDR-05',
  family: 'SEC-HDR',
  severity: 'S3',
  title: 'CSP 生效时浏览器无安全策略违规',
  expected: '登录、办事列表/详情、模板列表与表单设计器都无 securitypolicyviolation',
  async run() {
    let output = ''
    let ok = true
    try {
      output = execFileSync('npm', ['run', 'test:security', '--', '--grep', 'CSP'], {
        cwd: CLIENT_DIR,
        encoding: 'utf8',
        env: { ...process.env, DEMO_ORIGIN: ORIGIN, SEC_RUN_ID: state.runId },
        maxBuffer: 16 * 1024 * 1024,
      })
    } catch (error) {
      ok = false
      output = `${error.stdout || ''}\n${error.stderr || ''}`
    }
    const tail = output.split('\n').filter(Boolean).slice(-20).join('\n')
    return ok
      ? { ...pass('Playwright：无违规'), evidence: { outputTail: tail } }
      : { ...fail('Playwright：出现 securitypolicyviolation'), evidence: { outputTail: tail } }
  },
})

/** SEC-PAGE：分页上限。 */
defineCase({
  id: 'SEC-PAGE-01',
  family: 'SEC-PAGE',
  severity: 'S3',
  title: '用户分页拒绝超大 limit',
  expected: 'body.code=201（ADR 0024：超过上限直接拒绝，不静默截断）',
  async run() {
    const response = await call('get', '/admin/system/sysUser/1/100000', {
      token: state.tokens.admin,
    })
    const page = dataOf(response)
    const evidence = {
      code: codeOf(response),
      size: page?.size ?? null,
      records: page?.records?.length ?? null,
      total: page?.total ?? null,
      ms: response.ms,
    }
    if (evidence.code === 200) {
      return { ...fail(`limit=100000 被接受，返回 ${evidence.records} 条`), evidence }
    }
    return evidence.code === 201
      ? { ...pass(`code=201（拒绝）`), evidence }
      : { ...fail(`期望 code=201 拒绝，实际 code=${evidence.code}`), evidence }
  },
})

defineCase({
  id: 'SEC-PAGE-02',
  family: 'SEC-PAGE',
  severity: 'S3',
  title: '审批待处理分页拒绝超大 limit',
  expected: 'body.code=201（ADR 0024：超过上限直接拒绝，不静默截断）',
  async run() {
    const response = await call('get', '/admin/process/findPending/1/100000', {
      token: state.tokens.bob,
    })
    const page = dataOf(response)
    const evidence = {
      code: codeOf(response),
      size: page?.size ?? null,
      records: page?.records?.length ?? null,
      total: page?.total ?? null,
      ms: response.ms,
    }
    if (evidence.code === 200) {
      return { ...fail(`limit=100000 被接受，返回 ${evidence.records} 条`), evidence }
    }
    return evidence.code === 201
      ? { ...pass(`code=201（拒绝）`), evidence }
      : { ...fail(`期望 code=201 拒绝，实际 code=${evidence.code}`), evidence }
  },
})

defineCase({
  id: 'SEC-PAGE-03',
  family: 'SEC-PAGE',
  severity: 'S4',
  title: '负数/零分页参数被安全处理',
  expected: '不返回 5xx 或异常数据',
  async run() {
    const negative = await call('get', '/admin/system/sysUser/-1/5', {
      token: state.tokens.admin,
    })
    const zero = await call('get', '/admin/system/sysUser/1/0', {
      token: state.tokens.admin,
    })
    const evidence = {
      negative: { status: negative.status, code: codeOf(negative) },
      zero: { status: zero.status, code: codeOf(zero) },
    }
    const ok = [negative, zero].every(
      (response) => response.status < 500 && codeOf(response) !== null,
    )
    return ok ? { ...pass(JSON.stringify(evidence)), evidence } : { ...fail(JSON.stringify(evidence)), evidence }
  },
})

/** SEC-XSS：存储型 XSS 由 Playwright 套件执行。 */
defineCase({
  id: 'SEC-XSS-01',
  family: 'SEC-XSS',
  severity: 'S1',
  title: '审批表单字段存储型 XSS 不执行',
  expected: '脚本不执行（window.__secXss 为空）',
  async run() {
    const reportDir = path.join(CLIENT_DIR, 'tests', 'security', 'test-results')
    fs.rmSync(reportDir, { recursive: true, force: true })
    let output = ''
    let ok = true
    try {
      // SEC-HDR-05 跑同一目录下的 headers spec，按标题区分，避免两族互相计入结果。
      output = execFileSync('npm', ['run', 'test:security', '--', '--grep', 'XSS'], {
        cwd: CLIENT_DIR,
        encoding: 'utf8',
        env: { ...process.env, DEMO_ORIGIN: ORIGIN, SEC_RUN_ID: state.runId },
        maxBuffer: 16 * 1024 * 1024,
      })
    } catch (error) {
      ok = false
      output = `${error.stdout || ''}\n${error.stderr || ''}`
    }
    const tail = output.split('\n').filter(Boolean).slice(-20).join('\n')
    const evidence = { outputTail: tail, reportDir }
    return ok
      ? { ...pass('Playwright：未执行'), evidence }
      : { ...fail('Playwright：脚本执行（已留证）'), evidence }
  },
})

function selectCases(only) {
  if (!only) return cases
  const wanted = only
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
  return cases.filter(
    (def) => wanted.includes(def.family.toUpperCase()) || wanted.includes(def.id.toUpperCase()),
  )
}

function parseArgs(argv) {
  const options = { only: null, out: null, force: false }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--only') {
      options.only = argv[index + 1] || ''
      index += 1
    } else if (arg.startsWith('--only=')) {
      options.only = arg.slice('--only='.length)
    } else if (arg === '--out') {
      options.out = argv[index + 1] || null
      index += 1
    } else if (arg.startsWith('--out=')) {
      options.out = arg.slice('--out='.length)
    } else if (arg === '--force') {
      options.force = true
    }
  }
  return options
}

function environmentSummary() {
  let commit = 'unknown'
  try {
    commit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: REPO_ROOT, encoding: 'utf8' }).trim()
  } catch {
    // 保留 unknown
  }
  let compose = ''
  try {
    compose = execFileSync('docker', ['compose', 'ps', '--format', '{{.Service}} {{.Status}}'], {
      cwd: REPO_ROOT,
      encoding: 'utf8',
    }).trim()
  } catch {
    // 不影响证据主体
  }
  return {
    date: new Date().toISOString(),
    commit,
    origin: ORIGIN,
    apiDirect: API_DIRECT,
    node: process.version,
    runId: state.runId,
    compose,
    startedAt: state.startedAt.toISOString(),
  }
}

function writeEvidence(outDir) {
  fs.mkdirSync(outDir, { recursive: true })
  const results = { environment: environmentSummary(), results: state.results }
  fs.writeFileSync(
    path.join(outDir, 'security-results.json'),
    `${JSON.stringify(results, null, 2)}\n`,
  )

  const lines = []
  lines.push('# 安全定向用例结果（#113 决议）')
  lines.push('')
  lines.push(`- 执行时间：${results.environment.date}`)
  lines.push(`- 被测版本：\`${results.environment.commit}\``)
  lines.push(`- 入口：${ORIGIN}（API 经 \`/dev-api\`），匿名面 ${API_DIRECT}`)
  lines.push(`- runId：\`${results.environment.runId}\``)
  lines.push('')
  lines.push('| 用例 | 族 | 级别 | 结果 | 实际 |')
  lines.push('|---|---|---|---|---|')
  for (const item of state.results) {
    const mark = item.skipped ? '跳过' : item.pass ? '通过' : '**失败**'
    lines.push(
      `| ${item.id} | ${item.family} | ${item.severity ?? '-'} | ${mark} | ${String(item.actual).replace(/\|/g, '\\|')} |`,
    )
  }
  lines.push('')
  const failed = state.results.filter((item) => !item.pass && !item.skipped)
  lines.push(`合计 ${state.results.length} 条：通过 ${state.results.filter((item) => item.pass).length}、失败 ${failed.length}、跳过 ${state.results.filter((item) => item.skipped).length}。`)
  lines.push('')
  if (failed.length) {
    lines.push('## 失败项（候选缺陷）')
    lines.push('')
    for (const item of failed) {
      lines.push(`- **${item.id}**（${item.severity ?? '-'}）${item.title}：${item.actual}`)
      if (item.evidence) lines.push(`  - 证据：\`${JSON.stringify(item.evidence)}\``)
    }
    lines.push('')
  }
  fs.writeFileSync(path.join(outDir, 'security-summary.md'), `${lines.join('\n')}\n`)

  const envLines = [
    '# 安全测试环境摘要',
    '',
    '| 项 | 值 |',
    '|---|---|',
    `| 日期 | ${results.environment.date} |`,
    `| commit | \`${results.environment.commit}\` |`,
    `| 入口 | ${results.environment.origin} |`,
    `| 匿名面 | ${results.environment.apiDirect} |`,
    `| Node | ${results.environment.node} |`,
    `| runId | \`${results.environment.runId}\` |`,
    '',
    '```',
    results.environment.compose,
    '```',
    '',
  ]
  fs.writeFileSync(path.join(outDir, 'environment.md'), `${envLines.join('\n')}\n`)

  console.log(`\n证据已写入 ${path.relative(REPO_ROOT, outDir)}`)
  const failedCount = failed.length
  console.log(`结果：${state.results.length} 条用例，${failedCount} 条失败`)
  return failedCount
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  if (!options.out) {
    throw new Error('必须用 --out 指定证据目录')
  }
  const outDir = path.resolve(REPO_ROOT, options.out)
  const existing = path.join(outDir, 'security-results.json')
  if (!options.force && fs.existsSync(existing)) {
    throw new Error(`证据目录已有 security-results.json，拒绝覆盖（确需重写加 --force）：${outDir}`)
  }

  console.log(`安全扫描 runId=${state.runId} origin=${ORIGIN}`)
  let selected = []
  try {
    await prepare()
    selected = selectCases(options.only)
    console.log(`选中 ${selected.length} 条用例：${selected.map((item) => item.id).join(', ')}`)
    for (const def of selected) {
      await runCase(def)
    }
  } finally {
    await cleanupFixtures()
  }

  const failedCount = writeEvidence(outDir)
  process.exitCode = failedCount > 0 ? 1 : 0
}

main().catch((error) => {
  console.error(`安全扫描准备失败：${error.message}`)
  process.exitCode = 2
})
