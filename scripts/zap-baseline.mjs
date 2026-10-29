#!/usr/bin/env node

/**
 * ZAP 2.17.0 Core 被动 baseline（#113 决议 §2）。
 *
 * 只做被动：逐条 accessUrl / sendRequest，不启动 spider、不导入 OpenAPI、不做主动扫描。
 * 目标一：9530 统一客户端 SPA（响应头、静态资源、错误页、SPA 回退）。
 * 目标二：8800 匿名面（health / api-docs / swagger-ui / 登录错误响应 / 白名单穿越探测）。
 *
 *   node scripts/zap-baseline.mjs --out docs/test/evidence/2026-10-29-security-baseline
 *
 * `--out` 必填；目标目录已有 `zap-summary.md` 时拒绝覆盖，确需重写同一轮加 `--force`。
 *
 * 前置：ZAP Core 解压目录，默认 /tmp/zap/ZAP_2.17.0，可用 ZAP_HOME 覆盖。
 * 完整 HTML/JSON 报告体积大，不入仓；入仓的是 zap-summary.md 与 zap-alerts.json。
 */

import fs from 'node:fs'
import path from 'node:path'
import { spawn, execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const REPO_ROOT = path.resolve(fileURLToPath(new URL('..', import.meta.url)))
const CLIENT_ORIGIN = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')
const API_ORIGIN = (process.env.API_ORIGIN || 'http://localhost:8800').replace(/\/$/, '')
const ZAP_HOME = process.env.ZAP_HOME || '/tmp/zap/ZAP_2.17.0'
const ZAP_PORT = Number(process.env.ZAP_PORT || 8090)
const ZAP_BASE = `http://127.0.0.1:${ZAP_PORT}`
const RUN_ID = `zap${Date.now().toString(36)}`

function parseArgs(argv) {
  const options = { out: null, force: false }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--out') {
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function zapJson(pathname) {
  const response = await fetch(`${ZAP_BASE}${pathname}`, { signal: AbortSignal.timeout(20_000) })
  const text = await response.text()
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`ZAP 非 JSON 响应 ${pathname}: HTTP ${response.status}`)
  }
}

async function waitForZap(timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const version = await zapJson('/JSON/core/view/version/')
      if (version?.version) return version.version
    } catch {
      // 尚未就绪
    }
    await sleep(1000)
  }
  throw new Error('ZAP 在超时内未就绪')
}

function rawRequest(request) {
  return encodeURIComponent(request)
}

async function accessUrl(url) {
  const encoded = encodeURIComponent(url)
  return zapJson(`/JSON/core/action/accessUrl/?url=${encoded}&followRedirects=true`)
}

async function sendRequest(raw) {
  return zapJson(`/JSON/core/action/sendRequest/?request=${rawRequest(raw)}&followRedirects=true`)
}

async function passiveQueue() {
  const view = await zapJson('/JSON/pscan/view/recordsToScan/')
  return Number(view?.recordsToScan ?? 0)
}

async function waitForPassive(timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs
  let last = -1
  while (Date.now() < deadline) {
    const remaining = await passiveQueue()
    last = remaining
    if (remaining === 0) return
    await sleep(1000)
  }
  throw new Error(`被动扫描队列未清空（剩余 ${last}）`)
}

function buildTargets() {
  const client = [
    `${CLIENT_ORIGIN}/`,
    `${CLIENT_ORIGIN}/index.html`,
    `${CLIENT_ORIGIN}/login`,
    `${CLIENT_ORIGIN}/work/pending`,
    `${CLIENT_ORIGIN}/nonexistent-${RUN_ID}`,
    `${CLIENT_ORIGIN}/dev-api/actuator/health`,
    `${CLIENT_ORIGIN}/dev-api/nonexistent-${RUN_ID}`,
  ]
  const direct = [
    `${API_ORIGIN}/actuator/health`,
    `${API_ORIGIN}/actuator/health/readiness`,
    `${API_ORIGIN}/actuator/health/../env`,
    `${API_ORIGIN}/v3/api-docs`,
    `${API_ORIGIN}/swagger-ui/index.html`,
    `${API_ORIGIN}/swagger-ui.html`,
    `${API_ORIGIN}/admin/system/index/login`,
  ]
  return { client, direct }
}

function staticAssets() {
  const assets = new Set()
  try {
    const html = execFileSync('curl', ['-fsS', `${CLIENT_ORIGIN}/`], {
      encoding: 'utf8',
      timeout: 20_000,
    })
    // Vite 的 assetsDir 是 static（见 vite.config.js），同时兼容默认的 assets。
    for (const match of html.matchAll(/(?:src|href)="(\/(?:static|assets)\/[^"]+)"/g)) {
      assets.add(`${CLIENT_ORIGIN}${match[1]}`)
    }
  } catch {
    // 静态资源探测失败不阻塞；报告会记录缺口
  }
  return [...assets]
}

function writeSummary(outDir, { version, targets, alerts, directTargets, startedAt, finishedAt }) {
  fs.mkdirSync(outDir, { recursive: true })
  const risky = alerts.map((alert) => ({
    name: alert.name,
    risk: alert.risk,
    confidence: alert.confidence,
    url: alert.url,
    method: alert.method,
    cweid: alert.cweid,
    wasc: alert.wasc,
    count: alert.count ?? 1,
    evidence: alert.evidence ? String(alert.evidence).slice(0, 300) : '',
    description: alert.description ? String(alert.description).slice(0, 400) : '',
  }))
  fs.writeFileSync(
    path.join(outDir, 'zap-alerts.json'),
    `${JSON.stringify({ version, startedAt, finishedAt, targets, alerts: risky }, null, 2)}\n`,
  )

  const byRisk = risky.reduce((acc, alert) => {
    acc[alert.risk] = (acc[alert.risk] || 0) + 1
    return acc
  }, {})
  const lines = [
    '# ZAP 2.17.0 Core 被动 baseline',
    '',
    `- 开始：${startedAt}`,
    `- 结束：${finishedAt}`,
    `- 目标一（客户端 SPA）：${CLIENT_ORIGIN}`,
    `- 目标二（后端匿名面）：${API_ORIGIN}`,
    `- 模式：**被动 only**（逐条 accessUrl / sendRequest；未启用 spider、未导入 OpenAPI、未做主动扫描）`,
    '',
    '## 目标清单',
    '',
    ...targets.map((target) => `- ${target}`),
    '',
    '## 告警分布',
    '',
    `- High：${byRisk.High || 0}`,
    `- Medium：${byRisk.Medium || 0}`,
    `- Low：${byRisk.Low || 0}`,
    `- Informational：${byRisk.Informational || 0}`,
    '',
    '## 告警明细（同 URL 合并）',
    '',
  ]
  if (risky.length === 0) {
    lines.push('（无告警）')
  } else {
    lines.push('| 名称 | 风险 | 置信度 | 方法 | URL | CWE | 次数 |')
    lines.push('|---|---|---|---|---|---|---|')
    for (const alert of risky) {
      lines.push(
        `| ${alert.name} | ${alert.risk} | ${alert.confidence} | ${alert.method ?? ''} | ${alert.url} | ${alert.cweid ?? ''} | ${alert.count} |`,
      )
    }
    lines.push('')
    lines.push('## 重点证据')
    lines.push('')
    for (const alert of risky.filter((item) => ['High', 'Medium'].includes(item.risk))) {
      lines.push(`### ${alert.name}（${alert.risk}）`)
      lines.push('')
      lines.push(`- URL：${alert.url}`)
      lines.push(`- CWE：${alert.cweid ?? '-'} / WASC：${alert.wasc ?? '-'}`)
      lines.push(`- 证据：\`${alert.evidence || '（见完整报告）'}\``)
      lines.push(`- 描述：${alert.description || '（见完整报告）'}`)
      lines.push('')
    }
  }
  lines.push('## 说明')
  lines.push('')
  lines.push('- 被动扫描不会攻击目标，只分析已发出的请求/响应。')
  lines.push('- 完整 HTML/JSON 报告留在本机执行目录（见 execution 文档），不入仓。')
  lines.push(`- 匿名面探测里的穿越尝试：${directTargets.find((target) => target.includes('health/..')) ?? '-'}`)
  lines.push('')
  fs.writeFileSync(path.join(outDir, 'zap-summary.md'), `${lines.join('\n')}\n`)
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  if (!options.out) {
    throw new Error('必须用 --out 指定证据目录')
  }
  const outDir = path.resolve(REPO_ROOT, options.out)
  const existing = path.join(outDir, 'zap-summary.md')
  if (!options.force && fs.existsSync(existing)) {
    throw new Error(`证据目录已有 zap-summary.md，拒绝覆盖（确需重写加 --force）：${outDir}`)
  }
  const zapScript = path.join(ZAP_HOME, 'zap.sh')
  if (!fs.existsSync(zapScript)) {
    throw new Error(`找不到 ${zapScript}；先解压 ZAP Core 到 ZAP_HOME`)
  }

  const startedAt = new Date().toISOString()
  const logDir = path.join(ZAP_HOME, 'session')
  fs.mkdirSync(logDir, { recursive: true })
  const zap = spawn(
    zapScript,
    [
      '-daemon',
      '-host',
      '127.0.0.1',
      '-port',
      String(ZAP_PORT),
      '-dir',
      logDir,
      '-config',
      'api.disablekey=true',
      '-config',
      'api.addrs.addr.name=.*',
      '-config',
      'api.addrs.addr.regex=true',
      '-config',
      'scan.stats.enabled=false',
    ],
    { cwd: ZAP_HOME, stdio: 'ignore', detached: true },
  )

  try {
    const version = await waitForZap()
    const { client, direct } = buildTargets()
    const assets = staticAssets()
    const targets = [...client, ...assets, ...direct]
    console.log(`ZAP ${version} 就绪：${targets.length} 个目标（被动 only）`)

    for (const target of targets) {
      await accessUrl(target)
      console.log(`访问 ${target}`)
    }

    const loginBody = JSON.stringify({ username: `nobody-${RUN_ID}`, password: 'wrong-password' })
    const rawLogin =
      `POST /admin/system/index/login HTTP/1.1\r\n` +
      `Host: localhost:8800\r\n` +
      `Content-Type: application/json\r\n` +
      `Content-Length: ${Buffer.byteLength(loginBody)}\r\n` +
      `Connection: close\r\n\r\n` +
      loginBody
    await sendRequest(rawLogin)
    console.log('发送登录失败请求（应用账号 nobody）')

    await waitForPassive()
    const alertResponse = await zapJson('/JSON/alert/view/alerts/?start=0&count=5000')
    const alerts = alertResponse?.alerts || []
    const finishedAt = new Date().toISOString()

    const fullDir = path.join(outDir, 'zap-full')
    fs.mkdirSync(fullDir, { recursive: true })
    const html = await fetch(`${ZAP_BASE}/OTHER/core/other/htmlreport/`)
    fs.writeFileSync(path.join(fullDir, 'zap-report.html'), await html.text())
    const json = await fetch(`${ZAP_BASE}/OTHER/core/other/jsonreport/`)
    fs.writeFileSync(path.join(fullDir, 'zap-report.json'), await json.text())

    writeSummary(outDir, {
      version,
      targets,
      alerts,
      directTargets: direct,
      startedAt,
      finishedAt,
    })
    console.log(`ZAP 告警 ${alerts.length} 条，摘要写入 ${path.relative(REPO_ROOT, outDir)}`)
    console.log(`完整报告：${fullDir}/zap-report.html`)
  } finally {
    try {
      process.kill(-zap.pid, 'SIGTERM')
    } catch {
      try {
        zap.kill('SIGTERM')
      } catch {
        // 已退出
      }
    }
  }
}

main().catch((error) => {
  console.error(`ZAP baseline 失败：${error.message}`)
  process.exitCode = 2
})
